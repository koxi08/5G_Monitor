package com.kosigan.fiveguard

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * "Turns off the internet" without root: creates a local VPN that captures all traffic
 * (IPv4 + IPv6) and never forwards it anywhere, so every app is offline until it's stopped.
 */
class BlockVpnService : VpnService() {
    private var pfd: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBlock()
            stopSelf()
        } else {
            startBlock()
        }
        return START_NOT_STICKY
    }

    private fun startBlock() {
        if (pfd != null) return
        pfd = try {
            Builder()
                .setSession("5G Guard - internet off")
                .addAddress("10.255.255.1", 32)
                .addAddress("fd00:56::1", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .establish()
        } catch (e: Exception) {
            null
        }
        AppState.blocked.value = pfd != null
        if (pfd != null) AppState.log(this, "Internet turned OFF")
    }

    private fun stopBlock() {
        if (pfd != null) AppState.log(this, "Internet turned back ON")
        try { pfd?.close() } catch (_: Exception) {}
        pfd = null
        AppState.blocked.value = false
    }

    override fun onRevoke() {
        stopBlock()
        stopSelf()
    }

    override fun onDestroy() {
        stopBlock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.kosigan.fiveguard.STOP_BLOCK"
    }
}
