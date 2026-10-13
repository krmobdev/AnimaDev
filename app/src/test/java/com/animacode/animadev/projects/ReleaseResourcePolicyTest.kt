package com.animacode.animadev.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseResourcePolicyTest {
    @Test fun cpuIsNormalizedAcrossAllCores() {
        assertEquals(0f, ReleaseResourcePolicy.cpu(-1), 0.0001f)
        assertEquals(.20f, ReleaseResourcePolicy.cpu(20), 0.0001f)
        assertEquals(1f, ReleaseResourcePolicy.cpu(900), 0.0001f)
    }

    @Test fun memoryIsDisplayedInReadableUnits() {
        assertEquals("512 МБ", ReleaseResourcePolicy.memory(512))
        assertTrue(ReleaseResourcePolicy.memory(2048).contains("2"))
        assertTrue(ReleaseResourcePolicy.memory(2048).contains("ГБ"))
    }
}
