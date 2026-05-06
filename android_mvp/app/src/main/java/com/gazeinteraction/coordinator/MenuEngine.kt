package com.gazeinteraction.coordinator

import org.json.JSONArray
import org.json.JSONObject

class MenuEngine(menuJson: String) {
    private val root: JSONObject = JSONObject(menuJson)
    private val path = mutableListOf<Int>()
    private val selections = mutableListOf<String>()

    companion object {
        private const val KEY_ROOT = "options"
        private const val KEY_SUBMENU = "submenu"
    }

    fun getCurrentOptions(): List<JSONObject> {
        var current = root
        for (i in 0 until path.size - 1) {
            val items = current.optJSONArray(KEY_ROOT) ?: return emptyList()
            current = items.getJSONObject(path[i])
        }
        if (path.isEmpty()) {
            val items = current.optJSONArray(KEY_ROOT) ?: return emptyList()
            return (0 until items.length()).map { items.getJSONObject(it) }
        }
        val lastIdx = path.last()
        val parent = current.optJSONArray(KEY_ROOT)?.getJSONObject(lastIdx) ?: return emptyList()
        val subs = parent.optJSONArray(KEY_SUBMENU) ?: return emptyList()
        return (0 until subs.length()).map { subs.getJSONObject(it) }
    }

    fun selectCurrent(): JSONObject? {
        val options = getCurrentOptions()
        val idx = _currentIndex
        if (idx < 0 || idx >= options.size) return null
        val selected = options[idx]
        val action = selected.optString("action", "")
        if (action == "back") {
            goBack()
            return null
        }
        val subOptions = selected.optJSONArray(KEY_SUBMENU)
        if (subOptions != null && subOptions.length() > 0) {
            path.add(idx)
            _currentIndex = 0
            return null
        }
        selections.add(selected.optString("id", ""))
        return selected
    }

    fun goBack() {
        if (path.isNotEmpty()) {
            path.removeAt(path.size - 1)
            _currentIndex = 0
        }
    }

    fun reset() {
        path.clear()
        _currentIndex = 0
        selections.clear()
    }

    fun getSelectionHistory(): List<String> = selections.toList()

    var _currentIndex = 0
    fun setCurrentIndex(i: Int) { _currentIndex = i.coerceIn(0, (getCurrentOptions().size - 1).coerceAtLeast(0)) }

    fun nextOption(): JSONObject? {
        val options = getCurrentOptions()
        if (options.isEmpty()) return null
        _currentIndex = (_currentIndex + 1) % options.size
        return options[_currentIndex]
    }

    fun getCurrentOption(): JSONObject? {
        val options = getCurrentOptions()
        return if (_currentIndex in options.indices) options[_currentIndex] else options.firstOrNull()
    }

    val currentDepth: Int get() = path.size
}
