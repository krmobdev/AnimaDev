package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
class BranchPolicyTest {
    @Test fun cleanWorkingTreeIsExplicit(){assertEquals("Чисто",BranchPolicy.workingTree(false))}
    @Test fun localChangesAreSeparateFromBranchComparison(){assertEquals("Есть локальные изменения",BranchPolicy.workingTree(true))}
    @Test fun missingMetadataNeverClaimsClean(){assertEquals("Не подтверждено",BranchPolicy.workingTree(null))}
    @Test fun failedMetadataReadNeverClaimsClean(){assertEquals("Не подтверждено",BranchPolicy.workingTree(false,true))}
    @Test fun matchingLocalAndRemoteAreOneRow() {val rows=BranchPolicy.merge(listOf(BranchRef("main","aaa","",true),BranchRef("main","aaa","",false,default=true)));assertEquals(1,rows.size);assertTrue(rows.single().comparison.contains("снимке"));assertTrue(rows.single().default)}
    @Test fun differingShaArePreserved() {val row=BranchPolicy.merge(listOf(BranchRef("test","aaa","",true,true),BranchRef("test","bbb","",false))).single();assertEquals("aaa",row.local?.sha);assertEquals("bbb",row.remote?.sha);assertTrue(row.comparison.contains("различаются"))}
    @Test fun defaultAndActiveHaveIndependentPriority() {val rows=BranchPolicy.merge(listOf(BranchRef("z","a","",true,true),BranchRef("main","a","",false,default=true),BranchRef("a","a","",true)));assertEquals(listOf("main","z","a"),rows.map {it.name})}
    @Test fun moreThanFortyAndSearchHaveNoCap() {val refs=(1..150).map {BranchRef("feature/$it","a","",true)};assertEquals(150,BranchPolicy.merge(refs).size);assertEquals(1,BranchPolicy.merge(refs,"feature/150").size)}
    @Test fun latestCommitPicksLocalWhenAvailable() {
        val local=BranchRef("main","abc","2026-10-09T10:22:05+00:00",true)
        val remote=BranchRef("main","abc","2026-10-08T11:00:00+00:00",false)
        assertEquals(local,BranchPolicy.merge(listOf(local,remote)).single().lastCommit)
    }
    @Test fun localizedDateIsCommitDate() {assertEquals("08.10.2026 · 18:56:00",BranchPolicy.date("2026-10-08T18:56:00+00:00",ZoneId.of("UTC")));assertEquals("Дата не подтверждена",BranchPolicy.date(""))}
}
