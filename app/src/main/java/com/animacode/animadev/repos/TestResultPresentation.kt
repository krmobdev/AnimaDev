package com.animacode.animadev.repos

/** Corrects old saved messages too; never changes the actual outcome or counts. */
object TestResultPresentation {
    fun message(action:String,code:String,fallback:String):String =
        if(code in setOf("TESTS_FAILED","TEST_FAILED")) {
            if(action=="TEST_DEVICE")
                "Есть проваленные инструментальные тесты. Откройте журнал: там имена тестов и причины."
            else "Есть проваленные unit-тесты. Откройте журнал: там имена тестов и причины."
        } else fallback
}
