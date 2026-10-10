package com.animacode.animadev.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Youthful graphite-and-electric-green palette, independent of wallpaper color. */
object AnimaPulse {
    val dark=Color(0xFF111A1C)
    val panel=Color(0xFF1D2A2C)
    val green=Color(0xFF7BFFAE)
    val muted=Color(0xFFB7CBC5)
    val border=Color(0xFF3C6054)
}

@Composable fun ActiveBranchHero(branch:String,sha:String,dirty:Boolean?,modifier:Modifier=Modifier) {
    val valid=branch.isNotBlank() && branch!="Detached HEAD"
    val colors=MaterialTheme.colorScheme
    Surface(modifier=modifier.fillMaxWidth(),
        color=colors.surfaceContainer,contentColor=colors.onSurface,
        shape=MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Surface(color=colors.secondaryContainer,contentColor=colors.onSecondaryContainer,shape=RoundedCornerShape(50)) {
                    Text("Активная ветка",Modifier.padding(horizontal=12.dp,vertical=5.dp),
                        color=colors.onSecondaryContainer,style=MaterialTheme.typography.labelMedium,
                        fontWeight=FontWeight.Black)
                }
                Text("ЛОКАЛЬНАЯ ВЕТКА",style=MaterialTheme.typography.labelSmall,
                    color=colors.onSurfaceVariant)
            }
            Text(if(valid)branch else "HEAD не подтверждён",
                color=colors.primary,style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.ExtraBold,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(sha.take(12).ifBlank {"SHA неизвестен"}+"   •   "+
                 when(dirty){true->"Есть изменения";false->"Рабочее дерево чистое";else->"Нужна проверка"},
                color=colors.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable fun PulseToolTile(name:String,subtitle:String,icon:ImageVector,
                               modifier:Modifier=Modifier,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=modifier.heightIn(min=154.dp),
        shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer,
        contentColor=MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize().padding(17.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(29.dp))
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(name,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurface,
                    fontWeight=FontWeight.Bold)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines=2)
            }
        }
    }
}
