package com.rawtracker.i18n

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStringsTest {
    @Test
    fun polishLocaleUsesPolishCopy() {
        val t = appStringsFor("pl-PL")

        assertEquals(AppLanguage.Polish, appLanguageFor("pl-PL"))
        assertEquals("DZIŚ", t.todayLabel())
        assertEquals("PON", t.weekdayAbbrev(DayOfWeek.MONDAY))
        assertEquals("STY", t.monthAbbrev(Month.JANUARY))
        assertEquals("Dodaj wodę", t.logWater)
        assertEquals("+250 ml wody", t.waterLogged(250))
    }

    @Test
    fun unknownLocaleFallsBackToEnglish() {
        val t = appStringsFor("en-US")

        assertEquals(AppLanguage.English, appLanguageFor("en-US"))
        assertEquals("TODAY", t.todayLabel())
        assertEquals("MON", t.weekdayAbbrev(DayOfWeek.MONDAY))
        assertEquals("JAN", t.monthAbbrev(Month.JANUARY))
    }
}
