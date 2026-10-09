package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test
class WorktreePresentationTest {
    @Test fun collapsedPanelDoesNotRenderMetadata(){val metadata=(1..18).toList();assertTrue(WorktreePresentation.rows(metadata,false).isEmpty());assertEquals(18,metadata.size)}
    @Test fun expandedPanelPreservesAllRecordsAndOrder(){val metadata=(1..18).toList();assertSame(metadata,WorktreePresentation.rows(metadata,true))}
    @Test fun collapseAfterExpansionDoesNotDeleteRecords(){val metadata=listOf("branch / SHA / path");WorktreePresentation.rows(metadata,true);assertTrue(WorktreePresentation.rows(metadata,false).isEmpty());assertEquals(listOf("branch / SHA / path"),WorktreePresentation.rows(metadata,true))}
}
