package com.animacode.animadev.projects

import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONObject

class WorktreeStatusTextTest {
    @Test fun dirtyWorktreesClearlyShowUncommittedFiles() {
        assertEquals("Локальные изменения: 30 файлов (ещё не в коммите)",
            WorktreeStatusText.describe(true,30))
        assertEquals("Есть незакоммиченные файлы",
            WorktreeStatusText.describe(true,null))
    }

    @Test fun cleanAndUnknownAreNotTreatedAsGitHubSync() {
        assertEquals("Рабочие файлы без изменений",
            WorktreeStatusText.describe(false,0))
        assertEquals("Состояние файлов не проверено",
            WorktreeStatusText.describe(null,null))
    }

    @Test fun projectCatalogAcceptsOptionalWorktreeChangeCount() {
        val path=ProjectPolicy.HOME+"/anima-workspace/quizapp-three-themes"
        val input=JSONObject().put("id",ProjectPolicy.stableId(path))
            .put("name","quizapp-three-themes").put("path",path)
            .put("branch","feature/design-three-themes")
            .put("worktree",true).put("changes_count",30)
        val project=LocalProject.parse(input)
        assertEquals(true,project.worktree)
        assertEquals(30,project.changesCount)
        assertEquals(30,LocalProject.parse(project.json()).changesCount)
        input.remove("changes_count")
        assertEquals(null,LocalProject.parse(input).changesCount)
    }
}
