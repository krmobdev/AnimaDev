package com.animacode.animadev.projects

/** Non-destructive eligibility rules for an old local CHECK. */
object LocalCheckRecoveryPolicy {
    fun needsUpgrade(ready:Boolean,installedSha:String,currentSha:String):Boolean =
        !ready || installedSha!=currentSha
    fun canUpdate(pending:Boolean,permitted:Boolean,termuxInstalled:Boolean,
                  eligible:Boolean):Boolean =
        !pending && permitted && termuxInstalled && eligible
    fun canQuery(pending:Boolean,permitted:Boolean,termuxInstalled:Boolean,
                 ready:Boolean,oldIdValid:Boolean,needsUpgrade:Boolean):Boolean =
        !pending && permitted && termuxInstalled && ready && oldIdValid && !needsUpgrade
}
