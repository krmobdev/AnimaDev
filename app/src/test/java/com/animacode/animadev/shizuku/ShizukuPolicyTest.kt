package com.animacode.animadev.shizuku

import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuPolicyTest {
    @Test fun connectedRequiresBinderAndPermission() {
        assertEquals(ShizukuState.CONNECTED, ShizukuPolicy.resolve(true, true, true, true, false))
        assertEquals(ShizukuState.SERVICE_STOPPED, ShizukuPolicy.resolve(true, false, true, true, false))
        assertEquals(ShizukuState.PERMISSION_REQUIRED, ShizukuPolicy.resolve(true, true, true, false, false))
    }

    @Test fun absenceAndUnsupportedServiceStayDistinct() {
        assertEquals(ShizukuState.NOT_INSTALLED, ShizukuPolicy.resolve(false, false, true, false, false))
        assertEquals(ShizukuState.UNSUPPORTED, ShizukuPolicy.resolve(true, true, false, true, false))
    }

    @Test fun deniedStateHasPriorityOverNewPermissionRequest() {
        assertEquals(ShizukuState.PERMISSION_DENIED, ShizukuPolicy.resolve(true, true, true, false, true))
        assertEquals(ShizukuState.CONNECTED, ShizukuPolicy.resolve(true, true, true, true, true))
    }
}
