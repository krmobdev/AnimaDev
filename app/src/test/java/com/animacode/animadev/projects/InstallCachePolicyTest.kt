package com.animacode.animadev.projects

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class InstallCachePolicyTest {
    @get:Rule val temp=TemporaryFolder()

    @Test fun retainsCurrentAndTwoNewestVerifiedApks() {
        val dir=temp.newFolder()
        val files=(0..4).map { n ->
            File(dir,('a'+n).toString().repeat(64)+".apk").apply {
                writeText("fixture-$n")
                setLastModified(10_000L+n*1000L)
            }
        }
        // Open a previously-built APK for installation: it must survive pruning.
        InstallCachePolicy.prune(dir,files[0])
        assertTrue(files[0].exists())
        assertTrue(files[4].exists())
        assertTrue(files[3].exists())
        assertFalse(files[1].exists())
        assertFalse(files[2].exists())
    }

    @Test fun neverDeletesUnrelatedFilesOrFollowsLinks() {
        val dir=temp.newFolder()
        val external=temp.newFile("outside.apk").apply {writeText("do not touch")}
        val link=File(dir,"f".repeat(64)+".apk")
        Files.createSymbolicLink(link.toPath(),external.toPath())
        val ignored=File(dir,"readme.txt").apply {writeText("keep")}
        val current=File(dir,"a".repeat(64)+".apk").apply {writeText("ok")}
        InstallCachePolicy.prune(dir,current)
        assertEquals("do not touch",external.readText())
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertTrue(ignored.exists())
        assertTrue(current.exists())
    }
}
