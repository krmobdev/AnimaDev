package com.animacode.animadev.projects

import org.junit.Assert.assertEquals
import org.junit.Test

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

}
