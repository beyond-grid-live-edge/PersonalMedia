package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class PersonalMediaNavigationTest {
    @Test fun backReturnsAcrossTabsAndDoesNotExitAtRoot() {
        val stack = PersonalMediaNavHistory("Home")
        stack.visit("Home", "Stremio")
        stack.visit("Stremio", "Movies")
        stack.visit("Movies", "Settings")
        assertEquals("Movies", stack.previous("Settings"))
        assertEquals("Stremio", stack.previous("Movies"))
        assertEquals("Home", stack.previous("Stremio"))
        assertNull(stack.previous("Home"))
    }

    @Test fun sectionHistoryReturnsFromDetailsParent() {
        val stack = PersonalMediaNavHistory("Home")
        stack.visit("Home", "Discover")
        stack.visit("Discover", "Library")
        assertEquals("Discover", stack.previous("Library"))
        assertEquals("Home", stack.previous("Discover"))
        assertNull(stack.previous("Home"))
    }

    @Test fun repeatedTabAndHistoryLimitStaySafe() {
        val stack = PersonalMediaNavHistory("Home", limit = 2)
        stack.visit("Home", "Home")
        stack.visit("Home", "Movies")
        stack.visit("Movies", "TV Shows")
        stack.visit("TV Shows", "Music")
        assertEquals("TV Shows", stack.previous("Music"))
        assertEquals("Movies", stack.previous("TV Shows"))
        assertEquals("Home", stack.previous("Movies"))
    }
}
