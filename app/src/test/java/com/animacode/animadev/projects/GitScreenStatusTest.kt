package com.animacode.animadev.projects

import org.junit.Assert.assertEquals
import org.junit.Test

class GitScreenStatusTest {
    private fun title(
        working: Boolean = false, state: String = "SUCCEEDED", hasJob: Boolean = true,
        hasSnapshot: Boolean = true, branchAllowed: Boolean = true,
        originOk: Boolean = true, dirty: Boolean = false,
        checked: Boolean = true, ahead: Int = 0, behind: Int = 0,
        justSynced: Boolean = false
    ) = GitScreenStatus.headline(
        working, state, hasJob, hasSnapshot, branchAllowed, originOk,
        dirty, checked, ahead, behind, justSynced
    )

    @Test fun cleanUpToDateSnapshot() {
        assertEquals("Исходники актуальны", title())
    }

    @Test fun incomingCommitsRequireExplicitSync() {
        assertEquals("Доступны обновления", title(behind = 3))
    }

    @Test fun divergedBranchesMustNotLookUpToDate() {
        assertEquals("Ветки разошлись", title(ahead = 2, behind = 1))
    }

    @Test fun failedCheckHasPriorityOverStaleSnapshot() {
        assertEquals("Ошибка Git · подробности ниже",
            title(state = "FAILED", checked = false))
    }

    @Test fun unknownDoesNotLookSuccessful() {
        assertEquals("Результат Git неизвестен", title(state = "UNKNOWN"))
    }

    @Test fun dirtyOrRemoteMismatchTakePriorityOverCounts() {
        assertEquals("Есть локальные изменения", title(dirty = true, behind = 5))
        assertEquals("Origin не подтверждён", title(originOk = false, behind = 5))
    }

    @Test fun unverifiedRemoteDoesNotClaimUpToDate() {
        assertEquals("GitHub ещё не проверен", title(checked = false))
    }

    @Test fun syncSucceededReportsFetchedSourcesNotInstalledApk() {
        assertEquals("Исходники обновлены", title(justSynced = true))
    }

    @Test fun russianPluralForms() {
        assertEquals("коммит", GitScreenStatus.commitWord(1))
        assertEquals("коммита", GitScreenStatus.commitWord(2))
        assertEquals("коммитов", GitScreenStatus.commitWord(5))
        assertEquals("коммитов", GitScreenStatus.commitWord(11))
        assertEquals("коммитов", GitScreenStatus.commitWord(14))
        assertEquals("коммит", GitScreenStatus.commitWord(21))
    }
}
