package com.animacode.animadev.projects

/** Only request RUN_COMMAND when the user enters a Termux-backed surface.
 * No permission request loops after dismissal or denial.
 */
object TermuxPermissionPromptPolicy {
    fun shouldPrompt(resumed:Boolean,permitted:Boolean,installed:Boolean,
                     skipped:Boolean,section:String,route:String,
                     projectSupported:Boolean):Boolean =
        resumed && !permitted && installed && !skipped &&
        (section=="termux" ||
         (section=="projects" && projectSupported &&
          route in setOf("workspace","git","tests","build","branches")))
}
