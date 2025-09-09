package com.valerie.meteoquote

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

import android.os.Build
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.core.view.WindowInsetsCompat
import android.graphics.drawable.TransitionDrawable
import android.animation.ValueAnimator
import android.animation.ArgbEvaluator
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager



data class City(val label: String, val lat: Double, val lon: Double)
data class HourlyForecast(val time: LocalDateTime, val temp: Double, val code: Int)
data class DailyForecast(val date: LocalDate, val tmin: Double, val tmax: Double, val code: Int)
data class Quote(val text: String, val author: String)

class MainActivity : AppCompatActivity() {

    // Villes par défaut
    private val defaultCities = listOf(
        City("Camburat", 44.6433, 1.9975),
        City("Montpellier", 43.6119, 3.8772),
        City("Orléans", 47.9025, 1.9090),
        City("Saints-en-Puisaye", 47.6231, 3.2606),
        City("Paris", 48.8566, 2.3522),
        City("Lyon", 45.7640, 4.8357),
        City("Marseille", 43.2965, 5.3698),
        City("Toulouse", 43.6047, 1.4442),
        City("Bordeaux", 44.8378, -0.5792)
    )
    // Liste mutable utilisée par l’app (sera chargée depuis prefs)
    private val cities = mutableListOf<City>()
    private lateinit var spinner: Spinner
    private lateinit var ivIcon: ImageView
    private lateinit var tvCondition: TextView
    private lateinit var containerHourly: LinearLayout
    private lateinit var containerDaily: LinearLayout
    private lateinit var tvTemp: TextView
    private lateinit var tvUpdated: TextView
    private lateinit var tvQuote: TextView
    private lateinit var btnRefresh: Button
    private lateinit var cityNamesAdapter: ArrayAdapter<String>
    private lateinit var rootContainer: ViewGroup
    private var currentThemeRes: Int? = null
    private var currentStatusColor: Int? = null
    private var lastWeatherCode: Int? = null

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootContainer = findViewById(R.id.rootContainer)

        spinner = findViewById(R.id.spinnerCities)
        ivIcon = findViewById(R.id.ivIcon)
        tvCondition = findViewById(R.id.tvCondition)

        tvTemp = findViewById(R.id.tvTemp)
        tvUpdated = findViewById(R.id.tvUpdated)
        tvQuote = findViewById(R.id.tvQuote)
        btnRefresh = findViewById(R.id.btnRefresh)
        containerHourly = findViewById(R.id.containerHourly)
        containerDaily = findViewById(R.id.containerDaily)

        // Sur API 35+, la status bar est transparente : on laisse le BACKGROUND passer sous la barre
        // et on décale le contenu via les insets pour éviter le chevauchement.
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, sys.top, v.paddingRight, sys.bottom) // ← ajoute le bas
            insets
        }

        // Charger les villes (prefs) ou utiliser la liste par défaut
        val saved = loadCities()
        cities.clear()
        cities.addAll(if (saved.isNotEmpty()) saved else defaultCities)

        // Adapter
        cityNamesAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            cities.map { it.label }.toMutableList()
        )
        spinner.adapter = cityNamesAdapter

        // Bouton "Actualiser"
        btnRefresh.setOnClickListener { refresh() }

        // Bouton "Nouvelle citation"
        val btnNewQuote: Button = findViewById(R.id.btnNewQuote)
        btnNewQuote.setOnClickListener {
            val code = lastWeatherCode
            if (code != null) {
                tvQuote.text = nextQuote(LocalDate.now(), code, advance = true)
            } else {
                Toast.makeText(this, "Patiente, météo en cours…", Toast.LENGTH_SHORT).show()
            }
        }

        // ➕ Bouton "Envoyer un retour" (null-safe si pas encore dans le layout)
        findViewById<Button?>(R.id.btnFeedback)?.setOnClickListener {
            // En phase de test on envoie sur +beta ; en prod tu peux passer à +support
            sendFeedbackEmail(isBeta = true)
        }

        // Recherche & ajout de ville
        val etCitySearch: EditText = findViewById(R.id.etCitySearch)
        val btnAddCity: Button = findViewById(R.id.btnAddCity)
        btnAddCity.setOnClickListener {
            val q = etCitySearch.text.toString().trim()
            if (q.isEmpty()) {
                Toast.makeText(this, "Saisis un nom de ville", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ioScope.launch {
                val found = geocodeCity(q)
                withContext(Dispatchers.Main) {
                    if (found == null) {
                        Toast.makeText(this@MainActivity, "Ville introuvable", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    // Évite les doublons (par libellé)
                    val already = cities.any { it.label.equals(found.label, ignoreCase = true) }
                    if (!already) {
                        cities.add(found)
                        cityNamesAdapter.add(found.label)
                        saveCities()   // ✅ persistance
                    }
                    // Sélectionne la ville et rafraîchit
                    val pos = cityNamesAdapter.getPosition(found.label).coerceAtLeast(0)
                    spinner.setSelection(pos)
                    refresh()
                }
            }
        }

        // Premier chargement
        refresh()
    }

    private fun refresh() {
        val index = spinner.selectedItemPosition.coerceIn(0, cities.lastIndex)
        val city = cities[index]

        // État d'attente
        tvCondition.text = "Chargement…"
        tvTemp.text = "— °C"
        tvUpdated.text = ""

        ioScope.launch {
            try {
                val (current, hourly, daily) = fetchFullWeather(city.lat, city.lon)
                val (temp, code) = current
                val label = wmoToLabel(code)

                withContext(Dispatchers.Main) {
                    ivIcon.setImageResource(wmoToIconRes(code))
                    tvCondition.text = label
                    tvTemp.text = String.format("%.1f °C", temp)
                    tvUpdated.text = "Maj: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    applyWeatherTheme(code)
                    val light = isBgLight(code)
                    applyContentColorsFor(light)
                    applyIconScrim(ivIcon, light, 8)

                    lastWeatherCode = code
                    tvQuote.text = nextQuote(LocalDate.now(), code, advance = false)

// Rendu des prévisions (on passe la couleur à utiliser)
                    renderHourly(hourly, light)
                    renderDaily(daily, light)

                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    ivIcon.setImageResource(wmoToIconRes(3)) // fallback "nuageux"
                    tvCondition.text = "Erreur : ${e.message ?: "réseau"}"
                    tvTemp.text = "— °C"
                    containerHourly.removeAllViews()
                    containerDaily.removeAllViews()
                }
            }
        }
    }

    // ---- Thème météo ----
    private fun themeDrawableRes(code: Int): Int = when (code) {
        0 -> R.drawable.bg_weather_clear
        1, 2, 3 -> R.drawable.bg_weather_clouds
        45, 48 -> R.drawable.bg_weather_fog
        51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82, 66, 67 -> R.drawable.bg_weather_rain
        71, 73, 75, 85, 86 -> R.drawable.bg_weather_snow
        95, 96, 99 -> R.drawable.bg_weather_thunder
        else -> R.drawable.bg_weather_clouds
    }
    private fun statusColorRes(code: Int): Int = when (code) {
        0 -> R.color.status_clear
        1, 2, 3 -> R.color.status_clouds
        45, 48 -> R.color.status_fog
        51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82, 66, 67 -> R.color.status_rain
        71, 73, 75, 85, 86 -> R.color.status_snow
        95, 96, 99 -> R.color.status_thunder
        else -> R.color.status_clouds
    }
    private fun applyWeatherTheme(code: Int, durationMs: Long = 350L) {
        val newBgRes = themeDrawableRes(code)
        val newStatus = ContextCompat.getColor(this, statusColorRes(code))

        val oldBgRes = currentThemeRes
        if (oldBgRes == null) {
            // 1er affichage : pas d’anim
            rootContainer.setBackgroundResource(newBgRes)
        } else if (oldBgRes != newBgRes) {
            val old = ContextCompat.getDrawable(this, oldBgRes)!!.mutate()
            val now = ContextCompat.getDrawable(this, newBgRes)!!.mutate()
            val td = TransitionDrawable(arrayOf(old, now)).apply { isCrossFadeEnabled = true }
            rootContainer.background = td
            td.startTransition(durationMs.toInt())
        }
        currentThemeRes = newBgRes

        // Barre de statut animée uniquement ≤ API 34 (Android 14 et moins)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val from = currentStatusColor ?: newStatus
            ValueAnimator.ofObject(ArgbEvaluator(), from, newStatus).apply {
                duration = durationMs
                addUpdateListener { anim ->
                    window.statusBarColor = anim.animatedValue as Int
                }
                start()
            }
            currentStatusColor = newStatus
        }
    }

    // ---- Récupération météo ----
    private fun fetchFullWeather(lat: Double, lon: Double): Triple<Pair<Double, Int>, List<HourlyForecast>, List<DailyForecast>> {
        val url =
            "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code" +
                    "&hourly=temperature_2m,weather_code" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto"

        val body = URL(url).openStream().bufferedReader().use { it.readText() }
        val root = JSONObject(body)

        // Current
        val cur = root.getJSONObject("current")
        val currentTemp = cur.getDouble("temperature_2m")
        val currentCode = cur.getInt("weather_code")

        // Hourly (24 prochaines heures >= maintenant)
        val hourly = root.getJSONObject("hourly")
        val hTimes = hourly.getJSONArray("time")
        val hTemps = hourly.getJSONArray("temperature_2m")
        val hCodes = hourly.getJSONArray("weather_code")
        val now = LocalDateTime.now()
        val hList = mutableListOf<HourlyForecast>()
        for (i in 0 until hTimes.length()) {
            val t = LocalDateTime.parse(hTimes.getString(i)) // ISO_LOCAL_DATE_TIME
            if (!t.isBefore(now)) {
                hList.add(HourlyForecast(t, hTemps.getDouble(i), hCodes.getInt(i)))
            }
            if (hList.size >= 24) break
        }

        // Daily (7 jours)
        val daily = root.getJSONObject("daily")
        val dTimes = daily.getJSONArray("time")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dCodes = daily.getJSONArray("weather_code")
        val dList = mutableListOf<DailyForecast>()
        val daysToTake = minOf(7, dTimes.length())
        for (i in 0 until daysToTake) {
            val d = LocalDate.parse(dTimes.getString(i))
            dList.add(DailyForecast(d, dMin.getDouble(i), dMax.getDouble(i), dCodes.getInt(i)))
        }

        return Triple(currentTemp to currentCode, hList, dList)
    }

    /** Géocodage Open-Meteo : nom de ville → (lat, lon) */
    private fun geocodeCity(name: String): City? {
        val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
        val url =
            "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=fr&format=json"
        val body = URL(url).openStream().bufferedReader().use { it.readText() }
        val results = JSONObject(body).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val first = results.getJSONObject(0)

        val nm = first.optString("name", name)
        val admin1 = first.optString("admin1", "")
        val country = first.optString("country_code", "")
        val label = when {
            admin1.isNotEmpty() && country.isNotEmpty() -> "$nm, $admin1 ($country)"
            country.isNotEmpty() -> "$nm ($country)"
            else -> nm
        }

        val lat = first.getDouble("latitude")
        val lon = first.getDouble("longitude")
        return City(label, lat, lon)
    }

    // ---- Libellés & icônes ----
    private fun wmoToLabel(code: Int): String = when (code) {
        0 -> "Ciel clair"
        1, 2, 3 -> "Plutôt nuageux"
        45, 48 -> "Brouillard"
        51, 53, 55 -> "Bruine"
        56, 57 -> "Bruine verglaçante"
        61, 63, 65, 80, 81, 82 -> "Pluie"
        66, 67 -> "Pluie verglaçante"
        71, 73, 75, 85, 86 -> "Neige"
        95, 96, 99 -> "Orage"
        else -> "Conditions variées"
    }
    private fun wmoToIconRes(code: Int): Int = when (code) {
        0 -> R.drawable.ic_weather_sunny_color
        1, 2, 3 -> R.drawable.ic_weather_cloudy_color
        45, 48 -> R.drawable.ic_weather_fog_color
        51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82, 66, 67 -> R.drawable.ic_weather_rain_color
        71, 73, 75, 85, 86 -> R.drawable.ic_weather_snow_color
        95, 96, 99 -> R.drawable.ic_weather_thunder_color
        else -> R.drawable.ic_weather_cloudy_color
    }

    // ---- Citations ----
    private fun prefs() = getSharedPreferences("meteoquote_prefs", Context.MODE_PRIVATE)
    private fun bucketName(code: Int): String = when (code) {
        0 -> "clear"
        1, 2, 3 -> "clouds"
        45, 48 -> "fog"
        51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82, 66, 67 -> "rain"
        71, 73, 75, 85, 86 -> "snow"
        95, 96, 99 -> "thunder"
        else -> "clouds"
    }
    private fun quotesFor(code: Int): List<Quote> {
        val clear = listOf(
            Quote("Le soleil brille pour tout le monde.", "Proverbe"),
            Quote("La simplicité est la sophistication suprême.", "L. de Vinci"),
            Quote("Crée la lumière que tu cherches.", "Anonyme")
        )
        val clouds = listOf(
            Quote("Au-dessus des nuages, le ciel est toujours bleu.", "Proverbe"),
            Quote("Patience : les nuages se dissipent toujours.", "Anonyme"),
            Quote("Notre clarté naît parfois de l’ombre.", "Anonyme")
        )
        val rain = listOf(
            Quote("Sans la pluie, rien ne pousse.", "Anonyme"),
            Quote("Il faut de la pluie pour voir l’arc-en-ciel.", "D. Parton"),
            Quote("Chaque goutte prépare une moisson.", "Proverbe")
        )
        val snow = listOf(
            Quote("La paix tombe parfois comme la neige.", "Anonyme"),
            Quote("Chaque flocon a sa forme et son destin.", "Proverbe"),
            Quote("Le silence de la neige dit l’essentiel.", "Anonyme")
        )
        val fog = listOf(
            Quote("Quand la route est brumeuse, avance pas à pas.", "Anonyme"),
            Quote("La clarté se révèle en chemin.", "Anonyme"),
            Quote("Tout brouillard finit par se lever.", "Proverbe")
        )
        val thunder = listOf(
            Quote("Le courage n’est pas l’absence de peur.", "N. Mandela"),
            Quote("L’éclair éclaire l’instant : saisis-le.", "Anonyme"),
            Quote("La tempête forge les marins.", "Proverbe")
        )
        return when (code) {
            0 -> clear
            1, 2, 3 -> clouds
            45, 48 -> fog
            51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82, 66, 67 -> rain
            71, 73, 75, 85, 86 -> snow
            95, 96, 99 -> thunder
            else -> clouds
        }
    }
    /** Renvoie une citation formatée. Si advance=true, passe à la suivante et persiste l’index. */
    private fun nextQuote(date: LocalDate, code: Int, advance: Boolean): String {
        val bucket = quotesFor(code)
        val key = "quote_idx_${bucketName(code)}"
        val p = prefs()
        var idx = p.getInt(key, (date.dayOfYear - 1) % bucket.size)
        if (advance) idx = (idx + 1) % bucket.size
        p.edit().putInt(key, idx).apply()
        val q = bucket[idx]
        return "“${q.text}” — ${q.author}"
    }

    // ---- Bouton "Envoyer un retour" ----
    private fun sendFeedbackEmail(isBeta: Boolean) {
        val address = if (isBeta) "ivray3dlabs+beta@gmail.com" else "ivray3dlabs+support@gmail.com"
        val (appId, vName, vCode) = appInfo() // ta fonction ou BuildConfig si activé
        val subject = "[Meteo & Citation] Retour testeur V$vName"
        val body = buildString {
            appendLine("Merci pour votre retour 🙌")
            appendLine()
            appendLine("Décrivez le problème/amélioration :")
            appendLine("- Étapes pour reproduire :")
            appendLine("- Comportement attendu :")
            appendLine("- Capture/vidéo (si possible) :")
            appendLine()
            appendLine("---")
            appendLine("Infos techniques (auto) :")
            appendLine("App : $appId $vName ($vCode)")
            appendLine("Android : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Appareil : ${Build.MANUFACTURER} ${Build.MODEL}")
        }

        // 1) Essai strict: apps e-mail uniquement
        val sendTo = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")                 // ← important
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(Intent.createChooser(sendTo, "Envoyer par e-mail"))
            return
        } catch (_: ActivityNotFoundException) { /* on tente le fallback */ }

        // 2) Fallback: message/rfc822 (inclut Gmail, Outlook, etc.)
        val sendAny = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(Intent.createChooser(sendAny, "Envoyer par e-mail"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Aucun client e-mail trouvé", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Persistance villes ----
    private fun saveCities() {
        val arr = JSONArray()
        cities.forEach { c ->
            val o = JSONObject()
            o.put("label", c.label)
            o.put("lat", c.lat)
            o.put("lon", c.lon)
            arr.put(o)
        }
        getSharedPreferences("meteoquote_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("cities_json", arr.toString())
            .apply()
    }
    private fun loadCities(): List<City> {
        val json = getSharedPreferences("meteoquote_prefs", Context.MODE_PRIVATE)
            .getString("cities_json", null) ?: return emptyList()
        val arr = JSONArray(json)
        val list = mutableListOf<City>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(City(o.getString("label"), o.getDouble("lat"), o.getDouble("lon")))
        }
        return list
    }

    // ---- Rendu UI ----
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun renderHourly(items: List<HourlyForecast>, useDarkText: Boolean) {
        containerHourly.removeAllViews()
        val fmtHour = DateTimeFormatter.ofPattern("HH'h'", Locale.getDefault())
        val textColor = if (useDarkText) 0xFF111111.toInt() else Color.WHITE

        items.forEach { h ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(12) }
            }

            val iv = ImageView(this).apply {
                setImageResource(wmoToIconRes(h.code))
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                contentDescription = wmoToLabel(h.code)
            }
            applyIconScrim(iv, useDarkText, 6)

            val tvT = TextView(this).apply {
                text = String.format(Locale.getDefault(), "%.0f°", h.temp)
                textSize = 16f
                setTextColor(textColor)
            }
            val tvH = TextView(this).apply {
                text = h.time.format(fmtHour)
                textSize = 12f
                setTextColor(textColor)
            }

            col.addView(iv); col.addView(tvT); col.addView(tvH)
            containerHourly.addView(col)
        }
    }

    private fun renderDaily(items: List<DailyForecast>, useDarkText: Boolean) {
        containerDaily.removeAllViews()
        val fmtDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        val textColor = if (useDarkText) 0xFF111111.toInt() else Color.WHITE

        items.forEach { d ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
                setPadding(0, dp(6), 0, dp(6))
            }

            val tvDate = TextView(this).apply {
                text = d.date.format(fmtDay)
                textSize = 14f
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val iv = ImageView(this).apply {
                setImageResource(wmoToIconRes(d.code))
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(12) }
                contentDescription = wmoToLabel(d.code)
            }
            applyIconScrim(iv, useDarkText, 6)

            val tvTemps = TextView(this).apply {
                text = String.format(Locale.getDefault(), "min %.0f° / max %.0f°", d.tmin, d.tmax)
                textSize = 14f
                setTextColor(textColor)
            }

            row.addView(tvDate); row.addView(iv); row.addView(tvTemps)
            containerDaily.addView(row)
        }
    }

    private fun appInfo(): Triple<String, String, Long> {
        val pm = packageManager
        val pn = packageName
        val pi = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pn, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pn, 0)
        }
        val vName = pi.versionName ?: "?"
        val vCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode
        else @Suppress("DEPRECATION") pi.versionCode.toLong()
        return Triple(pn, vName, vCode)
    }

    // Fond "clair" ? -> texte foncé ; sinon texte clair
    private fun isBgLight(code: Int): Boolean = when (code) {
        0, 1, 2, 3, 45, 48, 71, 73, 75, 85, 86 -> true   // clair, nuages, brouillard, neige
        else -> false                                    // pluie/orage plutôt sombres
    }

    private fun applyContentColorsFor(useDarkText: Boolean) {
        val primary = if (useDarkText) 0xFF111111.toInt() else Color.WHITE
        tvCondition.setTextColor(primary)
        tvTemp.setTextColor(primary)
        tvUpdated.setTextColor(primary)
        tvQuote.setTextColor(primary)
    }

    private fun pad(v: Int) = (v * resources.displayMetrics.density).toInt()

    // Pastille circulaire derrière les icônes pour garantir le contraste
    private fun applyIconScrim(iv: ImageView, useDarkText: Boolean, paddingDp: Int) {
        val color = if (useDarkText) 0x33000000 else 0xB3FFFFFF.toInt() // noir 20% / blanc 70%
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        iv.background = bg
        iv.setPadding(pad(paddingDp), pad(paddingDp), pad(paddingDp), pad(paddingDp))
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
    }
}
