package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test
class DestinationPolicyTest {
    @Test fun downloadsDefault(){assertEquals("Downloads",DestinationPolicy.DEFAULT);assertFalse(DestinationPolicy.needsRecovery("",false))}
    @Test fun missingGrantAndDecline(){assertTrue(DestinationPolicy.needsRecovery("content://tree",false));assertFalse(DestinationPolicy.needsRecovery("content://tree",true));assertEquals("",DestinationPolicy.afterDecline())}
    @Test fun uniqueNames(){assertNotEquals(DestinationPolicy.filename("AnimaDev","txt","a".repeat(32)),DestinationPolicy.filename("AnimaDev","txt","b".repeat(32)))}
    @Test(expected=IllegalArgumentException::class) fun pathTraversal(){DestinationPolicy.filename("../other","txt","a".repeat(32))}
}
