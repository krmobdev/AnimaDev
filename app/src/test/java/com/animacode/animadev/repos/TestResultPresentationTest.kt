package com.animacode.animadev.repos
import org.junit.Assert.*
import org.junit.Test
class TestResultPresentationTest {
    @Test fun oldDeviceFailureGetsDeviceMessage() {
        val text=TestResultPresentation.message("TEST_DEVICE","TESTS_FAILED","Есть проваленные unit-тесты")
        assertTrue(text.contains("инструментальные"))
        assertFalse(text.contains("unit"))
    }
    @Test fun unitFailureRemainsUnitFailure() {
        assertTrue(TestResultPresentation.message("TEST","TESTS_FAILED","old").contains("unit"))
    }
    @Test fun transportErrorKeepsItsActualReason() {
        assertEquals("Нет устройства",TestResultPresentation.message("TEST_DEVICE","ADB_MISSING","Нет устройства"))
    }
}
