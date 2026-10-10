package com.animacode.animadev.projects

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Exercises the REAL Android Keystore using only synthetic passwords. */
@RunWith(AndroidJUnit4::class)
class SigningSecretVaultInstrumentedTest {
    @Test fun passwordsAreEncryptedBoundToAliasAndRemovable() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val vault=SigningSecretVault(context)
        val id="instrumentation-"+UUID.randomUUID().toString()
        val fileUri="content://com.animadev.instrumentation/test-only.jks"
        val store="device-smoke-store-not-real"
        val key="device-smoke-key-not-real"
        try {
            assertTrue("Android Keystore save failed",vault.save(id,fileUri,"testAlias",store,key))
            assertTrue(vault.has(id))
            val encrypted=context.getSharedPreferences("signing_secrets_v1",0).getString(id,"").orEmpty()
            assertFalse("Plaintext store password found in preferences",encrypted.contains(store))
            assertFalse("Plaintext key password found in preferences",encrypted.contains(key))
            // Read through another vault instance, as after recreation of the UI.
            val loaded=SigningSecretVault(context).load(id,fileUri,"testAlias")
            assertNotNull(loaded)
            assertEquals(store,loaded!!.storePassword)
            assertEquals(key,loaded.keyPassword)
            assertNull("Another alias must never unlock the credential",vault.load(id,fileUri,"differentAlias"))
            assertNull("Another JKS URI must never unlock it",vault.load(id,fileUri+"/other","testAlias"))
        } finally {
            assertTrue(vault.forget(id))
        }
        assertFalse(vault.has(id))
        assertNull(vault.load(id,fileUri,"testAlias"))
    }
}
