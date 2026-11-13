package com.valerie.meteoquote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import com.valerie.meteoquote.data.CityRepository
import com.valerie.meteoquote.data.model.City
import com.valerie.meteoquote.ui.WeatherViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.valerie.meteoquote.util.WeatherUtils
import kotlinx.coroutines.launch
import androidx.core.widget.doAfterTextChanged
import java.util.Locale
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var cityRepository: CityRepository
    
    private lateinit var forecastRenderer: com.valerie.meteoquote.ui.ForecastRenderer
    private lateinit var viewModel: WeatherViewModel
    private lateinit var spinner: Spinner
    private lateinit var ivIcon: ImageView
    private lateinit var tvCondition: TextView
    private lateinit var containerHourly: LinearLayout
    private lateinit var containerDaily: LinearLayout
    private lateinit var tvTemp: TextView
    private lateinit var cityNamesAdapter: ArrayAdapter<String>
    private lateinit var rootContainer: ViewGroup
    private lateinit var tvAqi: TextView
    private lateinit var containerRecentChips: LinearLayout
    private val recentCities = mutableListOf<City>()  // LRU (max 10)

    private lateinit var tvUv: TextView

    // --- Permissions localisation ---
    private val locationPermsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            pendingLocationRequest?.let { locateAndSelectCity(it) }
        } else {
            val showRationaleCoarse = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (!showRationaleCoarse) {
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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        containerRecentChips = findViewById(R.id.containerRecentChips)

// 1) Charger les récents et les afficher
        loadRecentCities()
        updateRecentChips()

        // Initialisation du ViewModel
        viewModel = ViewModelProvider(this)[WeatherViewModel::class.java]
        findViewById<androidx.compose.ui.platform.ComposeView>(R.id.quoteBlock).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.valerie.meteoquote.ui.compose.QuoteRoute(viewModel.uiState)
            }
        }

        tvUv = findViewById<ViewGroup>(R.id.uvCard).findViewById(R.id.metricValue)
        rootContainer = findViewById(R.id.rootContainer)
        spinner = findViewById(R.id.spinnerCities)
        ivIcon = findViewById(R.id.ivIcon)
        tvCondition = findViewById(R.id.tvCondition)
        tvTemp = findViewById(R.id.tvTemp)
        tvAqi = findViewById<ViewGroup>(R.id.aqiCard).findViewById(R.id.metricValue)
        containerHourly = findViewById(R.id.containerHourly)
        containerDaily = findViewById(R.id.containerDaily)
        forecastRenderer = com.valerie.meteoquote.ui.ForecastRenderer(
            this, containerHourly, containerDaily, findViewById(R.id.btnToggleDaily),
            savedInstanceState?.getBoolean("dailyExpanded") ?: false)

        val content = rootContainer.parent.parent as View
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(content)
        listOf(R.id.tvHourlyTitle, R.id.tvDailyTitle).forEach { ViewCompat.setAccessibilityHeading(findViewById(it), true) }
        setupSpinner()
        setupButtons()
        observeViewModel()

    }

    private fun setupSpinner() {
        viewModel.uiState.value.cities.let { cities ->
            cityNamesAdapter = ArrayAdapter(
                this,
                R.layout.item_city,
                cities.map { it.label }.toMutableList()
            )
            cityNamesAdapter.setDropDownViewResource(R.layout.item_city_dropdown)
            spinner.adapter = cityNamesAdapter
            if (cities.isNotEmpty()) {
                spinner.setSelection(viewModel.uiState.value.selectedCityIndex)
            }
        }

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (position != viewModel.uiState.value.selectedCityIndex) viewModel.selectCity(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { viewModel.refreshWeather() }
        findViewById<Button>(R.id.btnRetry).setOnClickListener { viewModel.refreshWeather() }
        findViewById<Button>(R.id.btnPrivacy).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.privacy_title)
                .setMessage(getString(R.string.privacy_notice))
                .setPositiveButton("Fermer", null).show()
        }

        findViewById<Button>(R.id.btnFeedback)?.setOnClickListener { openFeedbackForm() }

        findViewById<Button>(R.id.btnManageCities).setOnClickListener { showCityManager() }

        findViewById<Button>(R.id.btnLocate)?.setOnClickListener {
            val request = viewModel.beginLocationRequest()
            pendingLocationRequest = request
            ensureLocationPermission { locateAndSelectCity(request) }
        }

        val etCitySearch: EditText = findViewById(R.id.etCitySearch)
        // The ViewModel owns the draft across Activity recreation.
        etCitySearch.isSaveEnabled = false
        etCitySearch.setText(viewModel.searchState.value.query)
        etCitySearch.doAfterTextChanged { viewModel.updateSearchQuery(it.toString()) }
        findViewById<Button>(R.id.btnAddCity).setOnClickListener { viewModel.searchCity() }
        findViewById<Button>(R.id.btnCancelSearch).setOnClickListener { viewModel.cancelSearch() }
    }
    private fun showCityManager() {
        val cities = viewModel.uiState.value.cities.toList()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val scroll = android.widget.ScrollView(this).apply { addView(content) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.city_manage)
            .setView(scroll)
            .setNegativeButton(R.string.weather_data_close, null)
            .create()
        if (cities.size == 1) content.addView(TextView(this).apply { setText(R.string.city_keep_one) })
        cities.forEach { city ->
            fun action(label: String, description: String, onClick: () -> Unit) =
                com.google.android.material.button.MaterialButton(this).apply {
                    text = label
                    contentDescription = description
                    isAllCaps = false
                    minHeight = (48 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setOnClickListener { onClick() }
                }
            content.addView(action(city.label, getString(R.string.city_show_accessibility, city.label)) {
                viewModel.selectSavedCity(city)
                dialog.dismiss()
            })
            content.addView(action(getString(R.string.city_remove), getString(R.string.city_remove_accessibility, city.label)) {
                dialog.dismiss()
                AlertDialog.Builder(this)
                    .setTitle(R.string.city_remove_title)
                    .setMessage(getString(R.string.city_remove_confirm, city.label))
                    .setPositiveButton(R.string.city_remove) { _, _ ->
                        val removed = viewModel.removeCity(city)
                        if (removed) {
                            recentCities.removeAll { it.lat == city.lat && it.lon == city.lon }
                            saveRecentCities()
                            updateRecentChips()
                        }
                        Toast.makeText(this, if (removed) R.string.city_removed else R.string.city_remove_unavailable, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.city_search_cancel, null)
                    .show()
            }.apply { isEnabled = cities.size > 1 })
        }
        dialog.show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    while (true) {
                        viewModel.refreshDailyQuote()
                        viewModel.refreshVisibleWeather()
                        renderFreshness(viewModel.uiState.value, java.time.Instant.now())
                        kotlinx.coroutines.delay(60_000)
                    }
                }
                launch {
                    viewModel.searchState.collect { state ->
                        val input = findViewById<EditText>(R.id.etCitySearch)
                        if (input.text.toString() != state.query) input.setText(state.query)
                        findViewById<Button>(R.id.btnAddCity).isEnabled = !state.isSearching
                        findViewById<View>(R.id.searchProgress).visibility = if (state.isSearching) View.VISIBLE else View.GONE
                        findViewById<View>(R.id.btnCancelSearch).visibility =
                            if (state.isSearching || state.results.isNotEmpty()) View.VISIBLE else View.GONE
                        findViewById<TextView>(R.id.searchStatus).apply {
                            text = when {
                                state.isSearching -> getString(R.string.city_search_loading)
                                state.results.isNotEmpty() -> getString(R.string.city_search_choose)
                                else -> state.message.orEmpty()
                            }
                            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                        }
                        findViewById<LinearLayout>(R.id.searchResults).apply {
                            removeAllViews()
                            visibility = if (state.results.isEmpty()) View.GONE else View.VISIBLE
                            state.results.forEach { city ->
                                addView(com.google.android.material.button.MaterialButton(this@MainActivity).apply {
                                    text = city.label
                                    isAllCaps = false
                                    gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                                    minHeight = (48 * resources.displayMetrics.density).toInt()
                                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                    setOnClickListener { viewModel.chooseSearchResult(city, state.requestId) }
                                })
                            }
                        }
                        state.addedCity?.let {
                            addToRecents(it)
                            Toast.makeText(this@MainActivity, getString(R.string.city_search_selected, it.label), Toast.LENGTH_SHORT).show()
                            viewModel.consumeSearchMessage()
                        }
                    }
                }
                viewModel.uiState.collect { updateUI(it) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("dailyExpanded", forecastRenderer.isDailyExpanded)
        super.onSaveInstanceState(outState)
    }

    private fun renderFreshness(state: com.valerie.meteoquote.ui.WeatherUiState, now: java.time.Instant) {
        val hasWeather = state.currentTemp != null && !state.isLoading && state.error == null
        findViewById<TextView>(R.id.tvCurrentWeatherTitle).text =
            if (hasWeather) com.valerie.meteoquote.data.WeatherFreshness.title(state.observedAt, state.updatedAt, now)
            else getString(R.string.current_weather)
        findViewById<TextView>(R.id.tvUpdated).text = listOfNotNull(
            if (hasWeather) com.valerie.meteoquote.ui.WeatherFreshnessText.summary(
                this, state.observedAt, state.updatedAt, now) else null,
            getString(R.string.weather_refreshing).takeIf { state.isRefreshing && hasWeather },
            state.notice).joinToString("\n")
        findViewById<Button>(R.id.btnWeatherDetails).apply {
            visibility = if (hasWeather) View.VISIBLE else View.GONE
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.weather_data_dates)
                    .setMessage(com.valerie.meteoquote.data.WeatherFreshness.label(
                        state.observedAt, state.updatedAt, state.zoneId, java.time.Instant.now()).replace(" · ", "\n\n"))
                    .setPositiveButton(R.string.weather_data_close, null)
                    .show()
            }
        }
    }

    private fun updateUI(state: com.valerie.meteoquote.ui.WeatherUiState) {
        val labels = state.cities.map { it.label }
        if ((0 until cityNamesAdapter.count).map { cityNamesAdapter.getItem(it) } != labels) {
            cityNamesAdapter.clear()
            cityNamesAdapter.addAll(labels)
        }
        spinner.contentDescription = "Ville sélectionnée : " + labels.getOrNull(state.selectedCityIndex).orEmpty()
        if (spinner.selectedItemPosition != state.selectedCityIndex) spinner.setSelection(state.selectedCityIndex)



        findViewById<Button>(R.id.btnRefresh).isEnabled = !state.isLoading && !state.isRefreshing
        findViewById<Button>(R.id.btnRefresh).visibility = if (state.error == null && state.notice == null) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnRetry).apply {
            visibility = if ((state.error != null || state.notice != null) && !state.isLoading) View.VISIBLE else View.GONE
            isEnabled = !state.isLoading && !state.isRefreshing
        }
        val now = java.time.Instant.now()
        renderFreshness(state, now)
        // Gestion du chargement
        if (state.isLoading) {
            containerHourly.removeAllViews()
            forecastRenderer.renderDaily(emptyList(), true)
            findViewById<View>(R.id.uvCard).visibility = View.GONE
            findViewById<View>(R.id.aqiCard).visibility = View.GONE
            tvCondition.text = "Chargement…"
            tvTemp.text = "— °C"
            return
        }

        // Gestion des erreurs
        state.error?.let { error ->
            ivIcon.setImageResource(WeatherUtils.wmoToIconRes(3)) // fallback "nuageux"
            // Message d'erreur plus clair
            val errorMessage = if (state.errorKind != null) error else when {
                error.contains("Pas de connexion", ignoreCase = true) -> "Pas de connexion internet"
                error.contains("Délai", ignoreCase = true) -> "Connexion lente ou indisponible"
                error.contains("réseau", ignoreCase = true) -> "Erreur réseau. Vérifiez votre connexion."
                else -> "Météo indisponible. Réessayez dans un instant."
            }
            applyWeatherTheme(-1)
            tvCondition.text = errorMessage
            tvTemp.text = "— °C"
            containerHourly.removeAllViews()
            forecastRenderer.renderDaily(emptyList(), true)
            // Masquer les badges UV et AQI en cas d'erreur
            findViewById<View>(R.id.uvCard).visibility = View.GONE
            findViewById<View>(R.id.aqiCard).visibility = View.GONE
            return
        }

        // Mise à jour de la météo
        state.currentTemp?.let { temp ->
            state.weatherCode?.let { code ->
                ivIcon.setImageResource(WeatherUtils.wmoToIconRes(code))
                tvCondition.text = state.condition
                ivIcon.contentDescription = state.condition
                tvTemp.text = String.format(Locale.FRANCE, "%.1f °C", temp)

                applyWeatherTheme(code)
                val light = true
                applyContentColors()
                ivIcon.background = null

                renderMetric(tvUv, "Indice UV",
                    if (state.uvNow >= 0) String.format(Locale.FRANCE, "%.1f", state.uvNow) else "—",
                    if (state.uvMaxToday >= 0) "Risque au pic : " + state.uvLabel.lowercase(Locale.FRENCH) else "Indisponible",
                    if (state.uvMaxToday >= 0) String.format(Locale.FRANCE, "Pic %.1f à %s", state.uvMaxToday, state.uvPeakTime ?: "—") else "Pic indisponible",
                    state.uvColor)
                renderMetric(tvAqi, "Air · AQI européen",
                    if (state.aqiNow >= 0) state.aqiNow.toString() else "—",
                    if (state.isAirQualityLoading) "Chargement…" else if (state.aqiNow >= 0) state.aqiLabel +
                        (state.airFetchedAt?.takeIf { !com.valerie.meteoquote.data.CachePolicy.fresh(it.toEpochMilli(), java.time.Instant.now()) }
                            ?.let { " · " + com.valerie.meteoquote.data.CachePolicy.label(it, java.time.Instant.now()) } ?: "") else "Indisponible",
                    if (state.isAirQualityLoading) "" else if (state.aqiMaxToday >= 0) "Pic ${state.aqiMaxToday} à ${state.aqiPeakTime ?: "—"}" else "Pic indisponible",
                    state.aqiColor)
                // Rendus
                forecastRenderer.renderHourly(state.hourlyForecasts, light)
                forecastRenderer.renderDaily(state.dailyForecasts, light)
            }
        }

    }

    private fun renderMetric(view: TextView, title: String, value: String, level: String, peak: String, color: Int) {
        val panel = view.parent as LinearLayout
        panel.findViewById<TextView>(R.id.metricTitle).text = title
        view.text = value
        panel.findViewById<TextView>(R.id.metricLevel).text = level
        panel.findViewById<TextView>(R.id.metricPeak).text = peak
        panel.visibility = View.VISIBLE
        panel.background = GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.card_radius)
            setColor(androidx.core.graphics.ColorUtils.blendARGB(Color.WHITE, color, 0.10f))
            setStroke(dp(1), androidx.core.graphics.ColorUtils.blendARGB(Color.WHITE, color, 0.35f))
        }
        view.setTextColor(0xFF203A43.toInt())
        view.visibility = View.VISIBLE
    }
    @Suppress("DEPRECATION")
    private fun applyWeatherTheme(code: Int) {
        val tint = com.valerie.meteoquote.ui.WeatherMood.forCode(code).first
        findViewById<View>(R.id.weatherBlock).background = GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.card_radius)
            setColor(androidx.core.graphics.ColorUtils.blendARGB(Color.WHITE, tint, 0.5f))
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            window.statusBarColor = 0xFFF6F8F7.toInt()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun applyContentColors() {
        val primary = 0xFF203A43.toInt()
        val secondary = 0xFF52646B.toInt()

        tvCondition.setTextColor(primary)
        tvTemp.setTextColor(primary)

        findViewById<TextView>(R.id.tvAppTitle)?.setTextColor(primary)
        findViewById<TextView>(R.id.tvHourlyTitle)?.setTextColor(secondary)
        findViewById<TextView>(R.id.tvDailyTitle)?.setTextColor(secondary)

        findViewById<EditText>(R.id.etCitySearch)?.apply {
            setTextColor(primary)
            setHintTextColor(secondary)
        }

        (spinner.selectedView as? TextView)?.setTextColor(primary)
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
        return c
    }

    private fun requestLocationPermission() {
        locationPermsLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun ensureLocationPermission(onGranted: () -> Unit) {
        if (hasLocationPermission()) {
            onGranted()
        } else {
            val showRationaleCoarse = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (showRationaleCoarse) {
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

    private var locationJob: kotlinx.coroutines.Job? = null
    private var pendingLocationRequest: Long? = null

    private fun locateAndSelectCity(request: Long) {
        locationJob?.cancel()
        locationJob = lifecycleScope.launch {
            findViewById<Button>(R.id.btnLocate).isEnabled = false
            try {
                val manager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                if (!androidx.core.location.LocationManagerCompat.isLocationEnabled(manager)) {
                    Toast.makeText(this@MainActivity, "Localisation désactivée. Activez-la dans les paramètres ou recherchez une ville.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val loc = com.valerie.meteoquote.ui.currentLocation(this@MainActivity)
                if (loc == null) {
                    Toast.makeText(this@MainActivity, "Position indisponible. Recherchez une ville.", Toast.LENGTH_LONG).show()
                } else viewModel.detectLocation(loc.latitude, loc.longitude, request)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, "Localisation indisponible. La recherche reste accessible.", Toast.LENGTH_LONG).show()
            } finally {
                findViewById<Button>(R.id.btnLocate).isEnabled = true
            }
        }
    }

    override fun onStop() {
        locationJob?.cancel()
        pendingLocationRequest = null
        viewModel.cancelLocationRequest()
        super.onStop()
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
        findViewById<View>(R.id.recentChipsScroll).visibility = if (recentCities.isEmpty()) View.GONE else View.VISIBLE

        // Puis les récents
        recentCities.forEach { city ->
            val tv = TextView(this).apply {
                // même rendu que WidgetChipPill
                setBackgroundResource(R.drawable.bg_surface)
                setTextColor(0xFF315E6B.toInt())
                setPadding(dp(12), dp(6), dp(12), dp(6))
                minimumHeight = dp(48)
                gravity = android.view.Gravity.CENTER_VERTICAL
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

    }

    private fun selectCityFromChip(city: City) {
        viewModel.addCity(city)
        addToRecents(city)
    }
}

