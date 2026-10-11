package com.animacode.animadev.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Emerald-only toolbar. The former global overflow popup is deliberately
 * absent: App options live on the Settings index as full-sized touch targets.
 * Pulse keeps its own toolbar and menu.
 */
@Composable
fun NeonTopBar(
    title:String,
    canGoBack:Boolean,
    showSettings:Boolean,
    onBack:()->Unit,
    onSettings:()->Unit
) {
    Surface(color=NeonGlass.background,tonalElevation=0.dp) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min=70.dp)
                    .padding(horizontal=13.dp,vertical=8.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                if(canGoBack) {
                    IconButton(onClick=onBack,modifier=Modifier.size(48.dp)) {
                        Icon(AppIcons.Back,"Вернуться",tint=NeonGlass.white)
                    }
                } else {
                    NeonIconTile(AppIcons.Terminal,Modifier.size(45.dp))
                }
                Text(title,modifier=Modifier.weight(1f),
                    style=MaterialTheme.typography.headlineSmall,
                    color=NeonGlass.white,fontWeight=FontWeight.SemiBold,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
                if(showSettings) {
                    Surface(onClick=onSettings,
                        shape=RoundedCornerShape(16.dp),
                        color=NeonGlass.raised,
                        border=BorderStroke(1.dp,NeonGlass.outline)) {
                        Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
                            Icon(AppIcons.Settings,"Настройки и информация",
                                tint=NeonGlass.green,modifier=Modifier.size(25.dp))
                        }
                    }
                }
            }
            HorizontalDivider(color=NeonGlass.outline.copy(alpha=.55f))
        }
    }
}
