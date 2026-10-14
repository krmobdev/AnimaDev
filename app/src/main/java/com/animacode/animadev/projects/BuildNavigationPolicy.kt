package com.animacode.animadev.projects

object BuildNavigationPolicy {
    fun debugResultIndex(neon:Boolean,recoveryCard:Boolean):Int =
        3+(if(neon)1 else 0)+(if(recoveryCard)1 else 0)
    fun accepted(before:String,id:String,owner:String,action:String,state:String,projectId:String):Boolean =
        id!=before && id.matches(Regex("[a-f0-9]{32}")) &&
        owner==projectId && action=="BUILD_DEBUG" &&
        state in setOf("REQUESTED","QUEUED","RUNNING")
}
