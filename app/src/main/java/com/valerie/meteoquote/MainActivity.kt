package com.valerie.meteoquote
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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
import android.view.View
import android.content.res.ColorStateList
import android.util.Log
import androidx.core.view.updateLayoutParams
import java.net.HttpURLConnection

data class City(val label: String, val lat: Double, val lon: Double)
data class HourlyForecast(val time: LocalDateTime, val temp: Double, val code: Int)
data class DailyForecast(val date: LocalDate, val tmin: Double, val tmax: Double, val code: Int)
data class Quote(val text: String, val author: String)
data class WeatherResult(
    val current: Pair<Double, Int>,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val uvNow: Double,
    val uvMaxToday: Double,
    val uvPeakTime: LocalDateTime?,
    val aqiNow: Int,
    val aqiMaxToday: Int
)

class MainActivity : AppCompatActivity() {

    // Villes par défaut
    private val defaultCities = listOf(
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
    private lateinit var cityNamesAdapter: ArrayAdapter<String>
    private lateinit var rootContainer: ViewGroup
    private lateinit var tvAqi: TextView
    private var tvUv: TextView? = null
    private var currentThemeRes: Int? = null
    private var currentStatusColor: Int? = null
    private var lastWeatherCode: Int? = null

    private val coarsePermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) locateAndSelectCity() else
            Toast.makeText(this, "Autorise la localisation pour utiliser \"Ma position\"", Toast.LENGTH_SHORT).show()
    }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var suppressNextRefresh = true

    private val locationPermsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = (perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true) ||
                (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true)
        if (granted) locateAndSelectCity()
        else Toast.makeText(this, "Autorise la localisation pour utiliser « Ma position ».", Toast.LENGTH_SHORT).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvUv = findViewById(R.id.tvUv)
        tvUv?.updateLayoutParams<LinearLayout.LayoutParams> {
            topMargin = dp(16)
        }

        rootContainer = findViewById(R.id.rootContainer)
        spinner = findViewById(R.id.spinnerCities)
        ivIcon = findViewById(R.id.ivIcon)
        tvCondition = findViewById(R.id.tvCondition)
        tvTemp = findViewById(R.id.tvTemp)
        tvQuote = findViewById(R.id.tvQuote)
        tvAqi = findViewById(R.id.tvAqi)
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

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (suppressNextRefresh) { suppressNextRefresh = false; return }
                refresh() // ⬅️ recharge météo/UV/AQI pour la ville choisie
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

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

        findViewById<Button?>(R.id.btnFeedback)?.setOnClickListener {
            openFeedbackForm()
        }

        findViewById<Button?>(R.id.btnLocate)?.setOnClickListener {
            // ici tu gardes ton check/ask de permissions comme tu l’as déjà
            locateAndSelectCity() // ⬅️ appellera la suite et mettra à jour le spinner + météo
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
        tvUv = findViewById(R.id.tvUv)

        // Premier chargement
        refresh()
    }

    private fun refresh() {
        val index = spinner.selectedItemPosition.coerceIn(0, cities.lastIndex)
        val city = cities[index]

        // État d'attente
        tvCondition.text = "Chargement…"
        tvTemp.text = "— °C"
        tvUpdated.visibility = View.GONE   // garde caché en permanence

        ioScope.launch {
            try {
                val result = fetchFullWeather(city.lat, city.lon)
                val (temp, code) = result.current
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

                    // UV badge
                    val (uvLabel, uvColor) = uvCategory(result.uvMaxToday)
                    val peakTxt = result.uvPeakTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "—"
                    tvUv?.apply {
                        setBackgroundResource(R.drawable.bg_uv_chip)
                        text = String.format("UV %.1f • pic %.1f à %s — %s",
                            result.uvNow, result.uvMaxToday, peakTxt, uvLabel)
                        backgroundTintList = ColorStateList.valueOf(uvColor)
                        setTextColor(Color.WHITE)
                        visibility = View.VISIBLE
                    }
// AQI
                    val (aqiLabel, aqiColor) = aqiCategoryEU(result.aqiMaxToday)
                    tvAqi?.let { aqi ->
                        aqi.setBackgroundResource(R.drawable.bg_uv_chip)
                        aqi.text = "AQI ${result.aqiNow} — $aqiLabel"
                        ViewCompat.setBackgroundTintList(aqi, ColorStateList.valueOf(aqiColor))
                        aqi.setTextColor(Color.WHITE)
                        aqi.visibility = View.VISIBLE
                    }
                    tvUv?.backgroundTintList = ColorStateList.valueOf(uvColor)
                    // Rendus
                    renderHourly(result.hourly, light)
                    renderDaily(result.daily, light)
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
    private fun fetchFullWeather(lat: Double, lon: Double): WeatherResult {
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&hourly=temperature_2m,weather_code,uv_index" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,uv_index_max" +
                "&timezone=auto"

        val body = URL(url).openStream().bufferedReader().use { it.readText() }
        val root = JSONObject(body)

        // Current
        val cur = root.getJSONObject("current")
        val currentTemp = cur.getDouble("temperature_2m")
        val currentCode = cur.getInt("weather_code")

        // Hourly
        val hourly = root.getJSONObject("hourly")
        val hTimes = hourly.getJSONArray("time")
        val hTemps = hourly.getJSONArray("temperature_2m")
        val hCodes = hourly.getJSONArray("weather_code")
        val hUv    = hourly.getJSONArray("uv_index")
        val now = LocalDateTime.now()

        val hList = mutableListOf<HourlyForecast>()
        var uvNow = 0.0
        var grabbedUv = false
        for (i in 0 until hTimes.length()) {
            val t = LocalDateTime.parse(hTimes.getString(i))
            if (!grabbedUv && !t.isBefore(now)) {
                uvNow = hUv.optDouble(i, 0.0)
                grabbedUv = true
            }
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
        val dUvMax = daily.getJSONArray("uv_index_max")
        val dList = mutableListOf<DailyForecast>()
        val daysToTake = minOf(7, dTimes.length())
        for (i in 0 until daysToTake) {
            val d = LocalDate.parse(dTimes.getString(i))
            dList.add(DailyForecast(d, dMin.getDouble(i), dMax.getDouble(i), dCodes.getInt(i)))
        }
        val today = LocalDate.now()
        var peakVal = -1.0
        var peakTime: LocalDateTime? = null
        for (i in 0 until hTimes.length()) {
            val t = LocalDateTime.parse(hTimes.getString(i))
            if (t.toLocalDate() == today) {
                val v = hUv.optDouble(i, 0.0)
                if (v > peakVal) { peakVal = v; peakTime = t }
            }
        }
        val uvMaxToday = if (peakVal >= 0) peakVal else if (dUvMax.length() > 0) dUvMax.getDouble(0) else 0.0
        // AQI (européen)
        val urlAq = "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=$lat&longitude=$lon" +
                "&hourly=european_aqi" +
                "&timezone=auto"
        val aq = URL(urlAq).openStream().bufferedReader().use { it.readText() }
        val aqr = JSONObject(aq).getJSONObject("hourly")
        val aTimes = aqr.getJSONArray("time")
        val aVals  = aqr.getJSONArray("european_aqi")

        var aqiNow = 0
        var aqiMaxToday = 0
        var tookNow = false
        for (i in 0 until aTimes.length()) {
            val t = LocalDateTime.parse(aTimes.getString(i))
            val v = aVals.optInt(i, 0)
            if (!t.isBefore(now) && !tookNow) { aqiNow = v; tookNow = true }
            if (t.toLocalDate() == today && v > aqiMaxToday) aqiMaxToday = v
        }
        if (!tookNow && aVals.length() > 0) aqiNow = aVals.optInt(0, 0)

        return WeatherResult(currentTemp to currentCode, hList, dList, uvNow, uvMaxToday, peakTime, aqiNow, aqiMaxToday)
    }

    private fun aqiCategoryEU(v: Int): Pair<String, Int> {
        val res = when {
            v <= 20  -> R.color.aqi_good      // Très bon/Bon
            v <= 40  -> R.color.aqi_fair      // Moyen
            v <= 60  -> R.color.aqi_mod       // Médiocre
            v <= 80  -> R.color.aqi_poor      // Mauvais
            v <= 100 -> R.color.aqi_vpoor     // Très mauvais
            else     -> R.color.aqi_epoor     // Extrêmement mauvais
        }
        val label = when {
            v <= 20  -> "Bon"
            v <= 40  -> "Moyen"
            v <= 60  -> "Médiocre"
            v <= 80  -> "Mauvais"
            v <= 100 -> "Très mauvais"
            else     -> "Extrêmement mauvais"
        }
        return label to ContextCompat.getColor(this, res)
    }

    private fun uvCategory(v: Double): Pair<String, Int> {
        val resId = when {
            v < 3  -> R.color.uv_low
            v < 6  -> R.color.uv_mod
            v < 8  -> R.color.uv_high
            v < 11 -> R.color.uv_veryhigh
            else   -> R.color.uv_extreme
        }
        val label = when {
            v < 3  -> "Faible"
            v < 6  -> "Modéré"
            v < 8  -> "Élevé"
            v < 11 -> "Très élevé"
            else   -> "Extrême"
        }
        return label to ContextCompat.getColor(this, resId)
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

    /** Reverse geocoding Open-Meteo : (lat, lon) -> City(label) */
    private fun reverseGeocode(lat: Double, lon: Double): City? {
        val url = URL(
            "https://geocoding-api.open-meteo.com/v1/reverse" +
                    "?latitude=$lat&longitude=$lon&language=fr"
        )

        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }

            if (code !in 200..299) {
                Log.w("MeteoQuote", "reverseGeocode HTTP $code: $body")
                null
            } else {
                val results = JSONObject(body).optJSONArray("results") ?: return null
                if (results.length() == 0) return null
                val first = results.getJSONObject(0)

                val nm = first.optString("name", "Ma position")
                val admin1 = first.optString("admin1", "")
                val country = first.optString("country_code", "")
                val label = when {
                    admin1.isNotEmpty() && country.isNotEmpty() -> "$nm, $admin1 ($country)"
                    country.isNotEmpty() -> "$nm ($country)"
                    else -> nm
                }
                val la = first.optDouble("latitude", lat)
                val lo = first.optDouble("longitude", lon)
                City(label, la, lo)
            }
        } catch (e: Exception) {
            Log.e("MeteoQuote", "reverseGeocode error", e)
            null
        } finally {
            conn?.disconnect()
        }
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
        val (appId, vName, vCode) = appInfo()
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

        val uri = Uri.parse("mailto:$address")
        val email = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(Intent.createChooser(email, "Envoyer par e-mail"))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gm")))
            } catch (_: Exception) {
                Toast.makeText(this, "Installe une application e-mail (Gmail, Outlook…)", Toast.LENGTH_LONG).show()
            }
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

    // Fond "clair" ? -> texte foncé ; sinon texte clair
    private fun isBgLight(code: Int): Boolean = when (code) {
        0, 1, 2, 3, 45, 48, 71, 73, 75, 85, 86 -> true   // clair, nuages, brouillard, neige
        else -> false                                    // pluie/orage plutôt sombres
    }

    // Remplace ENTIEREMENT applyContentColorsFor(...)
    private fun applyContentColorsFor(useDarkText: Boolean) {
        // Texte principal: noir (lisible sur tes fonds clairs), secondaire: gris
        val primary   = 0xFF111111.toInt()
        val secondary = 0xFF5E5E5E.toInt()

        tvCondition.setTextColor(primary)
        tvTemp.setTextColor(primary)
        tvQuote.setTextColor(primary)
        tvUpdated.setTextColor(secondary)

        findViewById<TextView?>(R.id.tvAppTitle)?.setTextColor(primary)
        findViewById<TextView?>(R.id.tvHourlyTitle)?.setTextColor(secondary)
        findViewById<TextView?>(R.id.tvDailyTitle)?.setTextColor(secondary)
        findViewById<TextView?>(R.id.tvQuoteTitle)?.setTextColor(secondary)

        findViewById<EditText?>(R.id.etCitySearch)?.apply {
            setTextColor(primary)
            setHintTextColor(secondary)
        }

        // Met la couleur du texte de l'élément SÉLECTIONNÉ du spinner
        (spinner.selectedView as? TextView)?.setTextColor(primary)
    }

    // AJOUTE ce helper
    private fun buildCityAdapter(useDarkText: Boolean): ArrayAdapter<String> {
        val textColor = if (useDarkText) 0xFF111111.toInt() else Color.WHITE
        val labels = cities.map { it.label }.toMutableList()
        return object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_dropdown_item, labels
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(textColor)
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.setTextColor(textColor)
                return v
            }
        }
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
    private fun appInfo(): Triple<String, String, Int> {
        val appId = packageName
        val pm = packageManager
        val pInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(appId, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(appId, 0)
        }
        val vName = pInfo.versionName ?: "?"
        val vCode = if (android.os.Build.VERSION.SDK_INT >= 28)
            (pInfo.longVersionCode and 0xFFFFFFFF).toInt()
        else @Suppress("DEPRECATION") pInfo.versionCode
        return Triple(appId, vName, vCode)
    }
    private fun openFeedbackForm() {
        val (appId, vName, vCode) = appInfo()

        // Base du formulaire (ton FORM_ID)
        val formBase = "https://docs.google.com/forms/d/e/1FAIpQLSf1PBwF2QuVXHx8IfWM12jCh-7Tc0LhRgwfQZVBLZ_29bS6zg/viewform"

        // Tes champs préremplis (entry.*)
        val entryVersion = "entry.621899440"     // « Version de l’app »
        val entryAndroid = "entry.1531362918"    // « Android (version / SDK) »
        val entryDevice  = "entry.1583715954"    // « Appareil »

        val url = Uri.parse(formBase).buildUpon()
            .appendQueryParameter("usp", "pp_url") // mode prérempli
            .appendQueryParameter(entryVersion, "$appId $vName ($vCode)")
            .appendQueryParameter(entryAndroid, "${android.os.Build.VERSION.RELEASE} / SDK ${android.os.Build.VERSION.SDK_INT}")
            .appendQueryParameter(entryDevice, "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            .build()

        try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (_: Exception) {
            Toast.makeText(this, "Impossible d’ouvrir le formulaire.", Toast.LENGTH_LONG).show()
        }
    }
    private fun locateAndSelectCity() {
        // désactive le bouton pour éviter les doubles clics
        findViewById<Button?>(R.id.btnLocate)?.isEnabled = false

        val cts = CancellationTokenSource()
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        applyLocation(loc.latitude, loc.longitude)
                    } else {
                        // fallback: dernière position connue
                        fused.lastLocation
                            .addOnSuccessListener { last ->
                                if (last != null) applyLocation(last.latitude, last.longitude)
                                else Toast.makeText(this, "Position indisponible.", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Erreur position (fallback).", Toast.LENGTH_SHORT).show()
                            }
                            .addOnCompleteListener { findViewById<Button?>(R.id.btnLocate)?.isEnabled = true }
                        return@addOnSuccessListener
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Erreur position.", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener { findViewById<Button?>(R.id.btnLocate)?.isEnabled = true }
        } catch (_: SecurityException) {
            Toast.makeText(this, "Permission localisation requise.", Toast.LENGTH_SHORT).show()
            findViewById<Button?>(R.id.btnLocate)?.isEnabled = true
        } catch (_: Exception) {
            Toast.makeText(this, "Localisation indisponible.", Toast.LENGTH_SHORT).show()
            findViewById<Button?>(R.id.btnLocate)?.isEnabled = true
        }
    }
    private fun applyLocation(lat: Double, lon: Double) {
        ioScope.launch {
            val detected = try {
                reverseGeocode(lat, lon)
            } catch (e: Exception) {
                Log.e("MeteoQuote", "applyLocation/reverse failed", e)
                null
            } ?: City("Ma position", lat, lon)

            withContext(Dispatchers.Main) {
                selectOrInsertCity(detected)  // met le Spinner sur la bonne ville
                refresh()                     // recharge la météo pour cette ville
                Toast.makeText(this@MainActivity, "Ville détectée : ${detected.label}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    /** Met à jour le spinner/liste villes :
     * - si la ville existe déjà (même label) → on met à jour ses coords et on la sélectionne
     * - sinon → on l’insère en tête et on sélectionne 0
     * Puis on persiste. */
    private fun selectOrInsertCity(city: City) {
        // 1) cherche si le label existe déjà dans l'adapter
        val count = cityNamesAdapter.count
        var pos = -1
        for (i in 0 until count) {
            val lbl = cityNamesAdapter.getItem(i) ?: continue
            if (lbl.equals(city.label, ignoreCase = true)) { pos = i; break }
        }

        if (pos >= 0) {
            // Met à jour les coords dans la liste 'cities'
            val idx = cities.indexOfFirst { it.label.equals(city.label, ignoreCase = true) }
            if (idx >= 0) cities[idx] = city.copy(label = cities[idx].label) // garde le même label
            spinner.setSelection(pos, true)
        } else {
            // Insère en tête dans la liste ET dans l'adapter
            cities.add(0, city)
            cityNamesAdapter.insert(city.label, 0)
            spinner.setSelection(0, true)
        }

        saveCities()
    }



    private fun onLocationReady(lat: Double, lon: Double) {
        ioScope.launch {
            val detected = reverseGeocode(lat, lon) ?: City("Ma position", lat, lon)
            withContext(Dispatchers.Main) {
                // 1) Cherche la position de la ville dans l'adapter (ignore case)
                val count = cityNamesAdapter.count
                var pos = -1
                for (i in 0 until count) {
                    val lbl = cityNamesAdapter.getItem(i) ?: continue
                    if (lbl.equals(detected.label, ignoreCase = true)) { pos = i; break }
                }

                if (pos >= 0) {
                    val existing = cities.removeAt(cities.indexOfFirst { it.label.equals(detected.label, ignoreCase = true) })
                    cities.add(0, detected.copy(label = existing.label)) // garde le même label
                    cityNamesAdapter.remove(existing.label)
                    cityNamesAdapter.insert(existing.label, 0)
                    spinner.setSelection(0, true)
                } else {
                    // 4) Nouvelle ville → insère en tête dans les deux listes
                    cities.add(0, detected)
                    cityNamesAdapter.insert(detected.label, 0)
                    spinner.setSelection(0, true)
                }

                saveCities()
                refresh()
                Toast.makeText(this@MainActivity, "Ville détectée : ${detected.label}", Toast.LENGTH_SHORT).show()
            }
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
    }
}
