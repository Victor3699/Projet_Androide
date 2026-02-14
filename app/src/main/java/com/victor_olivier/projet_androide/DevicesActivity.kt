package com.victor_olivier.projet_androide

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.victor_olivier.projet_androide.data.api.Api
import com.victor_olivier.projet_androide.data.api.ApiRoutes
import com.victor_olivier.projet_androide.data.model.Device
import com.victor_olivier.projet_androide.data.model.DevicesResponse
import com.victor_olivier.projet_androide.data.storage.TokenStore

class DevicesActivity : AppCompatActivity() {

    companion object {
        private const val FILTER_ALL = "Tous"
        private const val FILTER_ON = "Allumés"
        private const val FILTER_OFF = "Éteints"
        private const val FILTER_OPEN = "Ouverts"
        private const val FILTER_CLOSED = "Fermés"

        private const val TYPE_LIGHT = "light"
        private const val TYPE_SHUTTER = "shutter"
        private const val TYPE_DOOR = "door"
        private const val TYPE_GARAGE = "garage"

        private val DEVICE_STATES = listOf(FILTER_ALL, FILTER_ON, FILTER_OFF, FILTER_OPEN, FILTER_CLOSED)
    }

    private var houseId: Int = -1
    private var token: String? = null
    private lateinit var houseUrl: String

    private lateinit var webHouse: WebView
    private lateinit var spinnerType: Spinner
    private lateinit var spinnerState: Spinner

    private lateinit var tvHouseId: TextView
    private lateinit var tvOwner: TextView
    private lateinit var tvLightsOn: TextView
    private lateinit var tvShuttersOpen: TextView
    private lateinit var tvDoorsOpen: TextView
    private lateinit var tvGarageOpen: TextView

    private lateinit var panelComponents: View
    private lateinit var panelGroup: View
    private lateinit var panelUsers: View

    private lateinit var btnSelectAll: MaterialButton
    private lateinit var btnBatchOn: MaterialButton
    private lateinit var btnBatchOff: MaterialButton

    private val allDevices = arrayListOf<Device>()
    private val filteredDevices = arrayListOf<Device>()
    private val selectedDeviceIds = linkedSetOf<String>()

    private var selectedType: String = FILTER_ALL
    private var selectedState: String = FILTER_ALL

    private var isLoadingDevices = false
    private var isBatchRunning = false
    private var devicesLoadedAtLeastOnce = false

    private var pendingBrowserInitRetry = false
    private var alreadyOpenedCustomTabForInit = false

    private var customTabsSession: CustomTabsSession? = null
    private var serviceConnection: CustomTabsServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_devices)

        houseId = intent.getIntExtra("houseId", -1)
        token = TokenStore(this).getToken()

        if (houseId == -1 || token.isNullOrBlank()) {
            Toast.makeText(this, "houseId/token manquant", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        houseUrl = ApiRoutes.HOUSE_BROWSER(houseId)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarDevices)
        toolbar.title = "Maison #$houseId"
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    doLogout()
                    true
                }
                else -> false
            }
        }

        webHouse = findViewById(R.id.webHouse)
        spinnerType = findViewById(R.id.spinnerType)
        spinnerState = findViewById(R.id.spinnerState)

        tvHouseId = findViewById(R.id.tvHouseId)
        tvOwner = findViewById(R.id.tvOwner)
        tvLightsOn = findViewById(R.id.tvLightsOn)
        tvShuttersOpen = findViewById(R.id.tvShuttersOpen)
        tvDoorsOpen = findViewById(R.id.tvDoorsOpen)
        tvGarageOpen = findViewById(R.id.tvGarageOpen)

        panelComponents = findViewById(R.id.panelComponents)
        panelGroup = findViewById(R.id.panelGroup)
        panelUsers = findViewById(R.id.panelUsers)

        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnBatchOn = findViewById(R.id.btnBatchOn)
        btnBatchOff = findViewById(R.id.btnBatchOff)

        panelComponents.visibility = View.VISIBLE
        panelGroup.visibility = View.GONE
        panelUsers.visibility = View.GONE

        tvHouseId.text = "Maison : #$houseId"
        tvOwner.text = "Propriétaire : (à venir)"

        setupAccordion(findViewById(R.id.btnToggleComponents), panelComponents)
        setupAccordion(findViewById(R.id.btnToggleGroup), panelGroup)
        setupAccordion(findViewById(R.id.btnToggleUsers), panelUsers)

        findViewById<View>(R.id.btnAddUser).setOnClickListener {
            Toast.makeText(this, "Add utilisateur (à venir)", Toast.LENGTH_SHORT).show()
        }

        adapter = DeviceAdapter()
        findViewById<android.widget.ListView>(R.id.lvDevices).adapter = adapter

        btnSelectAll.setOnClickListener {
            selectedDeviceIds.clear()
            selectedDeviceIds.addAll(filteredDevices.map { it.id })
            adapter.notifyDataSetChanged()
            updateBatchButtonsState()
        }
        btnBatchOn.setOnClickListener { executeBatchCommand(targetOn = true) }
        btnBatchOff.setOnClickListener { executeBatchCommand(targetOn = false) }

        setupStateSpinner()
        setupTypeSpinner(listOf(FILTER_ALL))

        setupWebView(webHouse)
        webHouse.loadUrl(houseUrl)
        warmupChromeAndPrefetch(houseUrl)
        loadDevices()
    }

    override fun onResume() {
        super.onResume()
        if (pendingBrowserInitRetry) {
            pendingBrowserInitRetry = false
            loadDevices()
        }
    }

    private fun doLogout() {
        TokenStore(this).saveToken("")
        Toast.makeText(this, "Déconnecté", Toast.LENGTH_SHORT).show()
        val i = Intent(this, MainActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }

    private fun setupAccordion(button: MaterialButton, panel: View) {
        button.setOnClickListener {
            val willShow = panel.visibility != View.VISIBLE
            panel.visibility = if (willShow) View.VISIBLE else View.GONE
            if (panel.id == R.id.panelComponents && willShow) applyFiltersAndRender()
        }
    }

    private fun setupWebView(webView: WebView) {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadDevices()
            }
        }
    }

    private fun warmupChromeAndPrefetch(url: String) {
        serviceConnection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(name: android.content.ComponentName, client: CustomTabsClient) {
                client.warmup(0L)
                customTabsSession = client.newSession(null)
                customTabsSession?.mayLaunchUrl(Uri.parse(url), null, null)
            }

            override fun onServiceDisconnected(name: android.content.ComponentName) {
                customTabsSession = null
            }
        }

        try {
            CustomTabsClient.bindCustomTabsService(this, "com.android.chrome", serviceConnection!!)
        } catch (e: Exception) {
            Log.d("API", "CustomTabs bind failed: ${e.message}")
        }
    }

    private fun openHouseInCustomTabForInit(url: String) {
        if (alreadyOpenedCustomTabForInit) return
        alreadyOpenedCustomTabForInit = true

        val intent = CustomTabsIntent.Builder(customTabsSession).setShowTitle(true).build()
        pendingBrowserInitRetry = true
        intent.launchUrl(this, Uri.parse(url))
    }

    private fun setupTypeSpinner(types: List<String>) {
        spinnerType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        val idx = types.indexOf(selectedType)
        if (idx >= 0) spinnerType.setSelection(idx)

        spinnerType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedType = types[position]
                applyFiltersAndRender()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setupStateSpinner() {
        val a = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, DEVICE_STATES)
        spinnerState.adapter = a

        spinnerState.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedState = DEVICE_STATES[position]
                applyFiltersAndRender()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun loadDevices() {
        if (isLoadingDevices) return
        val t = token ?: return
        isLoadingDevices = true

        Api().get<DevicesResponse>(
            ApiRoutes.DEVICES(houseId),
            onSuccess = { code, body ->
                runOnUiThread {
                    isLoadingDevices = false
                    Log.d("API", "DEVICES code=$code bodyNull=${body == null} size=${body?.devices?.size}")

                    if (code == 200 && body != null) {
                        devicesLoadedAtLeastOnce = true
                        alreadyOpenedCustomTabForInit = false

                        allDevices.clear()
                        allDevices.addAll(body.devices)

                        val types = mutableListOf(FILTER_ALL)
                        types.addAll(allDevices.map { it.type }.distinct().sorted())
                        setupTypeSpinner(types)

                        updateInfoPanel()
                        applyFiltersAndRender()

                        if (panelComponents.visibility != View.VISIBLE) {
                            panelComponents.visibility = View.VISIBLE
                        }

                    } else {
                        Toast.makeText(this, "Erreur devices ($code)", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            securityToken = t
        )
    }

    private fun updateInfoPanel() {
        val lightsOn = allDevices.count { it.isType(TYPE_LIGHT) && (it.power ?: 0) > 0 }
        val shuttersOpen = allDevices.count { it.isType(TYPE_SHUTTER) && (it.opening ?: 0) > 0 }
        val doorsOpen = allDevices.count {
            it.isType(TYPE_DOOR) &&
                    (it.opening ?: 0) > 0 &&
                    !it.isType(TYPE_GARAGE)
        }
        val garageOpen = allDevices.count { it.isType(TYPE_GARAGE) && (it.opening ?: 0) > 0 }

        tvLightsOn.text = "Lumières allumées : $lightsOn"
        tvShuttersOpen.text = "Volets ouverts : $shuttersOpen"
        tvDoorsOpen.text = "Portes ouvertes : $doorsOpen"
        tvGarageOpen.text = "Garage ouvert : $garageOpen"
    }

    private fun applyFiltersAndRender() {
        val filtered = allDevices.filter { d ->
            val okType = (selectedType == FILTER_ALL) || (d.type == selectedType)

            val okState = when (selectedState) {
                FILTER_ALL -> true
                FILTER_ON -> (d.power != null && d.power > 0)
                FILTER_OFF -> (d.power != null && d.power == 0)
                FILTER_OPEN -> (d.opening != null && d.opening > 0)
                FILTER_CLOSED -> (d.opening != null && d.opening == 0)
                else -> true
            }
            okType && okState
        }

        filteredDevices.clear()
        filteredDevices.addAll(filtered)

        selectedDeviceIds.retainAll(filteredDevices.map { it.id }.toSet())
        adapter.notifyDataSetChanged()
        updateBatchButtonsState()
    }

    private fun executeBatchCommand(targetOn: Boolean) {
        if (isBatchRunning) return
        val targets = filteredDevices.filter { selectedDeviceIds.contains(it.id) }
        if (targets.isEmpty()) {
            Toast.makeText(this, "Sélectionne au moins un composant", Toast.LENGTH_SHORT).show()
            return
        }

        isBatchRunning = true
        updateBatchButtonsState()
        executeCommandAtIndex(targets, 0, targetOn, successCount = 0)
    }

    private fun Device.isType(typeKey: String): Boolean = type.lowercase().contains(typeKey)

    private fun lineForDevice(d: Device): String {
        val state = when {
            d.opening != null -> "Ouverture: ${d.opening}%"
            d.power != null -> "Puissance: ${d.power}%"
            else -> "État: -"
        }
    }
}
