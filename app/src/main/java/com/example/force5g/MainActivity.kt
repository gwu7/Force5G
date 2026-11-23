package com.example.force5g

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/* ---------- Design tokens ---------- */

internal class Palette(dark: Boolean) {
    val bg = (if (dark) 0xFF0E0E10 else 0xFFF5F5F7).toInt()
    val card = (if (dark) 0xFF1C1C1E else 0xFFFFFFFF).toInt()
    val text = (if (dark) 0xFFFFFFFF else 0xFF1D1D1F).toInt()
    val sub = (if (dark) 0xFF98989F else 0xFF6E6E73).toInt()
    val line = (if (dark) 0xFF38383A else 0xFFE5E5EA).toInt()
    val accent = (if (dark) 0xFF0A84FF else 0xFF007AFF).toInt()
    val danger = (if (dark) 0xFFFF453A else 0xFFFF3B30).toInt()
    val ok = (if (dark) 0xFF30D158 else 0xFF34C759).toInt()
    val gray = 0xFF8E8E93.toInt()
}

/* ---------- Custom drawn views (no assets, no dependencies) ---------- */

/** Minimal Wi-Fi glyph. While scanning, the arcs pulse outward in sequence. */
internal class WifiGlyph(ctx: Context, private val accent: Int, private val dim: Int) : View(ctx) {
    var muted = false
        set(v) { field = v; invalidate() }
    private var scanning = false
    private var phase = 0f
    private var anim: ValueAnimator? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val d = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setScanning(on: Boolean) {
        if (on == scanning) return
        scanning = on
        anim?.cancel()
        if (on) {
            anim = ValueAnimator.ofFloat(0f, 4f).apply {
                duration = 1600
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        } else invalidate()
    }

    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val cx = w / 2f
        val cy = height * 0.82f
        val col = if (muted) dim else accent
        p.strokeWidth = w * 0.075f
        for (i in 1..3) {
            val r = w * (0.16f * i + 0.04f)
            var a = 1f
            if (scanning) {
                val t = ((phase - (i - 1)) % 4f + 4f) % 4f
                a = 0.22f + 0.78f * (if (t < 1.6f) 1f - t / 1.6f else 0f)
            }
            p.color = col
            p.alpha = (255 * a).toInt()
            c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 225f, 90f, false, p)
        }
        d.color = col
        c.drawCircle(cx, cy, w * 0.065f, d)
    }
}

internal class SignalBars(ctx: Context, private val on: Int, private val off: Int) : View(ctx) {
    var bars = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val bw = width / 7f
        for (i in 0 until 4) {
            val h = height * (0.35f + 0.65f * i / 3f)
            p.color = if (i < bars) on else off
            val l = i * bw * 1.75f
            c.drawRoundRect(l, height - h, l + bw, height.toFloat(), bw / 2f, bw / 2f, p)
        }
    }
}

/** kind 0 = save (arrow into tray), 1 = trash, 2 = settings sliders */
internal class IconView(ctx: Context, private val kind: Int, tint: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    override fun onDraw(c: Canvas) {
        val u = width / 24f
        p.strokeWidth = 2f * u
        p.style = Paint.Style.STROKE
        when (kind) {
            0 -> {
                c.drawLine(12f * u, 4f * u, 12f * u, 14f * u, p)
                c.drawPath(Path().apply {
                    moveTo(7.5f * u, 10f * u); lineTo(12f * u, 14.5f * u); lineTo(16.5f * u, 10f * u)
                }, p)
                c.drawPath(Path().apply {
                    moveTo(5f * u, 17f * u); lineTo(5f * u, 20f * u); lineTo(19f * u, 20f * u); lineTo(19f * u, 17f * u)
                }, p)
            }
            1 -> {
                c.drawLine(4.5f * u, 7f * u, 19.5f * u, 7f * u, p)
                c.drawPath(Path().apply {
                    moveTo(9f * u, 7f * u); lineTo(9f * u, 4.5f * u); lineTo(15f * u, 4.5f * u); lineTo(15f * u, 7f * u)
                }, p)
                c.drawPath(Path().apply {
                    moveTo(6.5f * u, 7f * u); lineTo(7.5f * u, 19.5f * u); lineTo(16.5f * u, 19.5f * u); lineTo(17.5f * u, 7f * u)
                }, p)
            }
            else -> {
                c.drawLine(4f * u, 8f * u, 20f * u, 8f * u, p)
                c.drawLine(4f * u, 16f * u, 20f * u, 16f * u, p)
                p.style = Paint.Style.FILL
                c.drawCircle(15f * u, 8f * u, 2.8f * u, p)
                c.drawCircle(9f * u, 16f * u, 2.8f * u, p)
            }
        }
    }
}

/* ---------- Screen ---------- */

class MainActivity : Activity() {
    private lateinit var wm: WifiManager
    private lateinit var cm: ConnectivityManager
    private lateinit var pal: Palette
    private var dark = false
    private val handler = Handler(Looper.getMainLooper())

    private var results = listOf<ScanResult>()
    private var selected: ScanResult? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var scanning = false
    private var hasScanned = false

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var glyph: WifiGlyph
    private lateinit var emptyBox: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var scanBtn: TextView
    private lateinit var connectBtn: TextView
    private lateinit var saveRow: View
    private lateinit var pass: EditText
    private lateinit var passBox: LinearLayout

    private class Ref(val r: ScanResult, val view: View, val check: TextView)
    private val refs = mutableListOf<Ref>()

    private val timeout = Runnable { if (scanning) showResults() }
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = showResults()
    }

    @Suppress("DEPRECATION")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        wm = applicationContext.getSystemService(WifiManager::class.java)
        cm = getSystemService(ConnectivityManager::class.java)
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        pal = Palette(dark)
        window.setBackgroundDrawable(ColorDrawable(pal.bg))
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        if (!dark) window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContentView(buildUi())
        registerReceiver(scanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        setStatus("Ready. Scan to find your 5GHz network.")
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) showResults(true)
        else { render(); styleBtns() }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterReceiver(scanReceiver)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(rc, perms, res)
        if (rc == 1 && res.firstOrNull() == PackageManager.PERMISSION_GRANTED) scan()
        else if (rc == 1) setStatus("Location permission is required to scan for Wi-Fi.", 3)
    }

    /* ---------- UI construction ---------- */

    private fun buildUi(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            isFocusableInTouchMode = true
            setPadding(dp(20), dp(28), dp(20), dp(40))
        }
        col.addView(label("Force 5GHz", 34f, pal.text, Typeface.BOLD))
        col.addView(label("Pin your phone to the faster band.", 16f, pal.sub), lp(top = 2))

        statusDot = View(this)
        statusText = label("", 13.5f, pal.sub)
        col.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(statusDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(8) })
            addView(statusText)
        }, lp(top = 14, bottom = 18))

        // Scanner card: empty / scanning state or network list
        glyph = WifiGlyph(this, pal.accent, pal.sub)
        emptyTitle = label("", 19f, pal.text, family = "sans-serif-medium").apply { gravity = Gravity.CENTER }
        emptyBody = label("", 14.5f, pal.sub).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.1f) }
        emptyBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(36), dp(28), dp(34))
            addView(glyph, LinearLayout.LayoutParams(dp(96), dp(96)))
            addView(emptyTitle, lp(top = 14))
            addView(emptyBody, lp(top = 6))
        }
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(pal.card, 24f)
            if (!dark) elevation = dpf(2f)
            addView(emptyBox)
            addView(listBox)
        })

        scanBtn = pill("Scan for 5GHz Networks") { scan() }
        col.addView(scanBtn, lp(h = dp(54), top = 14))

        // Password
        col.addView(section("Wi-Fi password"))
        pass = EditText(this).apply {
            hint = "Leave blank for an open network"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(pal.text)
            setHintTextColor(pal.sub)
            textSize = 16f
            background = null
            setPadding(0, 0, 0, 0)
            typeface = Typeface.DEFAULT
        }
        val toggle = label("Show", 15f, pal.accent, family = "sans-serif-medium").apply {
            setPadding(dp(12), dp(8), 0, dp(8))
            setOnClickListener {
                val show = text.toString() == "Show"
                val at = pass.selectionEnd
                pass.inputType = InputType.TYPE_CLASS_TEXT or
                    (if (show) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
                pass.typeface = Typeface.DEFAULT
                pass.setSelection(at.coerceAtLeast(0))
                text = if (show) "Hide" else "Show"
            }
        }
        passBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = shape(pal.card, 16f, dp(1), pal.line)
            setPadding(dp(16), 0, dp(10), 0)
            addView(pass, LinearLayout.LayoutParams(0, dp(54), 1f))
            addView(toggle)
        }
        pass.setOnFocusChangeListener { _, f ->
            passBox.background = shape(pal.card, 16f, dp(if (f) 2 else 1), if (f) pal.accent else pal.line)
        }
        col.addView(passBox)

        // Connection
        col.addView(section("Connection"))
        connectBtn = pill("Connect Now") { connectNow() }
        col.addView(connectBtn, lp(h = dp(54)))
        col.addView(
            label("Applies to this app only. Other apps won't use this connection. Use the system-wide option below for everything on your phone.", 13f, pal.sub)
                .apply { setPadding(dp(8), dp(10), dp(8), 0) }
        )

        // System preferences
        col.addView(section("System preferences"))
        saveRow = row(0, pal.accent, "Save as System-Wide Suggestion",
            "Android reconnects to this exact access point automatically, for every app.", pal.text, false) { suggest() }
        val removeRow = row(1, pal.danger, "Remove My Suggestions",
            "Stop this app from steering your Wi-Fi.", pal.danger, false) {
            wm.removeNetworkSuggestions(wm.networkSuggestions)
            setStatus("Suggestions removed.", 2)
        }
        col.addView(group(saveRow, removeRow))

        // Advanced
        col.addView(section("Advanced"))
        col.addView(group(row(2, pal.gray, "Open Wi-Fi Settings",
            "Approve suggestions or manage saved networks.", pal.text, true) {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }))

        return ScrollView(this).apply {
            setBackgroundColor(pal.bg)
            isFillViewport = true
            clipToPadding = false
            addView(col)
        }
    }

    /* ---------- UI helpers ---------- */

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = v * resources.displayMetrics.density
    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    private fun lp(w: Int = -1, h: Int = -2, top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun shape(color: Int, radius: Float, strokeW: Int = 0, strokeC: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dpf(radius)
        if (strokeW > 0) setStroke(strokeW, strokeC)
    }

    private fun label(t: String, size: Float, color: Int, style: Int = Typeface.NORMAL, family: String = "sans-serif") =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(family, style)
        }

    private fun section(t: String) = label(t.uppercase(), 12.5f, pal.sub, family = "sans-serif-medium").apply {
        letterSpacing = 0.06f
        setPadding(dp(8), dp(24), 0, dp(8))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun View.press(scale: Float = 0.97f) {
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(scale).scaleY(scale).setDuration(110).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(190).start()
            }
            false
        }
    }

    private fun pill(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t
        gravity = Gravity.CENTER
        textSize = 17f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        press()
        setOnClickListener { onClick() }
    }

    private fun styleBtn(v: TextView, filled: Boolean) {
        v.background = shape(if (filled) pal.accent else withAlpha(pal.accent, 0x26), 27f)
        v.setTextColor(if (filled) Color.WHITE else pal.accent)
    }

    private fun styleBtns() {
        val has = selected != null
        styleBtn(scanBtn, !has)
        styleBtn(connectBtn, has)
        scanBtn.alpha = if (scanning) 0.7f else 1f
        connectBtn.alpha = if (has) 1f else 0.45f
        saveRow.alpha = if (has) 1f else 0.45f
    }

    private fun group(vararg rows: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(pal.card, 20f)
        if (!dark) elevation = dpf(2f)
        rows.forEachIndexed { i, v ->
            if (i > 0) addView(View(this@MainActivity).apply { setBackgroundColor(pal.line) },
                LinearLayout.LayoutParams(-1, 1).apply { marginStart = dp(64) })
            addView(v)
        }
    }

    private fun row(kind: Int, tile: Int, title: String, sub: String, titleColor: Int, chevron: Boolean, onClick: () -> Unit): View {
        val icon = FrameLayout(this).apply {
            background = shape(tile, 9f)
            addView(IconView(this@MainActivity, kind, Color.WHITE), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 16f, titleColor, family = "sans-serif-medium"))
            addView(label(sub, 13f, pal.sub), lp(top = 2))
        }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(icon, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(14) })
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            if (chevron) addView(label("\u203A", 24f, pal.sub))
            press(0.985f)
            setOnClickListener { onClick() }
        }
    }

    /* ---------- State rendering ---------- */

    private fun setStatus(t: String, kind: Int = 0) {
        val c = when (kind) { 1 -> pal.accent; 2 -> pal.ok; 3 -> pal.danger; else -> pal.sub }
        statusDot.background = shape(c, 4f)
        statusText.text = t
        statusText.alpha = 0f
        statusText.animate().alpha(1f).setDuration(220).start()
    }

    private fun render() {
        glyph.setScanning(scanning)
        glyph.muted = hasScanned && results.isEmpty() && !scanning
        if (results.isNotEmpty()) {
            emptyBox.visibility = View.GONE
            listBox.visibility = View.VISIBLE
            listBox.alpha = if (scanning) 0.5f else 1f
        } else {
            if (emptyBox.visibility != View.VISIBLE) {
                emptyBox.alpha = 0f
                emptyBox.animate().alpha(1f).setDuration(260).start()
            }
            emptyBox.visibility = View.VISIBLE
            listBox.visibility = View.GONE
            emptyTitle.text = when {
                scanning -> "Searching\u2026"
                hasScanned -> "No 5GHz networks found"
                else -> "Find your 5GHz network"
            }
            emptyBody.text = when {
                scanning -> "Listening for access points nearby."
                hasScanned -> "Move closer to your router, make sure its 5GHz network is on, and that Location is enabled."
                else -> "Scan to see nearby networks broadcasting on the 5GHz band."
            }
        }
        scanBtn.text = if (scanning) "Scanning\u2026" else "Scan for 5GHz Networks"
        scanBtn.isEnabled = !scanning
        styleBtns()
    }

    private fun addNetwork(r: ScanResult, i: Int) {
        val six = r.frequency >= 5925
        val ch = if (six) (r.frequency - 5950) / 5 else (r.frequency - 5000) / 5
        val bars = SignalBars(this, pal.accent, pal.line).apply {
            this.bars = when { r.level >= -55 -> 4; r.level >= -67 -> 3; r.level >= -78 -> 2; else -> 1 }
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(r.SSID, 16f, pal.text, family = "sans-serif-medium"))
            addView(label("${if (six) "6" else "5"} GHz \u00B7 Ch $ch \u00B7 ${r.level} dBm", 13f, pal.sub), lp(top = 1))
            addView(label(r.BSSID.uppercase(), 11.5f, pal.sub, family = "monospace").apply { alpha = 0.75f }, lp(top = 1))
        }
        val check = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.WHITE)
        }
        val v = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(bars, LinearLayout.LayoutParams(dp(24), dp(18)).apply { marginEnd = dp(14) })
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            addView(check, LinearLayout.LayoutParams(dp(24), dp(24)))
            press(0.98f)
            setOnClickListener {
                selected = r
                refreshSelection()
                setStatus("Selected ${r.SSID}.")
            }
        }
        val ref = Ref(r, v, check)
        refs.add(ref)
        styleRow(ref)
        listBox.addView(v, lp(top = if (i == 0) 0 else 4))
        v.alpha = 0f
        v.translationY = dpf(10f)
        v.animate().alpha(1f).translationY(0f).setStartDelay(i * 45L).setDuration(320)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun styleRow(ref: Ref) {
        val sel = selected?.BSSID == ref.r.BSSID
        ref.view.background = if (sel) shape(withAlpha(pal.accent, 0x1F), 16f, dp(1), pal.accent)
        else shape(Color.TRANSPARENT, 16f)
        ref.check.text = if (sel) "\u2713" else ""
        ref.check.background = if (sel) shape(pal.accent, 12f) else shape(Color.TRANSPARENT, 12f, dp(2), pal.line)
    }

    private fun refreshSelection() {
        refs.forEach { styleRow(it) }
        styleBtns()
    }

    /* ---------- Wi-Fi logic (unchanged behaviour) ---------- */

    private fun scan() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            setStatus("Allow Location access so Android can scan for Wi-Fi.", 1)
            return
        }
        if (!wm.isWifiEnabled) { setStatus("Turn Wi-Fi on first.", 3); return }
        scanning = true
        render()
        setStatus("Scanning\u2026", 1)
        @Suppress("DEPRECATION")
        val ok = wm.startScan()
        if (ok) handler.postDelayed(timeout, 12000)
        else {
            showResults(true)
            setStatus("Android limits scans to about 4 every 2 minutes. Showing the latest results.", 3)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showResults(quiet: Boolean = false) {
        handler.removeCallbacks(timeout)
        scanning = false
        if (!quiet) hasScanned = true
        val found = try { wm.scanResults } catch (e: SecurityException) { emptyList<ScanResult>() }
        results = found.filter { it.frequency >= 5000 && it.SSID.isNotEmpty() }.sortedByDescending { it.level }
        selected = results.firstOrNull { it.BSSID == selected?.BSSID }
        listBox.removeAllViews()
        refs.clear()
        results.forEachIndexed { i, r -> addNetwork(r, i) }
        render()
        if (!quiet) {
            val n = results.size
            if (n == 0) setStatus("No 5GHz networks in range.", 3)
            else setStatus("$n 5GHz network${if (n == 1) "" else "s"} found. Tap one.", 2)
        }
    }

    private fun needSelection(): ScanResult? =
        selected.also { if (it == null) setStatus("Select a network first.", 3) }

    private fun connectNow() {
        val r = needSelection() ?: return
        val sb = WifiNetworkSpecifier.Builder().setSsid(r.SSID).setBssid(MacAddress.fromString(r.BSSID))
        if (pass.text.isNotEmpty()) sb.setWpa2Passphrase(pass.text.toString())
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(sb.build()).build()
        netCallback?.let { cm.unregisterNetworkCallback(it) }
        setStatus("Connecting to ${r.SSID}\u2026", 1)
        netCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) {
                cm.bindProcessToNetwork(n)
                runOnUiThread { setStatus("Connected to 5GHz (this app's traffic only).", 2) }
            }
            override fun onUnavailable() = runOnUiThread { setStatus("Connection failed or was cancelled.", 3) }
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
        if (code == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS)
            setStatus("Suggestion saved. Approve Android's notification, then turn off auto-connect on your saved 2.4GHz network.", 2)
        else setStatus("Failed (code $code). Check Settings > Apps > Force 5GHz and allow Wi-Fi control.", 3)
    }
}
