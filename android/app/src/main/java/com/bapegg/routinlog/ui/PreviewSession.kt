package com.bapegg.routinlog.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.bapegg.routinlog.data.ProfileDto

/** Screen navigation and unsent form drafts. Authentication lives in AccountViewModel. */
class PreviewSession : ViewModel() {
    var route by mutableStateOf("A01")
        private set
    private val history = mutableListOf<String>()
    val values = mutableStateMapOf<String, String>()
    var message by mutableStateOf<String?>(null)
    var previewMode by mutableStateOf(false)
    var accountMode by mutableStateOf(false)
    var loadedProfile: ProfileDto? = null
    fun today(): java.time.LocalDate = java.time.LocalDate.now(
        loadedProfile?.timeZone?.let(java.time.ZoneId::of) ?: java.time.ZoneId.systemDefault())
    fun get(key: String, fallback: String = "") = values[key] ?: fallback
    fun set(key: String, value: String) { values[key] = value }
    fun flag(key: String, fallback: Boolean = false) = values[key]?.toBooleanStrictOrNull() ?: fallback
    fun toggle(key: String, fallback: Boolean = false) = set(key, (!flag(key, fallback)).toString())
    fun go(id: String) {
        if (id == route) return
        if (id in listOf("H01", "F01", "W01", "R01")) history.clear() else history.add(route)
        route = id
    }
    fun back() {
        route = if (history.isNotEmpty()) history.removeAt(history.lastIndex)
        else if (route in listOf("H01", "F01", "W01", "R01")) "A01"
        else ScreenCatalog[route]?.back ?: "H01"
    }
    fun notify(text: String = "체험 화면에 반영했어요. 실제 기록은 저장되지 않아요.") { message = text }
    fun save(to: String) { notify(); go(to) }
    fun startPreview() { reset(); previewMode = true; go("H01") }
    fun reset() { values.clear(); history.clear(); previewMode = false; accountMode = false; loadedProfile=null; message = null; route = "A01" }
}
