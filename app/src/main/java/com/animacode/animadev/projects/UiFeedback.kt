package com.animacode.animadev.projects

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View

/** Uses OS tap feedback; respects Android sound/haptic configuration as well as in-app switches. */
class UiFeedback(context:Context) {
    val prefs=context.getSharedPreferences("interface_feedback_v1",Context.MODE_PRIVATE)
    fun vibrations()=prefs.getBoolean("vibrations",true)
    fun sounds()=prefs.getBoolean("sounds",true)
    fun setVibrations(value:Boolean)=prefs.edit().putBoolean("vibrations",value).apply()
    fun setSounds(value:Boolean)=prefs.edit().putBoolean("sounds",value).apply()
    fun tap(view:View) {
        if(vibrations())view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if(sounds())view.playSoundEffect(SoundEffectConstants.CLICK)
    }
}
