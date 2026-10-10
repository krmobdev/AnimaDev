package com.animacode.animadev.projects

import org.junit.Assert.*
import org.junit.Test

class SigningPolicyTest {
    @Test fun filePathValidated() {
        assertTrue(SigningPolicy.hasPath("/storage/emulated/0/Download/release.jks"))
        assertTrue(SigningPolicy.hasPath(ProjectPolicy.HOME+"/keys/release.keystore"))
        assertFalse(SigningPolicy.hasPath("/tmp/release.jks"))
        assertFalse(SigningPolicy.hasPath("/storage/emulated/0/Download/../release.jks"))
        assertFalse(SigningPolicy.hasPath(ProjectPolicy.HOME+"/notes.txt"))
    }
    @Test fun aabUnavailableWithoutSafeSecretChannel() {
        assertFalse(SigningPolicy.mayRelease("",true))
        assertFalse(SigningPolicy.mayRelease("/storage/emulated/0/Download/release.jks",false))
    }
    @Test fun pickedKeyFileNameValidatedWithoutFilesystemPath() {
        assertTrue(SigningPolicy.isKeyName("release.jks"))
        assertTrue(SigningPolicy.isKeyName("Production.KEYSTORE"))
        assertTrue(SigningPolicy.isKeyName("my signing key.JKS"))
        assertFalse(SigningPolicy.isKeyName("release.zip"))
        assertFalse(SigningPolicy.isKeyName("release.jks.txt"))
        assertFalse(SigningPolicy.isKeyName("../release.jks"))
        assertFalse(SigningPolicy.isKeyName("dir\\release.jks"))
        assertFalse(SigningPolicy.isKeyName("release\n.jks"))
        assertFalse(SigningPolicy.isKeyName(""))
    }
}
