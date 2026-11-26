package com.kosigan.fiveguard

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-process state shared by the services and the UI. */
object AppState {
    val gen = MutableStateFlow(Gen.NONE)
    val running = MutableStateFlow(false)
    val blocked = MutableStateFlow(false)
    val events = MutableStateFlow<List<String>>(emptyList())

    fun loadEvents(c: Context) {
        events.value = Prefs.events(c)
    }

    fun log(c: Context, msg: String) {
        val t = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date())
        val list = (listOf("$t  $msg") + Prefs.events(c)).take(40)
        Prefs.saveEvents(c, list)
        events.value = list
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("fiveg_guard", Context.MODE_PRIVATE)

    fun autoCut(c: Context) = sp(c).getBoolean("autoCut", true)
    fun setAutoCut(c: Context, v: Boolean) = sp(c).edit().putBoolean("autoCut", v).apply()

    fun autoRestore(c: Context) = sp(c).getBoolean("autoRestore", true)
    fun setAutoRestore(c: Context, v: Boolean) = sp(c).edit().putBoolean("autoRestore", v).apply()

    fun delaySec(c: Context) = sp(c).getInt("delaySec", 5)
    fun setDelaySec(c: Context, v: Int) = sp(c).edit().putInt("delaySec", v).apply()

    fun monitoring(c: Context) = sp(c).getBoolean("monitoring", false)
    fun setMonitoring(c: Context, v: Boolean) = sp(c).edit().putBoolean("monitoring", v).apply()

    fun events(c: Context): List<String> =
        (sp(c).getString("events", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun saveEvents(c: Context, list: List<String>) =
        sp(c).edit().putString("events", list.joinToString("\n")).apply()
}
