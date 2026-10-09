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
    @Test fun timestampIsIndependent(){val s=JournalStylePolicy.styles("[16:50:39] stderr: ERROR: failed").single();assertEquals(10,s.timestampEnd);assertEquals(LogTone.ERROR,s.tone)}
    @Test fun chunksPreserveSourceAndLongLine(){val text="[16:50:39] stdout: "+"a".repeat(6000)+"\n[16:50:40] event: SUCCEEDED\n";val chunks=JournalStylePolicy.chunks(text);assertEquals(text,chunks.joinToString(""));assertTrue(chunks[0].length>6000);assertTrue(chunks[1].startsWith("[16:50:40]"))}
}
