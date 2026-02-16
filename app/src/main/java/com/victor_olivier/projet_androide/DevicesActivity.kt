package com.victor_olivier.projet_androide

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
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
        private const val FILTER_ON = "Allumés / Ouverts"
        private const val FILTER_OFF = "Éteints / Fermés"

        private const val TYPE_LIGHT = "light"
        private const val TYPE_SHUTTER = "shutter"
        private const val TYPE_DOOR = "door"
        private const val TYPE_GARAGE = "garage"

        private val DEVICE_STATES = listOf(FILTER_ALL, FILTER_ON, FILTER_OFF)
        private val COMMAND_ON_CANDIDATES = listOf("on", "open", "up", "turn on", "turn_on")
        private val COMMAND_OFF_CANDIDATES = listOf("off", "close", "down", "turn off", "turn_off")
    }

    private data class CommandPayload(val command: String)

    private data class CommandAttempt(
        val url: String,
        val method: String,
        val payload: CommandPayload? = null
    )

    private var houseId: Int = -1
    private var token: String? = null
    private lateinit var houseUrl: String

    private lateinit var webHouse: WebView
    private lateinit var spinnerType: Spinner
    private lateinit var spinnerState: Spinner
    private lateinit var containerDevices: LinearLayout

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
    private lateinit var btnClearSelection: MaterialButton

    private val allDevices = arrayListOf<Device>()
    private val filteredDevices = arrayListOf<Device>()
    private val selectedDeviceIds = linkedSetOf<String>()
    private val pendingDeviceIds = linkedSetOf<String>()

    private var selectedType: String = FILTER_ALL
    private var selectedState: String = FILTER_ALL

    private var isLoadingDevices = false
    private var isBatchRunning = false
    private var devicesLoadedAtLeastOnce = false

    private var pendingBrowserInitRetry = false
    private var alreadyOpenedCustomTabForInit = false

    private var customTabsSession: CustomTabsSession? = null
    private var serviceConnection: CustomTabsServiceConnection? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_devices)

        houseId = intent.getIntExtra("houseId", -1)
        val tokenStore = TokenStore(this)
        token = tokenStore.getToken()

        if (houseId == -1 || token.isNullOrBlank()) {
            Toast.makeText(this, "houseId/token manquant", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        houseUrl = ApiRoutes.HOUSE_BROWSER(houseId)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarDevices)
        toolbar.title = "Maison #$houseId"
        toolbar.menu.findItem(R.id.action_username)?.title = tokenStore.getUsername()?.ifBlank { "Utilisateur" } ?: "Utilisateur"
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
        containerDevices = findViewById(R.id.containerDevices)

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
        btnClearSelection = findViewById(R.id.btnClearSelection)

        // Demande utilisateur : onglet composants fermé au démarrage
        panelComponents.visibility = View.GONE
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

        btnSelectAll.setOnClickListener {
            selectedDeviceIds.clear()
            selectedDeviceIds.addAll(filteredDevices.map { it.id })
            renderDeviceRows()
            updateBatchButtonsState()
        }

        btnClearSelection.setOnClickListener {
            selectedDeviceIds.clear()
            renderDeviceRows()
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
        TokenStore(this).clearToken()
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
        spinnerType.adapter = createSpinnerAdapter(types)
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
        spinnerState.adapter = createSpinnerAdapter(DEVICE_STATES)
        spinnerState.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedState = DEVICE_STATES[position]
                applyFiltersAndRender()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun createSpinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, R.layout.item_spinner_selected, items).also {
            it.setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
    }

    private fun loadDevices() {
        if (isLoadingDevices) return
        val t = token ?: return
        isLoadingDevices = true

        Api().get<DevicesResponse>(
            ApiRoutes.DEVICES(houseId),
            onSuccess = { code, body ->
                isLoadingDevices = false
                if (code == 200 && body != null) {
                    devicesLoadedAtLeastOnce = true
                    alreadyOpenedCustomTabForInit = false
                    allDevices.clear()
                    allDevices.addAll(body.devices)
                    pendingDeviceIds.clear()

                    val types = mutableListOf(FILTER_ALL)
                    types.addAll(allDevices.map { it.type }.distinct().sorted())
                    setupTypeSpinner(types)

                    updateInfoPanel()
                    applyFiltersAndRender()
                } else {
                    if (code == 500 && !devicesLoadedAtLeastOnce) {
                        Toast.makeText(this, "Initialisation maison…", Toast.LENGTH_SHORT).show()
                        openHouseInCustomTabForInit(houseUrl)
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
        val doorsOpen = allDevices.count { it.isType(TYPE_DOOR) && !it.isType(TYPE_GARAGE) && (it.opening ?: 0) > 0 }
        val garageOpen = allDevices.count { it.isType(TYPE_GARAGE) && (it.opening ?: 0) > 0 }

        tvLightsOn.text = "Lumières allumées : $lightsOn"
        tvShuttersOpen.text = "Volets ouverts : $shuttersOpen"
        tvDoorsOpen.text = "Portes ouvertes : $doorsOpen"
        tvGarageOpen.text = "Garage ouvert : $garageOpen"
    }

    private fun applyFiltersAndRender() {
        val filtered = allDevices.filter { d ->
            val okType = selectedType == FILTER_ALL || d.type == selectedType
            val isOn = (d.power ?: 0) > 0 || (d.opening ?: 0) > 0
            val okState = when (selectedState) {
                FILTER_ON -> isOn
                FILTER_OFF -> !isOn
                else -> true
            }
            okType && okState
        }

        filteredDevices.clear()
        filteredDevices.addAll(filtered)
        selectedDeviceIds.retainAll(filteredDevices.map { it.id }.toSet())
        renderDeviceRows()
        updateBatchButtonsState()
    }

    private fun renderDeviceRows() {
        containerDevices.removeAllViews()
        if (filteredDevices.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "Aucun composant pour ce filtre"
                setTextColor(getColor(R.color.app_text_secondary))
                textSize = 14f
                setPadding(8, 12, 8, 12)
            }
            containerDevices.addView(emptyView)
            return
        }

        filteredDevices.forEach { device ->
            val row = layoutInflater.inflate(R.layout.item_device, containerDevices, false)
            val cb = row.findViewById<CheckBox>(R.id.cbSelectDevice)
            val tvName = row.findViewById<TextView>(R.id.tvDeviceName)
            val tvState = row.findViewById<TextView>(R.id.tvDeviceState)
            val sw = row.findViewById<SwitchMaterial>(R.id.swDeviceState)

            val on = deviceIsOn(device)
            val hasAction = resolveCommand(device, true) != null || resolveCommand(device, false) != null
            val isPending = pendingDeviceIds.contains(device.id)

            tvName.text = "${device.type} (#${device.id})"
            tvState.text = when {
                device.opening != null -> "Ouverture: ${device.opening}%"
                device.power != null -> "Puissance: ${device.power}%"
                else -> "État: ${if (on) "1" else "0"}"
            }

            cb.setOnCheckedChangeListener(null)
            cb.isChecked = selectedDeviceIds.contains(device.id)
            cb.isEnabled = !isBatchRunning && !isPending
            cb.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedDeviceIds.add(device.id) else selectedDeviceIds.remove(device.id)
                updateBatchButtonsState()
            }

            sw.setOnCheckedChangeListener(null)
            sw.isEnabled = hasAction && !isBatchRunning && !isPending
            sw.isChecked = on
            sw.text = if (on) "1" else "0"
            sw.setOnCheckedChangeListener { _, isChecked ->
                if (!hasAction || pendingDeviceIds.contains(device.id)) return@setOnCheckedChangeListener

                pendingDeviceIds.add(device.id)
                renderDeviceRows()
                sendCommandToDevice(device, isChecked) { success ->
                    runOnUiThread {
                        pendingDeviceIds.remove(device.id)
                        if (success) {
                            applyInstantDeviceState(device.id, isChecked)
                            refreshDevicesSoon()
                        } else {
                            renderDeviceRows()
                        }
                    }
                }
            }

            containerDevices.addView(row)
        }
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
        renderDeviceRows()
        executeCommandAtIndex(targets, 0, targetOn, successCount = 0)
    }

    private fun executeCommandAtIndex(targets: List<Device>, index: Int, targetOn: Boolean, successCount: Int) {
        if (index >= targets.size) {
            isBatchRunning = false
            selectedDeviceIds.clear()
            Toast.makeText(this, "$successCount/${targets.size} commandes exécutées", Toast.LENGTH_SHORT).show()
            refreshDevicesSoon()
            renderDeviceRows()
            updateBatchButtonsState()
            return
        }

        sendCommandToDevice(targets[index], targetOn) { success ->
            if (success) applyInstantDeviceState(targets[index].id, targetOn)
            executeCommandAtIndex(targets, index + 1, targetOn, if (success) successCount + 1 else successCount)
        }
    }

    private fun sendCommandToDevice(device: Device, targetOn: Boolean, onDone: (Boolean) -> Unit) {
        val t = token
        if (t.isNullOrBlank()) {
            onDone(false)
            return
        }

        val command = resolveCommand(device, targetOn)
        if (command == null) {
            onDone(false)
            return
        }

        val encodedCommand = Uri.encode(command)
        val payload = CommandPayload(command)
        val attempts = listOf(
            CommandAttempt(ApiRoutes.DEVICE_COMMAND_PATH(houseId, device.id, encodedCommand), "PUT"),
            CommandAttempt(ApiRoutes.DEVICE_COMMANDS_PATH(houseId, device.id, encodedCommand), "PUT"),
            CommandAttempt(ApiRoutes.DEVICE_COMMAND_QUERY(houseId, device.id, encodedCommand), "PUT"),
            CommandAttempt(ApiRoutes.DEVICE_COMMAND(houseId, device.id), "PUT", payload),
            CommandAttempt(ApiRoutes.DEVICE_COMMANDS(houseId, device.id), "PUT", payload),
            CommandAttempt(ApiRoutes.DEVICE_COMMAND(houseId, device.id), "POST", payload),
            CommandAttempt(ApiRoutes.DEVICE_COMMANDS(houseId, device.id), "POST", payload),
            CommandAttempt(ApiRoutes.DEVICE(houseId, device.id), "PUT", payload)
        )

        tryCommandWithFallback(
            attempts = attempts,
            index = 0,
            tokenValue = t,
            onResult = { success, lastCode ->
                if (!success) {
                    Toast.makeText(this, "Commande ${device.id} refusée (${lastCode ?: -1})", Toast.LENGTH_SHORT).show()
                }
                onDone(success)
            }
        )
    }

    private fun tryCommandWithFallback(
        attempts: List<CommandAttempt>,
        index: Int,
        tokenValue: String,
        onResult: (Boolean, Int?) -> Unit
    ) {
        if (index >= attempts.size) {
            onResult(false, null)
            return
        }

        val attempt = attempts[index]
        if (attempt.payload != null) {
            Api().request<Unit, CommandPayload>(
                attempt.url,
                method = attempt.method,
                data = attempt.payload,
                onSuccess = { code, _ ->
                    if (code in 200..299) {
                        onResult(true, code)
                    } else if (code in listOf(400, 404, 405) && index < attempts.lastIndex) {
                        tryCommandWithFallback(attempts, index + 1, tokenValue, onResult)
                    } else {
                        onResult(false, code)
                    }
                },
                securityToken = tokenValue
            )
        } else {
            Api().request<Unit>(
                attempt.url,
                method = attempt.method,
                onSuccess = { code ->
                    if (code in 200..299) {
                        onResult(true, code)
                    } else if (code in listOf(400, 404, 405) && index < attempts.lastIndex) {
                        tryCommandWithFallback(attempts, index + 1, tokenValue, onResult)
                    } else {
                        onResult(false, code)
                    }
                },
                securityToken = tokenValue
            )
        }
    }

    private fun resolveCommand(device: Device, targetOn: Boolean): String? {
        val candidates = if (targetOn) COMMAND_ON_CANDIDATES else COMMAND_OFF_CANDIDATES
        val normalizedCommands = device.availableCommands.associateBy { normalizeCommand(it) }
        for (candidate in candidates) {
            val found = normalizedCommands[normalizeCommand(candidate)]
            if (found != null) return found
        }
        return null
    }

    private fun updateBatchButtonsState() {
        val hasSelection = selectedDeviceIds.isNotEmpty()
        btnBatchOn.isEnabled = hasSelection && !isBatchRunning
        btnBatchOff.isEnabled = hasSelection && !isBatchRunning
        btnSelectAll.isEnabled = !isBatchRunning && filteredDevices.isNotEmpty()
        btnClearSelection.isEnabled = hasSelection && !isBatchRunning
    }

    private fun refreshDevicesSoon(delayMs: Long = 350L) {
        mainHandler.postDelayed({ loadDevices() }, delayMs)
    }

    private fun applyInstantDeviceState(deviceId: String, targetOn: Boolean) {
        val index = allDevices.indexOfFirst { it.id == deviceId }
        if (index < 0) return

        val current = allDevices[index]
        val updated = when {
            current.power != null -> current.copy(power = if (targetOn) 100 else 0)
            current.opening != null -> current.copy(opening = if (targetOn) 100 else 0)
            else -> current
        }
        allDevices[index] = updated
        updateInfoPanel()
        applyFiltersAndRender()
    }

    private fun normalizeCommand(value: String): String = value.lowercase().replace("_", " ").trim()

    private fun Device.isType(typeKey: String): Boolean = type.lowercase().contains(typeKey)

    private fun deviceIsOn(device: Device): Boolean = (device.power ?: 0) > 0 || (device.opening ?: 0) > 0
}
