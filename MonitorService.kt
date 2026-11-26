package com.kosigan.fiveguard

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Foreground service that watches the radio generation. When it falls from 5G to 4G (or lower)
 * and stays there for the configured delay, it raises a loud alert and optionally cuts the internet.
 */
class MonitorService : Service() {
    private lateinit var tm: TelephonyManager
    private val handler = Handler(Looper.getMainLooper())

    private var current = Gen.NONE
    private var was5G = false
    private var pending: Runnable? = null
    private var registered = false
    private var modernCallback: Any? = null
    private var legacyListener: PhoneStateListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tm = getSystemService(TelephonyManager::class.java)
        Notifier.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifier.monitor(this, "Waiting for network info…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.ID_MONITOR, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.ID_MONITOR, n)
        }

        if (intent?.action == ACTION_STOP) {
            Prefs.setMonitoring(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!registered) register()
        AppState.running.value = true
        Prefs.setMonitoring(this, true)
        return START_STICKY
    }

    // ---- radio listening -------------------------------------------------------------------

    @Suppress("DEPRECATION", "MissingPermission")
    private fun register() {
        registered = true
        if (Build.VERSION.SDK_INT >= 31) {
            val cb = ModernCallback()
            modernCallback = cb
            tm.registerTelephonyCallback(ContextCompat.getMainExecutor(this), cb)
        } else {
            // Pre-Android 12 can't tell 5G NSA from 4G; only standalone 5G / plain 4G are reported.
            val l = object : PhoneStateListener() {
                override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                    onGen(Gen.from(networkType, 0))
                }
            }
            legacyListener = l
            tm.listen(l, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE)
        }
    }

    @RequiresApi(31)
    private inner class ModernCallback : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
            onGen(Gen.from(info.networkType, info.overrideNetworkType))
        }
    }

    @Suppress("DEPRECATION")
    private fun unregister() {
        if (!registered) return
        registered = false
        if (Build.VERSION.SDK_INT >= 31) {
            (modernCallback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
        } else {
            legacyListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
    }

    // ---- decision logic --------------------------------------------------------------------

    private fun onGen(g: Gen) {
        if (g == Gen.NONE || g == current) return
        current = g
        AppState.gen.value = g
        Notifier.updateMonitor(this, "Network: ${g.label}")
        AppState.log(this, "Network is now ${g.label}")

        if (g == Gen.G5) {
            cancelPending()
            was5G = true
            if (AppState.blocked.value && Prefs.autoRestore(this)) {
                startService(Intent(this, BlockVpnService::class.java).setAction(BlockVpnService.ACTION_STOP))
                Notifier.alert(this, "Back on 5G", "Internet turned back on.", offerRestore = false, loud = false)
            }
        } else if (was5G && pending == null) {
            val r = Runnable {
                pending = null
                was5G = false
                onDowngrade(current)
            }
            pending = r
            handler.postDelayed(r, Prefs.delaySec(this) * 1000L)
        }
    }

    private fun cancelPending() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    private fun onDowngrade(g: Gen) {
        AppState.log(this, "5G dropped to ${g.label}")
        var text = "Your network fell from 5G to ${g.label}."
        var offerRestore = false

        if (Prefs.autoCut(this)) {
            when {
                !onMobileData() -> text += " You're on Wi-Fi, so the internet was left on."
                VpnService.prepare(this) != null ->
                    text += " Open the app and allow the VPN permission so I can cut the internet."
                else -> {
                    startService(Intent(this, BlockVpnService::class.java))
                    text += " Internet turned off."
                    offerRestore = true
                }
            }
        }
        Notifier.alert(this, "5G → ${g.label}", text, offerRestore)
    }

    private fun onMobileData(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    override fun onDestroy() {
        cancelPending()
        unregister()
        AppState.running.value = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.kosigan.fiveguard.STOP_MONITOR"
    }
}
