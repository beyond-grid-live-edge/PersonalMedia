package com.personalmedia.android

fun main() {
    val rows = listOf(
        T405GuideCandidate("1:1:100", 4, "Channel 4", futureProgrammeCount = 4, currentProgramme = true, mediaPidCount = 3, frequencyKnown = true),
        T405GuideCandidate("2:2:100", 4, "Channel 4", futureProgrammeCount = 0, currentProgramme = false),
        T405GuideCandidate("1:1:101", 5, "5", futureProgrammeCount = 2),
        T405GuideCandidate("2:2:101", 5, "5", futureProgrammeCount = 0),
        T405GuideCandidate("1:1:102", 6, "ITV2", futureProgrammeCount = 1),
        T405GuideCandidate("1:1:103", 8, "Encrypted TV", encrypted = true),
        T405GuideCandidate("1:1:104", 0, "Unnumbered Radio"),
        T405GuideCandidate("2:2:104", 0, "Unnumbered Radio")
    )
    val visible = T405GuidePolicy.visible(rows)
    check(visible.size == 5) { "Expected 3 LCN channels and 2 distinct unnumbered services: $visible" }
    check(visible.first { it.number == 4 }.key == "1:1:100")
    check(visible.first { it.number == 5 }.key == "1:1:101")
    check(visible.none { it.number == 8 })
    // The currently tuned duplicate must remain selectable/highlighted until retuning.
    val selectedRows = rows.map { if (it.key == "2:2:100") it.copy(active = true) else it }
    check(T405GuidePolicy.visible(selectedRows).first { it.number == 4 }.key == "2:2:100")
    check(rows.size == 8) { "Guide selection should not mutate persistent saved services" }
    println("Sky guide LCN deduplication/selected-service test PASS")
}
