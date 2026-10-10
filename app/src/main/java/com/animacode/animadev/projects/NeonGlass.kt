package com.animacode.animadev.projects

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Emerald Glass — independent presentation only. Never owns repository state,
 * runner calls, credentials, file IO or job lifecycle.
 *
 * All artwork (glows, folder marks and icon tiles) is drawn natively in Compose.
 * No bitmap assets, heavy runtime blur or remote images are required.
 */
val LocalNeonSkin=compositionLocalOf { false }

object NeonGlass {
    val background=Color(0xFF080B0E)
    val surface=Color(0xFF151B21)
    val raised=Color(0xFF1D252C)
    val green=Color(0xFF46D779)
    val softGreen=Color(0xFF83E69C)
    val muted=Color(0xFFABB3B9)
    val outline=Color(0xFF354047)
    val warning=Color(0xFFFFA55D)
    val white=Color(0xFFF6F9F7)
    val gradient=Brush.linearGradient(listOf(Color(0xFF263B32),Color(0xFF1B2226),Color(0xFF111619)))
    val active=Brush.horizontalGradient(listOf(Color(0xFF153D2B),Color(0xFF164B31)))
}

fun neonColorScheme():ColorScheme=darkColorScheme(
    primary=NeonGlass.green,onPrimary=Color(0xFF04140A),
    primaryContainer=Color(0xFF143927),onPrimaryContainer=NeonGlass.softGreen,
    secondary=NeonGlass.softGreen,onSecondary=Color(0xFF082013),
    secondaryContainer=Color(0xFF172E26),onSecondaryContainer=NeonGlass.white,
    background=NeonGlass.background,onBackground=NeonGlass.white,
    surface=NeonGlass.surface,onSurface=NeonGlass.white,
    surfaceVariant=NeonGlass.raised,onSurfaceVariant=NeonGlass.muted,
    surfaceContainerLowest=NeonGlass.background,
    surfaceContainerLow=Color(0xFF10161B),
    surfaceContainer=NeonGlass.surface,surfaceContainerHigh=NeonGlass.raised,
    surfaceContainerHighest=Color(0xFF29323B),
    outline=NeonGlass.outline,outlineVariant=Color(0xFF2B353B),
    error=Color(0xFFFF8883),onError=Color(0xFF250D0C)
)

@Composable fun NeonPanel(
    modifier:Modifier=Modifier, glowing:Boolean=false,
    content:@Composable ColumnScope.()->Unit
) {
    val shape=RoundedCornerShape(27.dp)
    Surface(modifier=modifier,shape=shape,
        border=BorderStroke(1.dp,if(glowing)NeonGlass.green.copy(alpha=.34f) else NeonGlass.outline),
        color=NeonGlass.surface,contentColor=NeonGlass.white) {
        Box(Modifier.background(if(glowing)NeonGlass.gradient else
            Brush.verticalGradient(listOf(Color(0xFF1C242B),Color(0xFF12181D))))) {
            Column(Modifier.padding(19.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        }
    }
}

@Composable fun NeonIconTile(icon:ImageVector,modifier:Modifier=Modifier,tint:Color=NeonGlass.softGreen) {
    Box(modifier.size(58.dp).clip(RoundedCornerShape(17.dp)).background(
        Brush.verticalGradient(listOf(Color(0xFF27543A),Color(0xFF173026)))),
        contentAlignment=Alignment.Center) {
        Icon(icon,null,tint=tint,modifier=Modifier.size(32.dp))
    }
}

@Composable fun NeonStatus(label:String,positive:Boolean,modifier:Modifier=Modifier) {
    Surface(modifier=modifier,shape=RoundedCornerShape(20.dp),
        color=if(positive)Color(0xFF143025) else Color(0xFF39291E),
        border=BorderStroke(1.dp,if(positive)NeonGlass.green.copy(alpha=.32f) else NeonGlass.warning.copy(alpha=.45f))) {
        Row(Modifier.padding(horizontal=11.dp,vertical=7.dp),
            horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(
                if(positive)NeonGlass.green else NeonGlass.warning))
            Text(label,color=if(positive)NeonGlass.softGreen else NeonGlass.warning,
                style=MaterialTheme.typography.labelMedium,maxLines=2)
        }
    }
}

@Composable fun NeonProjectCard(
    project:LocalProject,branch:String,dirty:Boolean?,running:Boolean,onClick:()->Unit,
    onMore:()->Unit
) {
    val positive=dirty==false && project.error.isEmpty()
    val status=when {
        running->"Running"
        project.error.isNotBlank()->"Needs check"
        project.worktree->"Read-only"
        dirty==true->"Local changes"
        dirty==false->"Clean"
        else->"Not verified"
    }
    NeonPanel(Modifier.fillMaxWidth().clickable(onClick=onClick),glowing=positive) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            NeonIconTile(AppIcons.Folder,Modifier.size(64.dp),
                if(positive)NeonGlass.softGreen else NeonGlass.muted)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text(project.name,style=MaterialTheme.typography.titleLarge,
                    fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Row(verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Icon(AppIcons.Git,null,tint=NeonGlass.green,modifier=Modifier.size(17.dp))
                    Text(branch.ifBlank {"Branch unknown"},color=NeonGlass.muted,
                        style=MaterialTheme.typography.bodyMedium,
                        maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween,
            modifier=Modifier.fillMaxWidth()) {
            NeonStatus(status,positive && !running)
            IconButton(onClick=onMore,modifier=Modifier.size(42.dp)) {
                Icon(AppIcons.More,"Project actions",tint=NeonGlass.muted)
            }
        }
        HorizontalDivider(color=NeonGlass.outline)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Icon(AppIcons.History,null,tint=NeonGlass.muted,modifier=Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(project.commit.take(12).ifBlank {"SHA not checked"},
                color=NeonGlass.muted,style=MaterialTheme.typography.bodySmall,
                modifier=Modifier.weight(1f))
            Icon(AppIcons.Chevron,null,tint=NeonGlass.muted)
        }
    }
}

@Composable fun NeonToolCard(
    title:String,subtitle:String,icon:ImageVector,modifier:Modifier=Modifier,onClick:()->Unit
) {
    Surface(onClick=onClick,modifier=modifier.heightIn(min=152.dp),
        color=NeonGlass.surface,shape=RoundedCornerShape(22.dp),
        border=BorderStroke(1.dp,NeonGlass.outline)) {
        Column(Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            NeonIconTile(icon,Modifier.size(53.dp))
            Text(title,style=MaterialTheme.typography.titleMedium,
                fontWeight=FontWeight.Bold,color=NeonGlass.white)
            Text(subtitle,color=NeonGlass.muted,style=MaterialTheme.typography.bodySmall,
                maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable fun NeonPrimaryAction(
    title:String,subtitle:String="",icon:ImageVector=AppIcons.Build,
    enabled:Boolean=true,modifier:Modifier=Modifier,onClick:()->Unit
) {
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=76.dp),
        shape=RoundedCornerShape(20.dp),color=Color.Transparent) {
        Row(Modifier.fillMaxWidth().background(
            if(enabled)Brush.horizontalGradient(listOf(Color(0xFF115734),Color(0xFF2BB662)))
            else Brush.horizontalGradient(listOf(NeonGlass.raised,NeonGlass.surface)))
            .padding(horizontal=18.dp,vertical=14.dp),
            horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,tint=NeonGlass.white,modifier=Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,
                    color=NeonGlass.white)
                if(subtitle.isNotBlank()) Text(subtitle,style=MaterialTheme.typography.bodySmall,
                    color=NeonGlass.white.copy(alpha=.78f),maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Icon(AppIcons.Chevron,null,tint=NeonGlass.white)
        }
    }
}

/**
 * Root navigation adapts when font scaling or the display width leaves too
 * little room for icon+label. Color changes animate on the render thread;
 * there is no blur, infinite transition or background polling.
 */
@Composable fun NeonBottomBar(onProjects:()->Unit,onTermux:()->Unit,projectsSelected:Boolean) {
    val narrow=LocalConfiguration.current.screenWidthDp<360 ||
        LocalDensity.current.fontScale>=1.25f
    Surface(color=NeonGlass.background,tonalElevation=0.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("Projects",AppIcons.Folder,onProjects),
                Triple("Termux",AppIcons.Terminal,onTermux)
            ).forEachIndexed {index,(title,icon,handler)->
                val active=(index==0)==projectsSelected
                val tile by animateColorAsState(
                    targetValue=if(active)Color(0xFF143326) else NeonGlass.raised,
                    animationSpec=tween(160),label="Emerald nav tile")
                val labelColor by animateColorAsState(
                    targetValue=if(active)NeonGlass.softGreen else NeonGlass.muted,
                    animationSpec=tween(160),label="Emerald nav label")
                Surface(onClick=handler,modifier=Modifier.weight(1f)
                    .heightIn(min=if(narrow)68.dp else 62.dp),
                    color=tile,shape=RoundedCornerShape(21.dp),
                    border=BorderStroke(1.dp,
                        if(active)NeonGlass.green.copy(alpha=.4f) else NeonGlass.outline)) {
                    if(narrow) {
                        Column(Modifier.fillMaxWidth().padding(vertical=7.dp,horizontal=4.dp),
                            horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.Center) {
                            Icon(icon,null,tint=labelColor,modifier=Modifier.size(22.dp))
                            Text(title,color=labelColor,style=MaterialTheme.typography.labelMedium,
                                maxLines=1,overflow=TextOverflow.Ellipsis,
                                fontWeight=if(active)FontWeight.Bold else FontWeight.Medium)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),
                            verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.Center) {
                            Icon(icon,null,tint=labelColor,modifier=Modifier.size(23.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(title,color=labelColor,style=MaterialTheme.typography.titleMedium,
                                maxLines=1,overflow=TextOverflow.Ellipsis,
                                fontWeight=if(active)FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}
