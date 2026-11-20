package com.personalmedia.android

import org.junit.Assert.assertEquals
import org.junit.Test

class PmI18nTest {
    @Test fun englishIsUnchanged() {
        assertEquals("Settings", PmI18n.translate("Settings", "en"))
        assertEquals("Settings", PmI18n.translate("Settings", "en-US"))
    }

    @Test fun familiarNavigationLabelsHaveGaelicTranslations() {
        assertEquals("Roghainnean", PmI18n.translate("Settings", "gd"))
        assertEquals("Dachaigh", PmI18n.translate("Home", "gd"))
        assertEquals("Tbh beò", PmI18n.translate("Live TV", "gd"))
        assertEquals("CÀNAN AN EADAR-AGHAIDH", PmI18n.translate("INTERFACE LANGUAGE", "gd"))
    }

    @Test fun unknownStringsAndUserMediaNamesAreNotChanged() {
        val mediaName = "My Summer Holiday 2026.mkv"
        assertEquals(mediaName, PmI18n.translate(mediaName, "gd"))
        assertEquals("Stremio", PmI18n.translate("Stremio", "gd"))
    }

    @Test fun dynamicCountsHaveGaelicTranslations() {
        assertEquals("5 nithean", PmI18n.translate("5 items", "gd"))
        assertEquals("2 faidhlichean ionadail", PmI18n.translate("2 local files", "gd"))
        assertEquals("1 faidhle ionadail", PmI18n.translate("1 local file", "gd"))
        assertEquals("1 ràith · 1 earrann", PmI18n.translate("1 seasons · 1 episodes", "gd"))
        assertEquals("2 ràithean · 12 earrannan", PmI18n.translate("2 seasons  •  12 episodes", "gd"))
        assertEquals("Prògraman Tbh · 1 sreath", PmI18n.translate("TV Shows · 1 series", "gd"))
        assertEquals("Ceòl · 106", PmI18n.translate("Music · 106", "gd"))
        assertEquals("7 stàlaichte", PmI18n.translate("7 installed", "gd"))
        assertEquals("7 tuilleadain (stàlaichte) · 7 air an sealltainn",
            PmI18n.translate("7 add-ons in installed · 7 shown", "gd"))
    }

    @Test fun newlyCoveredScreensAndSafetyTextAreLocalised() {
        assertEquals("Rannsaich filmichean", PmI18n.translate("Explore Movies", "gd"))
        assertEquals("Ion-phortaich faidhlichean Dealbhan",
            PmI18n.translate("Import Pictures files", "gd"))
        assertEquals("Tuilleadain Stremio", PmI18n.translate("Stremio Add-ons", "gd"))
        assertEquals("Dì-stàlaich", PmI18n.translate("Uninstall", "gd"))
        assertEquals("Sgrùd slighe Wi-Fi Android", PmI18n.translate("Check Android Wi-Fi route", "gd"))
        assertEquals("Chan atharraichear na faidhlichean meadhain tùsail agad.",
            PmI18n.translate("Your original media files remain untouched.", "gd"))
    }

    @Test fun userContentIsNotChangedByDynamicRules() {
        val movieTitle = "7 Days in Scotland"
        assertEquals(movieTitle, PmI18n.translate(movieTitle, "gd"))
        assertEquals("Cinémata · Popular", PmI18n.translate("Cinémata · Popular", "gd"))
    }
}
