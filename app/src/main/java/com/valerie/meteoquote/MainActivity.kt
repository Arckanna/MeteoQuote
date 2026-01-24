package com.valerie.meteoquote

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.valerie.meteoquote.data.CityRepository
import com.valerie.meteoquote.data.QuoteRepository
import com.valerie.meteoquote.data.WeatherRepository
import com.valerie.meteoquote.data.model.City
import com.valerie.meteoquote.ui.WeatherViewModel
import com.valerie.meteoquote.ui.WeatherViewModelFactory
import com.valerie.meteoquote.util.WeatherUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.core.view.WindowInsetsCompat
import android.graphics.drawable.TransitionDrawable
import android.animation.ValueAnimator
import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import android.content.Intent
import android.net.Uri
import android.view.View
import android.content.res.ColorStateList
import android.util.Log
import java.net.HttpURLConnection
import androidx.appcompat.app.AlertDialog
import android.provider.Settings
import android.location.Geocoder
import java.io.IOException
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface

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
    val aqiMaxToday: Int,
    val aqiPeakTime: LocalDateTime?
)

class MainActivity : AppCompatActivity() {
    
    private lateinit var viewModel: WeatherViewModel
    private lateinit var spinner: Spinner
    private lateinit var ivIcon: ImageView
    private lateinit var tvCondition: TextView
    private lateinit var containerHourly: LinearLayout
    private lateinit var containerDaily: LinearLayout
    private lateinit var tvTemp: TextView
    private lateinit var tvQuote: TextView
    private lateinit var cityNamesAdapter: ArrayAdapter<String>
    private lateinit var rootContainer: ViewGroup
    private lateinit var tvAqi: TextView
    private lateinit var containerRecentChips: LinearLayout
    private val recentCities = mutableListOf<City>()  // LRU (max 10)

    private var tvUv: TextView? = null
    private var currentThemeRes: Int? = null
    private var currentStatusColor: Int? = null
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var suppressNextRefresh = true

    // --- Permissions localisation ---
    private val locationPermsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = (perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true) ||
                (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true)
        if (granted) {
            locateAndSelectCity()
        } else {
            val showRationaleFine = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            val showRationaleCoarse = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (!showRationaleFine && !showRationaleCoarse) {
                AlertDialog.Builder(this)
                    .setTitle("Permission requise")
                    .setMessage("Active la localisation dans les paramètres pour utiliser « Ma position ».")
                    .setPositiveButton("Ouvrir les paramètres") { _, _ -> openAppSettings() }
                    .setNegativeButton("Plus tard", null)
                    .show()
            } else {
                Toast.makeText(this, "Permission localisation refusée", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        containerRecentChips = findViewById(R.id.containerRecentChips)

// 1) Charger les récents et les afficher
        loadRecentCities()
        updateRecentChips()

// 2) Chip "Ma position" → réutilise ton bouton existant
        findViewById<View>(R.id.chipMyLocation).setOnClickListener {
            // On déclenche exactement le même flux que ton bouton de localisation
            findViewById<View?>(R.id.btnLocate)?.performClick()
        }

        // Initialisation du ViewModel
        val factory = WeatherViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[WeatherViewModel::class.java]

        tvUv = findViewById(R.id.tvUv)
        rootContainer = findViewById(R.id.rootContainer)
        spinner = findViewById(R.id.spinnerCities)
        ivIcon = findViewById(R.id.ivIcon)
        tvCondition = findViewById(R.id.tvCondition)
        tvTemp = findViewById(R.id.tvTemp)
        tvQuote = findViewById(R.id.tvQuote)
        tvAqi = findViewById(R.id.tvAqi)
        containerHourly = findViewById(R.id.containerHourly)
        containerDaily = findViewById(R.id.containerDaily)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, sys.top, v.paddingRight, sys.bottom)
            insets
        }

        setupSpinner()
        setupButtons()
        observeViewModel()

        rootContainer.post { ensureLocationPermission { } }
    }

    private fun setupSpinner() {
        viewModel.uiState.value.cities.let { cities ->
            cityNamesAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                cities.map { it.label }.toMutableList()
            )
            spinner.adapter = cityNamesAdapter
            if (cities.isNotEmpty()) {
                spinner.setSelection(viewModel.uiState.value.selectedCityIndex)
            }
        }

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (suppressNextRefresh) {
                    suppressNextRefresh = false
                    return
                }
                viewModel.selectCity(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnNewQuote).setOnClickListener {
            viewModel.nextQuote()
        }

        findViewById<Button?>(R.id.btnFeedback)?.setOnClickListener { openFeedbackForm() }

        findViewById<Button?>(R.id.btnDeleteCity)?.setOnClickListener {
            val currentIndex = viewModel.uiState.value.selectedCityIndex
            val cities = viewModel.uiState.value.cities
            if (cities.size > 1) {
                AlertDialog.Builder(this)
                    .setTitle("Supprimer la ville")
                    .setMessage("Voulez-vous supprimer « ${cities[currentIndex].label} » ?")
                    .setPositiveButton("Supprimer") { _, _ ->
                        viewModel.removeCity(currentIndex)
                        Toast.makeText(this, "Ville supprimée", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            } else {
                Toast.makeText(this, "Impossible de supprimer : il doit rester au moins une ville", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button?>(R.id.btnLocate)?.setOnClickListener {
            ensureLocationPermission { locateAndSelectCity() }
        }

        val etCitySearch: EditText = findViewById(R.id.etCitySearch)
        findViewById<Button>(R.id.btnAddCity).setOnClickListener {
            val q = etCitySearch.text.toString().trim()
            if (q.isEmpty()) {
                Toast.makeText(this, "Saisis un nom de ville", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Désactiver le bouton pendant la recherche
            findViewById<Button>(R.id.btnAddCity).isEnabled = false
            lifecycleScope.launch {
                try {
                    val cityRepository = CityRepository(this@MainActivity)
                    val found = withContext(Dispatchers.IO) {
                        cityRepository.geocodeCity(q)
                    }
                    withContext(Dispatchers.Main) {
                        if (found == null) {
                            Toast.makeText(this@MainActivity, "Ville introuvable", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addCity(found)
                            etCitySearch.text.clear()
                            Toast.makeText(this@MainActivity, "Ville ajoutée : ${found.label}", Toast.LENGTH_SHORT).show()
                        }
                        findViewById<Button>(R.id.btnAddCity).isEnabled = true
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Erreur : ${e.message ?: "Impossible d'ajouter la ville"}", Toast.LENGTH_SHORT).show()
                        findViewById<Button>(R.id.btnAddCity).isEnabled = true
                    }
                    // Sélectionne la ville et rafraîchit
                    val pos = cityNamesAdapter.getPosition(found.label).coerceAtLeast(0)
                    spinner.setSelection(pos)
                    addToRecents(found)
                    refresh()
                }
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun refresh() {
        val index = spinner.selectedItemPosition.coerceIn(0, cities.lastIndex)
        val city = cities[index]

        // État d'attente
        tvCondition.text = "Chargement…"
        tvTemp.text = "— °C"

        ioScope.launch {
            try {
                val result = fetchFullWeather(city.lat, city.lon)
                val (temp, code) = result.current
                val label = wmoToLabel(code)

                withContext(Dispatchers.Main) {
                    ivIcon.setImageResource(wmoToIconRes(code))
                    tvCondition.text = label
                    tvTemp.text = String.format("%.1f °C", temp)

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
                        val uvTop = String.format(
                            Locale.FRANCE, "UV %.1f • pic %.1f à %s",
                            result.uvNow, result.uvMaxToday, peakTxt
                        )
                        val uvBottom = "Risque UV\u202F: ${uvLabel.lowercase(Locale.FRENCH)}"
                        tvUv?.text = boldLine1SmallLine2(uvTop, uvBottom)
                        backgroundTintList = ColorStateList.valueOf(uvColor)
                        setTextColor(Color.WHITE)
                        visibility = View.VISIBLE
                    }

                    // AQI (now + pic heure)
                    val (aqiLabel, aqiColor) = aqiCategoryEU(result.aqiNow)
                    tvAqi?.let { aqi ->
                        aqi.setBackgroundResource(R.drawable.bg_uv_chip)
                        val aqiPeakTxt = result.aqiPeakTime
                            ?.format(DateTimeFormatter.ofPattern("HH:mm"))
                            ?: "—"
                        val aqiTop = "AQI ${result.aqiNow} • pic ${result.aqiMaxToday} à $aqiPeakTxt"
                        val aqiBottom = "Qualité de l’air\u202F: ${aqiLabel.lowercase(Locale.FRENCH)}"
                        tvAqi?.text = boldLine1SmallLine2(aqiTop, aqiBottom)
                        ViewCompat.setBackgroundTintList(aqi, ColorStateList.valueOf(aqiColor))
                        aqi.setTextColor(Color.WHITE)
                        aqi.visibility = View.VISIBLE
                    }

    private fun updateUI(state: com.valerie.meteoquote.ui.WeatherUiState) {
        // Mise à jour du spinner si les villes ont changé
        if (state.cities.size != cityNamesAdapter.count) {
            cityNamesAdapter.clear()
            cityNamesAdapter.addAll(state.cities.map { it.label })
            if (state.selectedCityIndex in state.cities.indices) {
                suppressNextRefresh = true
                spinner.setSelection(state.selectedCityIndex)
            }
        }
        
        // Activer/désactiver le bouton de suppression selon le nombre de villes
        findViewById<Button?>(R.id.btnDeleteCity)?.isEnabled = state.cities.size > 1

        // Gestion du chargement
        if (state.isLoading) {
            tvCondition.text = "Chargement…"
            tvTemp.text = "— °C"
            return
        }

        // Gestion des erreurs
        state.error?.let { error ->
            ivIcon.setImageResource(WeatherUtils.wmoToIconRes(3)) // fallback "nuageux"
            // Message d'erreur plus clair
            val errorMessage = when {
                error.contains("Pas de connexion", ignoreCase = true) -> "Pas de connexion internet"
                error.contains("Délai", ignoreCase = true) -> "Connexion lente ou indisponible"
                error.contains("réseau", ignoreCase = true) -> "Erreur réseau. Vérifiez votre connexion."
                else -> "Erreur : $error"
            }
            tvCondition.text = errorMessage
            tvTemp.text = "— °C"
            containerHourly.removeAllViews()
            containerDaily.removeAllViews()
            // Masquer les badges UV et AQI en cas d'erreur
            tvUv?.visibility = View.GONE
            tvAqi?.visibility = View.GONE
            return
        }

        // Mise à jour de la météo
        state.currentTemp?.let { temp ->
            state.weatherCode?.let { code ->
                ivIcon.setImageResource(WeatherUtils.wmoToIconRes(code))
                tvCondition.text = state.condition
                tvTemp.text = String.format("%.1f °C", temp)

                applyWeatherTheme(code)
                val light = WeatherUtils.isBgLight(code)
                applyContentColorsFor(light)
                applyIconScrim(ivIcon, light, 8)

                // UV badge
                tvUv?.apply {
                    setBackgroundResource(R.drawable.bg_uv_chip)
                    val uvTop = String.format(
                        Locale.FRANCE, "UV %.1f • pic %.1f à %s",
                        state.uvNow, state.uvMaxToday, state.uvPeakTime ?: "—"
                    )
                    val uvBottom = "Risque UV\u202F: ${state.uvLabel.lowercase(Locale.FRENCH)}"
                    text = boldLine1SmallLine2(uvTop, uvBottom)
                    backgroundTintList = ColorStateList.valueOf(state.uvColor)
                    setTextColor(Color.WHITE)
                    visibility = View.VISIBLE
                }

                // AQI
                tvAqi?.let { aqi ->
                    aqi.setBackgroundResource(R.drawable.bg_uv_chip)
                    val aqiTop = "AQI ${state.aqiNow} • pic ${state.aqiMaxToday} à ${state.aqiPeakTime ?: "—"}"
                    val aqiBottom = "Qualité de l'air\u202F: ${state.aqiLabel.lowercase(Locale.FRENCH)}"
                    aqi.text = boldLine1SmallLine2(aqiTop, aqiBottom)
                    ViewCompat.setBackgroundTintList(aqi, ColorStateList.valueOf(state.aqiColor))
                    aqi.setTextColor(Color.WHITE)
                    aqi.visibility = View.VISIBLE
                }

                // Rendus
                renderHourly(state.hourlyForecasts, light)
                renderDaily(state.dailyForecasts, light)
            }
        }

        // Citation
        tvQuote.text = state.quote
    }

    private fun boldLine1SmallLine2(top: String, bottom: String) =
        SpannableStringBuilder()
            .append(top, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            .append("\n")
            .append(bottom, RelativeSizeSpan(0.92f), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    private fun applyWeatherTheme(code: Int, durationMs: Long = 350L) {
        val newBgRes = WeatherUtils.themeDrawableRes(code)
        val newStatus = ContextCompat.getColor(this, WeatherUtils.statusColorRes(code))

        val oldBgRes = currentThemeRes
        if (oldBgRes == null) {
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
                addUpdateListener { anim -> window.statusBarColor = anim.animatedValue as Int }
                start()
            }
            currentStatusColor = newStatus
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun renderHourly(items: List<com.valerie.meteoquote.data.model.HourlyForecast>, useDarkText: Boolean) {
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
                setImageResource(WeatherUtils.wmoToIconRes(h.code))
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                contentDescription = WeatherUtils.wmoToLabel(h.code)
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

    private fun renderDaily(items: List<com.valerie.meteoquote.data.model.DailyForecast>, useDarkText: Boolean) {
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
                setImageResource(WeatherUtils.wmoToIconRes(d.code))
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(12) }
                contentDescription = WeatherUtils.wmoToLabel(d.code)
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

    private fun isBgLight(code: Int): Boolean = when (code) {
        0, 1, 2, 3, 45, 48, 71, 73, 75, 85, 86 -> true
        else -> false
    }

    private fun applyContentColorsFor(useDarkText: Boolean) {
        val primary = 0xFF111111.toInt()
        val secondary = 0xFF5E5E5E.toInt()

        tvCondition.setTextColor(primary)
        tvTemp.setTextColor(primary)
        tvQuote.setTextColor(primary)

        findViewById<TextView?>(R.id.tvAppTitle)?.setTextColor(primary)
        findViewById<TextView?>(R.id.tvHourlyTitle)?.setTextColor(secondary)
        findViewById<TextView?>(R.id.tvDailyTitle)?.setTextColor(secondary)
        findViewById<TextView?>(R.id.tvQuoteTitle)?.setTextColor(secondary)

        findViewById<EditText?>(R.id.etCitySearch)?.apply {
            setTextColor(primary)
            setHintTextColor(secondary)
        }

        (spinner.selectedView as? TextView)?.setTextColor(primary)
    }

    private fun pad(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun applyIconScrim(iv: ImageView, useDarkText: Boolean, paddingDp: Int) {
        val color = if (useDarkText) 0x33000000 else 0xB3FFFFFF.toInt()
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
        val pInfo = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(appId, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(appId, 0)
        }
        val vName = pInfo.versionName ?: "?"
        val vCode = if (Build.VERSION.SDK_INT >= 28)
            (pInfo.longVersionCode and 0xFFFFFFFF).toInt()
        else @Suppress("DEPRECATION") pInfo.versionCode
        return Triple(appId, vName, vCode)
    }

    private fun openFeedbackForm() {
        val (appId, vName, vCode) = appInfo()
        val formBase = "https://docs.google.com/forms/d/e/1FAIpQLSf1PBwF2QuVXHx8IfWM12jCh-7Tc0LhRgwfQZVBLZ_29bS6zg/viewform"
        val entryVersion = "entry.621899440"
        val entryAndroid = "entry.1531362918"
        val entryDevice = "entry.1583715954"

        val url = Uri.parse(formBase).buildUpon()
            .appendQueryParameter("usp", "pp_url")
            .appendQueryParameter(entryVersion, "$appId $vName ($vCode)")
            .appendQueryParameter(entryAndroid, "${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            .appendQueryParameter(entryDevice, "${Build.MANUFACTURER} ${Build.MODEL}")
            .build()

        try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (_: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir le formulaire.", Toast.LENGTH_LONG).show()
        }
    }

    // ===== Localisation =====

    private fun hasLocationPermission(): Boolean {
        val c = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val f = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return c || f
    }

    private fun requestLocationPermission() {
        locationPermsLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun ensureLocationPermission(onGranted: () -> Unit) {
        if (hasLocationPermission()) {
            onGranted()
        } else {
            val showRationaleFine = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            val showRationaleCoarse = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (showRationaleFine || showRationaleCoarse) {
                AlertDialog.Builder(this)
                    .setTitle("Utiliser ta position")
                    .setMessage("Nous avons besoin de ta localisation pour détecter ta ville actuelle.")
                    .setPositiveButton("Continuer") { _, _ -> requestLocationPermission() }
                    .setNegativeButton("Annuler", null)
                    .show()
            } else {
                requestLocationPermission()
            }
        }
    }

    private fun openAppSettings() {
        val uri = Uri.parse("package:$packageName")
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
    }

    private fun locateAndSelectCity() {
        findViewById<Button?>(R.id.btnLocate)?.isEnabled = false

        val cts = CancellationTokenSource()
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        viewModel.detectLocation(loc.latitude, loc.longitude)
                        Toast.makeText(this, "Position détectée", Toast.LENGTH_SHORT).show()
                    } else {
                        fused.lastLocation
                            .addOnSuccessListener { last ->
                                if (last != null) {
                                    viewModel.detectLocation(last.latitude, last.longitude)
                                    Toast.makeText(this, "Position détectée", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "Position indisponible.", Toast.LENGTH_SHORT).show()
                                }
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
                selectOrInsertCity(detected)
                refresh()
                Toast.makeText(this@MainActivity, "Ville détectée : ${detected.label}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Met à jour le spinner/liste villes : met à jour ou insère la ville détectée, puis persiste. */
    private fun selectOrInsertCity(city: City) {
        val count = cityNamesAdapter.count
        var pos = -1
        for (i in 0 until count) {
            val lbl = cityNamesAdapter.getItem(i) ?: continue
            if (lbl.equals(city.label, ignoreCase = true)) { pos = i; break }
        }

        if (pos >= 0) {
            val idx = cities.indexOfFirst { it.label.equals(city.label, ignoreCase = true) }
            if (idx >= 0) cities[idx] = city.copy(label = cities[idx].label) // garde le même label
            spinner.setSelection(pos, true)
        } else {
            cities.add(0, city)
            cityNamesAdapter.insert(city.label, 0)
            spinner.setSelection(0, true)
        }

        saveCities()
        addToRecents(city)
    }

    /** Ajoute une ville en tête de la LRU (max 10), sans doublon (lat/lon). */
    private fun addToRecents(city: City) {
        // dédup lat/lon (exacts) ; tu peux raffiner si besoin
        recentCities.removeAll { it.lat == city.lat && it.lon == city.lon }
        recentCities.add(0, city)
        if (recentCities.size > 10) {
            recentCities.removeAt(recentCities.lastIndex)   // ✅ compat 24
        }
        saveRecentCities()
        updateRecentChips()
    }

    /** Persistance LRU */
    private fun saveRecentCities() {
        val arr = JSONArray()
        recentCities.forEach { c ->
            arr.put(JSONObject().apply {
                put("label", c.label); put("lat", c.lat); put("lon", c.lon)
            })
        }
        getSharedPreferences("meteoquote_prefs", Context.MODE_PRIVATE)
            .edit().putString("recent_cities_json", arr.toString()).apply()
    }

    /** Chargement LRU */
    private fun loadRecentCities() {
        val json = getSharedPreferences("meteoquote_prefs", Context.MODE_PRIVATE)
            .getString("recent_cities_json", null) ?: return
        runCatching {
            val arr = JSONArray(json)
            recentCities.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                recentCities.add(City(o.getString("label"), o.getDouble("lat"), o.getDouble("lon")))
            }
        }
    }

    /** Construit les chips récents dynamiquement. */
    private fun updateRecentChips() {
        containerRecentChips.removeAllViews()

        // Laisse "Ma position" en premier
        val locChip = layoutInflater.inflate(R.layout.simple_chip_pill, containerRecentChips, false) as TextView?
            ?: TextView(this).apply { text = "Ma position" }
        locChip.id = R.id.chipMyLocation
        // Si tu veux garder le locChip en premier, on le rajoute tout de suite :
        containerRecentChips.addView(locChip)

        // Puis les récents
        recentCities.forEach { city ->
            val tv = TextView(this).apply {
                // même rendu que WidgetChipPill
                setBackgroundResource(R.drawable.bg_uv_chip)
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                text = city.label
                // petite marge à gauche
                val lp = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.leftMargin = dp(8)
                layoutParams = lp
                setOnClickListener {
                    selectCityFromChip(city)
                }
                setOnLongClickListener {
                    // Appui long : épingler aux favoris (Étape 2), ou retirer des récents :
                    // recentCities.removeAll { it.lat==city.lat && it.lon==city.lon }; saveRecentCities(); updateRecentChips(); true
                    false
                }
            }
            containerRecentChips.addView(tv)
        }

        // Rewire "Ma position" pour éviter de perdre le listener
        locChip.setOnClickListener {
            findViewById<View?>(R.id.btnLocate)?.performClick()
        }
    }

    /** Sélectionne la ville via le spinner existant (ajoute si absente), puis refresh. */
    private fun selectCityFromChip(city: City) {
        ensureCityInSpinner(city)
        val pos = cityNamesAdapter.getPosition(city.label).coerceAtLeast(0)
        spinner.setSelection(pos)
        refresh()
    }

    /** Garantit que la ville est présente dans le spinner/cities. */
    private fun ensureCityInSpinner(city: City) {
        val exists = cities.any { it.lat == city.lat && it.lon == city.lon }
        if (!exists) {
            cities.add(city)
            cityNamesAdapter.add(city.label)
            saveCities()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
    }
}
