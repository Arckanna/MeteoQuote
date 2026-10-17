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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
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
import com.valerie.meteoquote.ui.WeatherViewModelFactory
import com.valerie.meteoquote.util.WeatherUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException

class MainActivity : AppCompatActivity() {
    
    private lateinit var forecastRenderer: com.valerie.meteoquote.ui.ForecastRenderer
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



    // --- Permissions localisation ---
    private val locationPermsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            locateAndSelectCity()
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

// 2) Chip "Ma position" → réutilise ton bouton existant
        findViewById<View>(R.id.chipMyLocation).setOnClickListener {
            // On déclenche exactement le même flux que ton bouton de localisation
            findViewById<View>(R.id.btnLocate)?.performClick()
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
        forecastRenderer = com.valerie.meteoquote.ui.ForecastRenderer(this, containerHourly, containerDaily)

        val content = rootContainer.parent as View
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(content)
        setupSpinner()
        setupButtons()
        observeViewModel()


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
                if (position != viewModel.uiState.value.selectedCityIndex) viewModel.selectCity(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { viewModel.refreshWeather() }
        findViewById<Button>(R.id.btnPrivacy).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.privacy_title)
                .setMessage(getString(R.string.privacy_notice))
                .setPositiveButton("Fermer", null).show()
        }
        findViewById<Button>(R.id.btnNewQuote).setOnClickListener {
            viewModel.nextQuote()
        }

        findViewById<Button>(R.id.btnFeedback)?.setOnClickListener { openFeedbackForm() }

        findViewById<Button>(R.id.btnDeleteCity)?.setOnClickListener {
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

        findViewById<Button>(R.id.btnLocate)?.setOnClickListener {
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
                            addToRecents(found)
                            etCitySearch.text.clear()
                            Toast.makeText(this@MainActivity, "Ville ajoutée : ${found.label}", Toast.LENGTH_SHORT).show()
                        }
                        findViewById<Button>(R.id.btnAddCity).isEnabled = true
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Erreur : ${e.message ?: "Impossible d'ajouter la ville"}", Toast.LENGTH_SHORT).show()
                        findViewById<Button>(R.id.btnAddCity).isEnabled = true
                    }

                }
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { updateUI(it) }
            }
        }
    }

    private fun updateUI(state: com.valerie.meteoquote.ui.WeatherUiState) {
        val labels = state.cities.map { it.label }
        if ((0 until cityNamesAdapter.count).map { cityNamesAdapter.getItem(it) } != labels) {
            cityNamesAdapter.clear()
            cityNamesAdapter.addAll(labels)
        }
        if (spinner.selectedItemPosition != state.selectedCityIndex) spinner.setSelection(state.selectedCityIndex)
        findViewById<Button>(R.id.btnNewQuote).isEnabled = !state.isLoading && state.error == null
        // Activer/désactiver le bouton de suppression selon le nombre de villes
        findViewById<Button>(R.id.btnDeleteCity)?.isEnabled = state.cities.size > 1

        findViewById<Button>(R.id.btnRefresh).isEnabled = !state.isLoading
        findViewById<TextView>(R.id.tvUpdated).text = if (state.isLoading || state.error != null) "" else state.updatedAt?.let {
            "Actualisé le " + DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.FRANCE)
                .withZone(java.time.ZoneId.systemDefault()).format(it)
        } ?: ""
        // Gestion du chargement
        if (state.isLoading) {
            tvQuote.text = "—"
            containerHourly.removeAllViews()
            containerDaily.removeAllViews()
            tvUv?.visibility = View.GONE
            tvAqi.visibility = View.GONE
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
            tvQuote.text = "—"
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
                ivIcon.contentDescription = state.condition
                tvTemp.text = String.format(Locale.FRANCE, "%.1f °C", temp)

                applyWeatherTheme(code)
                val light = true
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
                    setTextColor(if (androidx.core.graphics.ColorUtils.calculateLuminance(state.uvColor) > 0.4) Color.BLACK else Color.WHITE)
                    visibility = if (state.uvMaxToday >= 0 && state.uvNow >= 0) View.VISIBLE else View.GONE
                }

                // AQI
                tvAqi?.let { aqi ->
                    aqi.setBackgroundResource(R.drawable.bg_uv_chip)
                    val aqiTop = if (state.aqiNow < 0) "AQI indisponible" else "AQI ${state.aqiNow} • pic ${state.aqiMaxToday} à ${state.aqiPeakTime ?: "—"}"
                    val aqiBottom = "Qualité de l'air\u202F: ${state.aqiLabel.lowercase(Locale.FRENCH)}"
                    aqi.text = boldLine1SmallLine2(aqiTop, aqiBottom)
                    ViewCompat.setBackgroundTintList(aqi, ColorStateList.valueOf(state.aqiColor))
                    aqi.setTextColor(if (androidx.core.graphics.ColorUtils.calculateLuminance(state.aqiColor) > 0.4) Color.BLACK else Color.WHITE)
                    aqi.visibility = View.VISIBLE
                }

                // Rendus
                forecastRenderer.renderHourly(state.hourlyForecasts, light)
                forecastRenderer.renderDaily(state.dailyForecasts, light)
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

    @Suppress("DEPRECATION") // Status bar color is used only on API 34 and below.
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

    private fun applyContentColorsFor(useDarkText: Boolean) {
        val primary = 0xFF111111.toInt()
        val secondary = 0xFF5E5E5E.toInt()

        tvCondition.setTextColor(primary)
        tvTemp.setTextColor(primary)
        tvQuote.setTextColor(primary)

        findViewById<TextView>(R.id.tvAppTitle)?.setTextColor(primary)
        findViewById<TextView>(R.id.tvHourlyTitle)?.setTextColor(secondary)
        findViewById<TextView>(R.id.tvDailyTitle)?.setTextColor(secondary)
        findViewById<TextView>(R.id.tvQuoteTitle)?.setTextColor(secondary)

        findViewById<EditText>(R.id.etCitySearch)?.apply {
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

    private fun locateAndSelectCity() {
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
                } else viewModel.detectLocation(loc.latitude, loc.longitude)
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

        // Laisse "Ma position" en premier
        val locChip = layoutInflater.inflate(R.layout.simple_chip_pill, containerRecentChips, false) as TextView?
            ?: TextView(this).apply { text = "Ma position" }
        locChip.id = R.id.chipMyLocation
        locChip.text = "Ma position"
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
            findViewById<View>(R.id.btnLocate)?.performClick()
        }
    }

    private fun selectCityFromChip(city: City) {
        viewModel.addCity(city)
        addToRecents(city)
    }
}
