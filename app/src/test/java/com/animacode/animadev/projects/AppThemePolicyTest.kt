package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test
class AppThemePolicyTest {
    @Test fun explicitLightOverridesSystemDark() {
        assertFalse(AppThemePolicy.dark("light",true))
        assertFalse(AppThemePolicy.dark("light",false))
    }
    @Test fun explicitDarkOverridesSystemLight() {
        assertTrue(AppThemePolicy.dark("dark",false))
        assertTrue(AppThemePolicy.dark("dark",true))
    }
    @Test fun systemAndUnknownModesFollowSystem() {
        for(mode in listOf("system","")) {
            assertTrue(AppThemePolicy.dark(mode,true))
            assertFalse(AppThemePolicy.dark(mode,false))
        }
    }
}
