package com.personalmedia.android

/** Presentation-only LCN deduplication: do not delete real DVB identities from storage. */
internal data class T405GuideCandidate(
    val key: String,
    val number: Int,
    val name: String,
    val encrypted: Boolean = false,
    val active: Boolean = false,
    val currentProgramme: Boolean = false,
    val futureProgrammeCount: Int = 0,
    val mediaPidCount: Int = 0,
    val frequencyKnown: Boolean = false
)

internal object T405GuidePolicy {
    private fun slot(row: T405GuideCandidate): String = if (row.number > 0) {
        "lcn:${row.number}"
    } else {
        // Services without an assigned Freeview LCN stay independently addressable.
        "service:${row.key}"
    }

    private fun score(row: T405GuideCandidate): Int =
        (if (row.active) 10000 else 0) +
        (if (row.currentProgramme) 200 else 0) +
        row.futureProgrammeCount.coerceIn(0, 32) * 3 +
        (if (row.mediaPidCount > 0) 70 else 0) +
        (if (row.frequencyKnown) 15 else 0)

    fun visible(rows: List<T405GuideCandidate>): List<T405GuideCandidate> {
        val best = linkedMapOf<String, T405GuideCandidate>()
        for (row in rows) {
            if (row.key.isBlank() || row.name.isBlank() || row.encrypted) continue
            val identity = slot(row)
            val previous = best[identity]
            if (previous == null || score(row) > score(previous) ||
                (score(row) == score(previous) && row.key < previous.key)) {
                best[identity] = row
            }
        }
        return best.values.sortedWith(
            compareBy<T405GuideCandidate> { if (it.number > 0) it.number else Int.MAX_VALUE }
                .thenBy { it.name.lowercase() }
                .thenBy { it.key })
    }
}
