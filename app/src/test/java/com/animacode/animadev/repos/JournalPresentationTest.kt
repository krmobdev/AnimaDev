package com.animacode.animadev.repos
import org.junit.Assert.*
import org.junit.Test
class JournalPresentationTest {
    @Test fun importantRemovesCommandsAndTheirContinuations() {
        val log="Тесты: 396/396 успешно\n[12:00:00] command: git -c core.hooksPath=/dev/null status\nflags continuation\n[12:00:01] step: technical detail\n[12:00:02] stdout: > Task :app:test\n[12:00:03] stdout: warning: check this\n[12:00:04] event: Итог: SUCCEEDED · exit=0"
        val s=JournalPresentation.important(log);assertFalse(s.contains("hooksPath"));assertFalse(s.contains("continuation"));assertFalse(s.contains("step:"));assertFalse(s.contains("> Task"));assertTrue(s.contains("warning:"));assertTrue(s.contains("exit=0"));assertTrue(s.contains("396/396"))
    }
    @Test fun failuresAndResultsRemain() {assertTrue(JournalPresentation.important("[10:00:00] stderr: ERROR: failed").contains("ERROR"))}
    @Test fun localizedSummaryDistinguishesJUnitTime() {val s=JournalPresentation.summary(396,396,0,0,1200,12);assertTrue(s.contains("396/396"));assertTrue(s.contains("Суммарное время JUnit"));assertFalse(s.contains("duration_ms"))}
    @Test fun commandsAreSeparateExpandableBlocks() {val s="header\n[12:00:00] command: git status\n[12:00:01] event: Итог";val chunks=JournalPresentation.chunks(s);assertEquals(1,chunks.count {JournalPresentation.isCommand(it)});assertTrue(chunks.joinToString("").contains(s))}
}
