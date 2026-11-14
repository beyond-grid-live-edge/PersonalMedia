package com.personalmedia.android

import java.util.ArrayDeque

/** In-memory navigation history shared by top-level tabs and Stremio sections.
 * Previous screens are visited in reverse order. Never implicitly finish the Activity.
 */
internal class PersonalMediaNavHistory(private val root: String, private val limit: Int = 32) {
    private val history = ArrayDeque<String>()

    fun visit(current: String, destination: String) {
        if (current == destination) return
        if (history.peekLast() != current) history.addLast(current)
        while (history.size > limit) history.removeFirst()
    }

    fun previous(current: String): String? {
        while (history.isNotEmpty()) {
            val candidate = history.removeLast()
            if (candidate != current) return candidate
        }
        return if (current != root) root else null
    }
}
