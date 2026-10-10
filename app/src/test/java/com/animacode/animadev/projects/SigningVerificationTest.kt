package com.animacode.animadev.projects

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path

class SigningVerificationTest {
    private fun fixture(type:String, store:String, key:String): ByteArray {
        val dir=Files.createTempDirectory("animadev-signing-test-")
        val output=dir.resolve("synthetic."+if(type=="JKS")"jks" else "p12")
        try {
            // The test generates an expendable fixture. No real project key or user password is used.
            val keytool=Path.of(System.getProperty("java.home"),"bin","keytool").toString()
            val command=listOf(keytool,"-genkeypair","-alias","fixture",
                "-keyalg","RSA","-keysize","2048","-validity","1",
                "-dname","CN=AnimaDev Test Only","-storetype",type,
                "-keystore",output.toString(),"-storepass",store,"-keypass",key,"-noprompt")
            val process=ProcessBuilder(command).redirectErrorStream(true).start()
            process.inputStream.use { it.readBytes() }
            assertEquals("Synthetic keytool fixture failed",0,process.waitFor())
            return Files.readAllBytes(output)
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(dir)
        }
    }
    @Test fun validJksPrivateKeyAndAlias() {
        val bytes=fixture("JKS","fixture-store-pass","fixture-key-pass")
        assertEquals(SigningVerification.Outcome.VERIFIED,
            SigningVerification.check(ByteArrayInputStream(bytes),"my_key_store.jks","fixture",
                "fixture-store-pass".toCharArray(),"fixture-key-pass".toCharArray()))
        assertEquals(SigningVerification.Outcome.BAD_KEY_PASSWORD,
            SigningVerification.check(ByteArrayInputStream(bytes),"my_key_store.jks","fixture",
                "fixture-store-pass".toCharArray(),"wrong-fixture-key".toCharArray()))
        assertEquals(SigningVerification.Outcome.ALIAS_NOT_FOUND,
            SigningVerification.check(ByteArrayInputStream(bytes),"my_key_store.jks","key0",
                "fixture-store-pass".toCharArray(),"fixture-key-pass".toCharArray()))
        assertEquals(SigningVerification.Outcome.UNSUPPORTED_OR_BAD_STORE_PASSWORD,
            SigningVerification.check(ByteArrayInputStream(bytes),"my_key_store.jks","fixture",
                "wrong-fixture-store".toCharArray(),"fixture-key-pass".toCharArray()))
    }
    @Test fun validPkcs12PrivateKeyInKeystoreExtension() {
        val bytes=fixture("PKCS12","fixture-only-pass","fixture-only-pass")
        assertEquals(SigningVerification.Outcome.VERIFIED,
            SigningVerification.check(ByteArrayInputStream(bytes),"my_key_store.keystore","fixture",
                "fixture-only-pass".toCharArray(),"fixture-only-pass".toCharArray()))
    }
    @Test fun rejectsInvalidSelectionAndOversizedInputs() {
        assertEquals(SigningVerification.Outcome.INVALID_SELECTION,
            SigningVerification.check(ByteArrayInputStream(byteArrayOf()),"not_a_key.txt","fixture",
                "anything".toCharArray(),"anything".toCharArray()))
        assertEquals(SigningVerification.Outcome.TOO_LARGE,
            SigningVerification.check(ByteArrayInputStream(ByteArray(SigningVerification.MAX_KEYSTORE_BYTES+1)),
                "my_key_store.jks","fixture","anything".toCharArray(),"anything".toCharArray()))
    }
}
