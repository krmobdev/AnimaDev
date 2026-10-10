package com.animacode.animadev.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Emerald-only presentation building blocks. No repository or runner effects. */
@Composable fun NeonTabs(labels:List<String>,selected:Int,onSelect:(Int)->Unit,
                        modifier:Modifier=Modifier) {
    Surface(modifier=modifier,shape=RoundedCornerShape(21.dp),
        color=NeonGlass.surface,border=BorderStroke(1.dp,NeonGlass.outline)) {
        Row(Modifier.padding(5.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed {index,label->
                val active=index==selected
                Surface(onClick={onSelect(index)},modifier=Modifier.weight(1f).heightIn(min=49.dp),
                    shape=RoundedCornerShape(17.dp),
                    color=if(active)Color(0xFF194330) else Color.Transparent,
                    border=if(active)BorderStroke(1.dp,NeonGlass.green.copy(alpha=.36f)) else null) {
                    Box(contentAlignment=Alignment.Center,
                        modifier=Modifier.padding(horizontal=6.dp,vertical=8.dp)) {
                        Text(label,style=MaterialTheme.typography.labelLarge,
                            color=if(active)NeonGlass.softGreen else NeonGlass.muted,
                            fontWeight=if(active)FontWeight.Bold else FontWeight.Normal,
                            maxLines=2)
                    }
                }
            }
        }
    }
}

@Composable fun NeonSection(title:String,icon:ImageVector,subtitle:String?=null,
                            modifier:Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(icon,null,tint=NeonGlass.green,modifier=Modifier.size(24.dp))
            Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,
                color=NeonGlass.white)
        }
        if(!subtitle.isNullOrBlank())
            Text(subtitle,style=MaterialTheme.typography.bodySmall,color=NeonGlass.muted)
    }
}

@Composable fun NeonKeyValue(label:String,value:String,
                            icon:ImageVector?=null,modifier:Modifier=Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical=3.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        if(icon!=null)Icon(icon,null,tint=NeonGlass.softGreen,modifier=Modifier.size(21.dp))
        Text(label,style=MaterialTheme.typography.bodyMedium,
            modifier=Modifier.weight(1f),color=NeonGlass.muted)
        Text(value,style=MaterialTheme.typography.bodyMedium,color=NeonGlass.white,
            maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}

@Composable fun NeonHistoryEntry(
    title:String,subtitle:String,metadata:String,successful:Boolean,
    running:Boolean=false,onClick:()->Unit
) {
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth(),
        color=NeonGlass.surface,shape=RoundedCornerShape(23.dp),
        border=BorderStroke(1.dp,NeonGlass.outline)) {
        Column(Modifier.background(Brush.verticalGradient(listOf(
            Color(0xFF1B2229),Color(0xFF151B21)))).padding(17.dp),
            verticalArrangement=Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Icon(if(successful)AppIcons.Success else if(running)AppIcons.Waiting else AppIcons.Error,
                    null,tint=if(successful)NeonGlass.green else if(running)NeonGlass.softGreen else NeonGlass.warning,
                    modifier=Modifier.size(38.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(title,style=MaterialTheme.typography.titleMedium,
                        color=if(successful)NeonGlass.softGreen else NeonGlass.white,
                        fontWeight=FontWeight.Bold)
                    Text(subtitle,style=MaterialTheme.typography.bodySmall,
                        color=NeonGlass.muted,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
                Icon(AppIcons.Chevron,null,tint=NeonGlass.green)
            }
            HorizontalDivider(color=NeonGlass.outline)
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.History,null,tint=NeonGlass.softGreen,
                    modifier=Modifier.size(17.dp))
                Text(metadata,style=MaterialTheme.typography.bodySmall,
                    color=NeonGlass.muted,maxLines=2)
            }
        }
    }
}

@Composable fun NeonEnvironmentRow(title:String,version:String,icon:ImageVector,
    installed:Boolean,modifier:Modifier=Modifier) {
    Surface(modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),
        color=NeonGlass.surface,border=BorderStroke(1.dp,NeonGlass.outline)) {
        Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            NeonIconTile(icon,modifier=Modifier.size(48.dp),
                tint=if(installed)NeonGlass.green else NeonGlass.muted)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium,
                    color=NeonGlass.white,fontWeight=FontWeight.Bold)
                Text(version.ifEmpty {"Not checked"},
                    style=MaterialTheme.typography.bodySmall,
                    color=NeonGlass.muted,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Icon(if(installed)AppIcons.Success else AppIcons.Info,null,
                tint=if(installed)NeonGlass.green else NeonGlass.muted)
        }
    }
}
