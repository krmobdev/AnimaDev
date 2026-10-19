package com.animacode.animadev.projects

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseHistoryRetentionInstrumentedTest {
    @Test fun evictionRemovesOrphanEventsWithoutLosingRecentHistory() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("release_history_v1",Context.MODE_PRIVATE)
        // Execute only in the dedicated device-fixture application, not production.
        assertEquals("com.animacode.animadev.devicefixture",context.packageName)
        prefs.edit().clear().commit()
        try {
            val cache=ReleaseHistoryCache(context)
            for(i in 0 until 105) {
                val id=i.toString(16).padStart(32,'0')
                val record=JSONObject().put("id",id).put("project","fixture")
                    .put("state","SUCCEEDED").put("phase","DONE")
                    .put("message","done").put("started_at",1_000L).put("ended_at",2_000L)
                cache.observe(record)
            }
            assertEquals(100,cache.history("fixture").size)
            assertTrue(cache.events("0".repeat(32)).isEmpty())
            assertFalse(prefs.contains("events_"+"0".repeat(32)))
            assertEquals(1,cache.events((104).toString(16).padStart(32,'0')).size)
            assertEquals(100,prefs.all.keys.count {it.startsWith("events_")})
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
