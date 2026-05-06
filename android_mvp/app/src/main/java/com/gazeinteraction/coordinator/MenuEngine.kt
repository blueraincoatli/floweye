package com.gazeinteraction.coordinator

import org.json.JSONArray
import org.json.JSONObject

class MenuEngine(menuJson: String) {
    private val root: JSONObject = JSONObject(menuJson)
    private val path = mutableListOf<Int>()
    private val selections = mutableListOf<String>()

    fun getCurrentOptions(): List<JSONObject> {
        var current = root
        for (i in 0 until path.size - 1) {
            val cats = current.optJSONArray("categories") ?: return emptyList()
            current = cats.getJSONObject(path[i])
        }
        if (path.isEmpty()) {
            val cats = current.optJSONArray("categories") ?: return emptyList()
            return (0 until cats.length()).map { cats.getJSONObject(it) }
        }
        val lastIdx = path.last()
        val cat = current.optJSONArray("categories")?.getJSONObject(lastIdx) ?: return emptyList()
        val opts = cat.optJSONArray("options") ?: return emptyList()
        return (0 until opts.length()).map { opts.getJSONObject(it) }
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
        val subOptions = selected.optJSONArray("options")
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
