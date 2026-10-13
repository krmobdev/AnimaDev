package com.animacode.animadev.shizuku

import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku

/** Real Binder test. Never requests/grants shell permission in automation. */
@RunWith(AndroidJUnit4::class)
class ShizukuServiceInstrumentedTest {
    @Test fun detectsRunningBinderBeforeUserGrantsAppPermission() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val installed = try {
            ctx.packageManager.getPackageInfo(ShizukuConnection.PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        assumeTrue("Shizuku manager is not installed on this test device", installed)
        val gateway = ShizukuConnection(ctx)
        gateway.start()
        try {
            val deadline = SystemClock.uptimeMillis() + 5000
            while (!Shizuku.pingBinder() && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(100)
            }
            assumeTrue("Shizuku service is not running on this test device", Shizuku.pingBinder())
            gateway.refresh()
            val info = gateway.info
            assertTrue("Unexpected Shizuku state: " + info,
                info.state == ShizukuState.PERMISSION_REQUIRED ||
                info.state == ShizukuState.PERMISSION_DENIED ||
                info.state == ShizukuState.CONNECTED)
            if (info.state == ShizukuState.CONNECTED) {
                assertTrue("Invalid service UID: " + info.uid, info.uid == 2000 || info.uid == 0)
            }
        } finally {
            gateway.stop()
        }
    }
}
