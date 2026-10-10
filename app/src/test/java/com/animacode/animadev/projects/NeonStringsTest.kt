package com.animacode.animadev.projects

import org.junit.Assert.assertEquals
import org.junit.Test

class NeonStringsTest {
    @Test fun neonRussianLabelsAreTranslatedPrecisely() {
        assertEquals("Журнал задания",NeonStrings.display("Job journal",false,true))
        assertEquals("На устройстве",NeonStrings.display("On device",false,true))
        assertEquals("Последний коммит · a1b2c3d",
            NeonStrings.display("Latest commit · a1b2c3d",false,true))
    }
    @Test fun legacySkinAndEnglishUiAreNotRewritten() {
        assertEquals("Job journal",NeonStrings.display("Job journal",false,false))
        assertEquals("Job journal",NeonStrings.display("Job journal",true,true))
        assertEquals("Проверить окружение",
            NeonStrings.display("Проверить окружение",false,false))
        assertEquals("Check environment",
            NeonStrings.display("Проверить окружение",true,true))
    }
    @Test fun exactMatchingNeverTouchesTechnicalDataOrLogs() {
        listOf(
            "/storage/emulated/0/Download/Tests.apk",
            "feature/termux-probe",
            "8b0aa8759d4de71fc9d10a8b974bb82503d5b553",
            "Job ID: ffffffffffffffffffffffffffffffff",
            "Error: Gradle failed at :app:compileDebugKotlin",
            "warning: Local changes are present"
        ).forEach {value->
            assertEquals(value,NeonStrings.display(value,false,true))
        }
    }
}
