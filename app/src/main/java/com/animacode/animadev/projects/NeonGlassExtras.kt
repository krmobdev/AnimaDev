package com.animacode.animadev.projects

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Emerald-only presentation building blocks. No repository or runner effects. */
/**
 * Tabs remain readable at 320dp and with accessibility font scaling.
 * Long filter sets scroll instead of forcing four cramped equal-width chips.
 */
@Composable fun NeonTabs(labels:List<String>,selected:Int,onSelect:(Int)->Unit,
                        modifier:Modifier=Modifier) {
    val scaled=LocalDensity.current.fontScale>=1.25f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scrollable=labels.size>3 && (maxWidth<470.dp || scaled)
        val scrollState=rememberScrollState()
        Surface(shape=RoundedCornerShape(21.dp),
            color=NeonGlass.surface,border=BorderStroke(1.dp,NeonGlass.outline)) {
            Row(Modifier.fillMaxWidth()
                .then(if(scrollable)Modifier.horizontalScroll(scrollState) else Modifier)
                .padding(5.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                labels.forEachIndexed {index,label->
                    val active=index==selected
                    val color by animateColorAsState(
                        targetValue=if(active)Color(0xFF194330) else Color.Transparent,
                        animationSpec=tween(160),label="Emerald tab background")
                    val tabModifier=if(scrollable)Modifier.width(112.dp) else Modifier.weight(1f)
                    Surface(onClick={onSelect(index)},
                        modifier=tabModifier.height(64.dp),
                        shape=RoundedCornerShape(17.dp),color=color,
                        border=if(active)BorderStroke(1.dp,
                            NeonGlass.green.copy(alpha=.36f)) else null) {
                        Box(contentAlignment=Alignment.Center,
                            modifier=Modifier.padding(horizontal=9.dp,vertical=9.dp)) {
                            Text(label,style=MaterialTheme.typography.labelLarge,
                                color=if(active)NeonGlass.softGreen else NeonGlass.muted,
                                fontWeight=if(active)FontWeight.Bold else FontWeight.Normal,
                                maxLines=3,overflow=TextOverflow.Ellipsis)
                        }
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

@Composable fun NeonSettingsRow(title:String,subtitle:String,icon:ImageVector,
                                onClick:()->Unit) {
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(19.dp),color=NeonGlass.surface,
        border=BorderStroke(1.dp,NeonGlass.outline)) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            NeonIconTile(icon,Modifier.size(53.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium,
                    fontWeight=FontWeight.Bold,color=NeonGlass.white,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=NeonGlass.muted,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Icon(AppIcons.Chevron,null,tint=NeonGlass.muted)
        }
    }
}

@Composable fun NeonAboutScreen(context:android.content.Context,version:String,english:Boolean) {
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=22.dp,end=22.dp,top=24.dp,bottom=40.dp),
        verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {
            Text(if(english)"About" else "О программе",
                style=MaterialTheme.typography.headlineLarge,
                fontWeight=FontWeight.Bold,color=NeonGlass.white)
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top=30.dp,bottom=20.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(15.dp)) {
                Text("</>",style=MaterialTheme.typography.displayLarge,
                    color=NeonGlass.green)
                Row(verticalAlignment=Alignment.Bottom,
                    horizontalArrangement=Arrangement.Center) {
                    Text("Anima",style=MaterialTheme.typography.displayMedium,
                        fontFamily=androidx.compose.ui.text.font.FontFamily.Serif,
                        color=NeonGlass.white)
                    Text("Dev",style=MaterialTheme.typography.displayMedium,
                        fontFamily=androidx.compose.ui.text.font.FontFamily.Serif,
                        color=NeonGlass.green)
                }
                Text(if(english)"Local Git and Android builds with Termux"
                    else "Локальный Git и Android-сборки через Termux",
                    style=MaterialTheme.typography.bodyLarge,color=NeonGlass.muted)
                NeonStatus("v"+version,true)
                Text("Android ${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}",
                    color=NeonGlass.muted,style=MaterialTheme.typography.bodyMedium)
            }
        }
        item {HorizontalDivider(color=NeonGlass.outline)}
        item {
            Text(if(english)
                "AnimaDev brings verified Git operations, Android tests, signed releases and local builds to your phone. Commands run through a restricted Termux integration; results and logs stay available in your project history."
                else "AnimaDev объединяет проверенные операции Git, тесты Android, подписанные релизы и локальные сборки на телефоне. Команды выполняются через ограниченную интеграцию Termux, а результаты и журналы доступны в истории.",
                color=NeonGlass.muted,style=MaterialTheme.typography.bodyLarge)
        }
        item {
            Surface(onClick={
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/krmobdev/AnimaDev")))
            },shape=RoundedCornerShape(19.dp),color=NeonGlass.surface,
                border=BorderStroke(1.dp,NeonGlass.green)) {
                Row(Modifier.fillMaxWidth().padding(19.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.SpaceBetween) {
                    Icon(AppIcons.Git,null,tint=NeonGlass.green)
                    Text(if(english)"GitHub repository" else "Репозиторий GitHub",
                        style=MaterialTheme.typography.titleMedium,
                        color=NeonGlass.softGreen,fontWeight=FontWeight.Bold)
                    Icon(AppIcons.Chevron,null,tint=NeonGlass.green)
                }
            }
        }
    }
}
