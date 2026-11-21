package com.example.force5g

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.net.*
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var wm: WifiManager
    private lateinit var cm: ConnectivityManager
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var pass: EditText
    private var results = listOf<ScanResult>()
    private var selected: ScanResult? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        wm = applicationContext.getSystemService(WifiManager::class.java)
        cm = getSystemService(ConnectivityManager::class.java)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        status = TextView(this).apply { text = "1) Scan  2) Tap a 5GHz entry  3) Enter password  4) Connect" }
        pass = EditText(this).apply { hint = "Wi-Fi password (blank if open)"; inputType = 129 }
        list = ListView(this)
        fun btn(t: String, a: () -> Unit) = Button(this).apply { text = t; setOnClickListener { a() } }

        root.addView(status)
        root.addView(btn("Scan for 5GHz networks") { scan() })
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(pass)
        root.addView(btn("Connect now (this app only)") { connectNow() })
        root.addView(btn("Save as system-wide suggestion") { suggest() })
        root.addView(btn("Remove my suggestions") {
            wm.removeNetworkSuggestions(wm.networkSuggestions)
            status.text = "Suggestions removed."
        })
        root.addView(btn("Open Wi-Fi settings") { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) })
        setContentView(root)

        list.setOnItemClickListener { _, _, i, _ ->
            selected = results[i]
            status.text = "Selected ${results[i].SSID} (${results[i].BSSID})"
        }
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = showResults()
        }, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    private fun scan() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1); return
        }
        if (!wm.isWifiEnabled) { status.text = "Turn Wi-Fi on first."; return }
        @Suppress("DEPRECATION")
        val ok = wm.startScan()
        status.text = if (ok) "Scanning..." else "Scan throttled by Android (max ~4 per 2 min). Showing last results."
        if (!ok) showResults()
    }

    @SuppressLint("MissingPermission")
    private fun showResults() {
        results = wm.scanResults.filter { it.frequency >= 5000 && it.SSID.isNotEmpty() }
            .sortedByDescending { it.level }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            results.map { "${it.SSID}  ${it.frequency}MHz  ${it.level}dBm\n${it.BSSID}" })
        status.text = if (results.isEmpty()) "No 5GHz networks found." else "${results.size} 5GHz networks. Tap one."
    }

    private fun needSelection(): ScanResult? =
        selected.also { if (it == null) status.text = "Select a network first." }

    private fun connectNow() {
        val r = needSelection() ?: return
        val sb = WifiNetworkSpecifier.Builder().setSsid(r.SSID).setBssid(MacAddress.fromString(r.BSSID))
        if (pass.text.isNotEmpty()) sb.setWpa2Passphrase(pass.text.toString())
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(sb.build()).build()
        netCallback?.let { cm.unregisterNetworkCallback(it) }
        netCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) {
                cm.bindProcessToNetwork(n)
                runOnUiThread { status.text = "Connected to 5GHz ${r.BSSID} (this app's traffic)." }
            }
            override fun onUnavailable() = runOnUiThread { status.text = "Connection failed or cancelled." }
        }
        cm.requestNetwork(req, netCallback!!)
    }

    private fun suggest() {
        val r = needSelection() ?: return
        val sb = WifiNetworkSuggestion.Builder().setSsid(r.SSID)
            .setBssid(MacAddress.fromString(r.BSSID)).setIsAppInteractionRequired(false)
        if (pass.text.isNotEmpty()) sb.setWpa2Passphrase(pass.text.toString())
        wm.removeNetworkSuggestions(wm.networkSuggestions)
        val code = wm.addNetworkSuggestions(listOf(sb.build()))
        status.text = if (code == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS)
            "Suggestion saved. Approve the notification, and forget the saved 2.4GHz network in Wi-Fi settings."
        else "Failed, code $code (check Settings > Apps > Force 5GHz > allow Wi-Fi control)."
    }
}
