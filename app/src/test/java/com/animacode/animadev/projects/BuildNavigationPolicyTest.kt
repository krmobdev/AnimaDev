package com.animacode.animadev.projects

import org.junit.Assert.*
import org.junit.Test

class BuildNavigationPolicyTest {
    @Test fun resultIndexTargetsResultNotEndOfList() {
        assertEquals(3,BuildNavigationPolicy.debugResultIndex(false,false))
        assertEquals(4,BuildNavigationPolicy.debugResultIndex(true,false))
        assertEquals(4,BuildNavigationPolicy.debugResultIndex(false,true))
        assertEquals(5,BuildNavigationPolicy.debugResultIndex(true,true))
    }
    @Test fun scrollOnlyWhenNewDebugJobWasActuallyCreated() {
        val id="a".repeat(32)
        fun accepted(before:String,owner:String="flylife",action:String="BUILD_DEBUG",state:String="REQUESTED")=
            BuildNavigationPolicy.accepted(before,id,owner,action,state,"flylife")
        assertTrue(accepted("b".repeat(32)))
        assertFalse(accepted(id))
        assertFalse(accepted("b".repeat(32),owner="other"))
        assertFalse(accepted("b".repeat(32),state="FAILED"))
        assertFalse(accepted("b".repeat(32),action="CHECK"))
    }
}
