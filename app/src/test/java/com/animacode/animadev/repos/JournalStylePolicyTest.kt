package com.animacode.animadev.repos
import org.junit.Assert.*
import org.junit.Test

class JournalStylePolicyTest {
    @Test fun commandsAndPathsAreNotErrors(){
        listOf("[16:50:39] command: bash --error-file error.log", "stdout: /tmp/ERROR.kt", "stdout: Test errorHandler PASSED").forEach {assertEquals(LogTone.NORMAL,JournalStylePolicy.styles(it).single().tone)}
    }
    @Test fun structuredOutput(){
        assertEquals(LogTone.SUCCESS,JournalStylePolicy.styles("[16:50:39] stdout: BUILD SUCCESSFUL in 1m").single().tone)
        assertEquals(LogTone.ERROR,JournalStylePolicy.styles("stderr: FAILURE: Build failed").single().tone)
        assertEquals(LogTone.WARNING,JournalStylePolicy.styles("stdout: w: deprecated").single().tone)
        assertEquals(LogTone.SUCCESS,JournalStylePolicy.styles("event: SUCCEEDED").single().tone)
    }
    @Test fun explicitSuccessIsRecognized(){assertEquals(LogTone.SUCCESS,JournalStylePolicy.styles("[16:50:39] stdout: Success").single().tone)}
    @Test fun realRunnerFinalEvent(){assertEquals(LogTone.SUCCESS,JournalStylePolicy.styles("[16:50:39] event: Итог: SUCCEEDED · exit=0").single().tone);assertEquals(LogTone.ERROR,JournalStylePolicy.styles("event: Итог: FAILED · exit=1").single().tone)}
    @Test fun pathologicalLineIsBounded(){val text="[16:50:39] stdout: "+"x".repeat(100_000);val c=JournalStylePolicy.chunks(text);assertEquals(text,c.joinToString(""));assertTrue(c.all {it.length<=24_000});assertTrue(c.first().startsWith("[16:50:39]"))}
    @Test fun timestampIsIndependent(){val s=JournalStylePolicy.styles("[16:50:39] stderr: ERROR: failed").single();assertEquals(10,s.timestampEnd);assertEquals(LogTone.ERROR,s.tone)}
    @Test fun chunksPreserveSourceAndLongLine(){val text="[16:50:39] stdout: "+"a".repeat(6000)+"\n[16:50:40] event: SUCCEEDED\n";val chunks=JournalStylePolicy.chunks(text);assertEquals(text,chunks.joinToString(""));assertTrue(chunks[0].length>6000);assertTrue(chunks[1].startsWith("[16:50:40]"))}
}
