package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test
class ProjectGroupingTest {
    @Test fun sameCommonDirectory(){val root=ProjectIdentity("a","/home/a","/home/a",false);val wt=ProjectIdentity("b","/home/b","/home/a",true);val g=ProjectGrouping.groups(listOf(wt,root));assertEquals(1,g.size);assertEquals(root,g[0][0]);assertEquals(2,g[0].size)}
    @Test fun unknownWorktreeIsNotMerged(){assertEquals(2,ProjectGrouping.groups(listOf(ProjectIdentity("a","/home/a","",false),ProjectIdentity("b","/home/b","",true))).size)}
    @Test fun copiesDoNotMerge(){assertEquals(2,ProjectGrouping.groups(listOf(ProjectIdentity("a","/home/a","/home/a",false),ProjectIdentity("b","/home/b","/home/b",false))).size)}
}
