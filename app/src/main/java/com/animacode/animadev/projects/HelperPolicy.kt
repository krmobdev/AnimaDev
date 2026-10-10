package com.animacode.animadev.projects

object HelperPolicy {
    fun label(installed:Boolean)=if(installed)"Обновить помощник" else "Подключить помощник"
    fun mayConnect(permission:Boolean,termuxInstalled:Boolean,pending:Boolean,blocked:Boolean)=permission && termuxInstalled && !pending && !blocked
    fun mayRun(permission:Boolean,termuxInstalled:Boolean,ready:Boolean,pending:Boolean,blocked:Boolean)=mayConnect(permission,termuxInstalled,pending,blocked) && ready
}

/** A visible launch button must represent every synchronous start gate. */
object DeviceTestLaunchPolicy {
    fun canStart(permission:Boolean,termuxInstalled:Boolean,helperReady:Boolean,
                 repoPending:Boolean,repoBlocked:Boolean,adbPending:Boolean,
                 releaseBusy:Boolean):Boolean =
        HelperPolicy.mayRun(permission,termuxInstalled,helperReady,repoPending,repoBlocked) &&
            !adbPending && !releaseBusy
}
