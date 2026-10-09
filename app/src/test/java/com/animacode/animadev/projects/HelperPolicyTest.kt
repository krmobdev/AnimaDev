package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test
class HelperPolicyTest {
    @Test fun oldHelperLabel() {assertEquals("Обновить помощник",HelperPolicy.label(true))}
    @Test fun noPermissionNoExecution() {assertFalse(HelperPolicy.mayRun(false,true,true,false,false))}
    @Test fun oldResultIsNotReady() {assertFalse(HelperPolicy.mayRun(true,true,false,false,false))}
    @Test fun activeOrUnknownJobBlocksInstall() {assertFalse(HelperPolicy.mayConnect(true,true,false,true))}
    @Test fun pendingConnectionCannotBeRepeated() {assertFalse(HelperPolicy.mayConnect(true,true,true,false))}
    @Test fun installedConfirmedHelperCanRun() {assertTrue(HelperPolicy.mayRun(true,true,true,false,false))}
}
