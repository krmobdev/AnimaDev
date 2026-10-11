package com.animacode.animadev.projects

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Small, collapsed-by-default entry point placed AFTER project tools.
 * The surrounding LazyColumn owns the expansion state and creates individual
 * lazy worktree rows only after an explicit user action.
 */
@Composable
fun EmeraldWorktreeOverview(
    root:LocalProject,
    relatedCount:Int,
    permitted:Boolean,
    pending:Boolean,
    scanned:Boolean,
    expanded:Boolean,
    onToggle:()->Unit,
    onRefresh:()->Unit
) {
    Surface(color=NeonGlass.surface,shape=RoundedCornerShape(21.dp),
        border=BorderStroke(1.dp,NeonGlass.outline),
        modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(AppIcons.Folder,null,tint=NeonGlass.green,
                    modifier=Modifier.size(25.dp))
                Column(Modifier.weight(1f),
                    verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text("Рабочие каталоги",style=MaterialTheme.typography.titleMedium,
                        fontWeight=FontWeight.Bold,color=NeonGlass.white)
                    Text(if(scanned)"Другие каталоги: $relatedCount"
                        else "Список ещё не проверен",
                        color=NeonGlass.muted,style=MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick=onRefresh,enabled=permitted && !pending,
                    modifier=Modifier.size(48.dp)) {
                    Icon(AppIcons.Refresh,"Обновить рабочие каталоги",
                        tint=if(permitted && !pending)NeonGlass.green else NeonGlass.muted)
                }
            }
            Text(
                "Сейчас: "+root.branch.ifBlank {"не определена"}+
                    " · "+WorktreeStatusText.describe(root.dirty,root.changesCount),
                style=MaterialTheme.typography.bodySmall,
                color=if(root.dirty==true)NeonGlass.warning else NeonGlass.muted,
                maxLines=3,overflow=TextOverflow.Ellipsis
            )
            Surface(onClick=onToggle,color=NeonGlass.raised,
                shape=RoundedCornerShape(15.dp),
                border=BorderStroke(1.dp,NeonGlass.outline),
                modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()
                    .heightIn(min=51.dp).padding(horizontal=14.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(if(expanded)"Скрыть список"
                        else "Показать каталоги ($relatedCount)",
                        style=MaterialTheme.typography.labelLarge,
                        color=NeonGlass.softGreen,fontWeight=FontWeight.Bold)
                    Icon(if(expanded)AppIcons.Back else AppIcons.Chevron,null,
                        tint=NeonGlass.softGreen)
                }
            }
            if(pending)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(expanded) Text(
                "Это локальные каталоги Termux. Их наличие не подтверждает "+
                    "отправку коммитов на GitHub. Нажмите на каталог для деталей.",
                style=MaterialTheme.typography.bodySmall,color=NeonGlass.muted
            )
        }
    }
}

/** A single lazy list item; no nested eager rendering of dozens of worktrees. */
@Composable
fun EmeraldWorktreeRow(project:LocalProject,onOpen:()->Unit) {
    Surface(onClick=onOpen,color=NeonGlass.surface,
        shape=RoundedCornerShape(16.dp),
        border=BorderStroke(1.dp,NeonGlass.outline),
        modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(AppIcons.Git,null,tint=NeonGlass.green,
                modifier=Modifier.size(23.dp))
            Column(Modifier.weight(1f),
                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(project.branch.ifBlank {"Ветка неизвестна"},
                    style=MaterialTheme.typography.titleSmall,
                    color=NeonGlass.white,fontWeight=FontWeight.SemiBold,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(project.path.removePrefix(ProjectPolicy.HOME+"/"),
                    style=MaterialTheme.typography.bodySmall,
                    color=NeonGlass.muted,maxLines=2,
                    overflow=TextOverflow.Ellipsis)
                Text(WorktreeStatusText.describe(project.dirty,project.changesCount),
                    style=MaterialTheme.typography.bodySmall,
                    color=if(project.dirty==true)NeonGlass.warning else NeonGlass.softGreen,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Icon(AppIcons.Chevron,"Подробности",tint=NeonGlass.green,
                modifier=Modifier.size(23.dp))
        }
    }
}

@Composable
fun EmeraldWorktreeDetails(context:Context,project:LocalProject,onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,
        title={Text(project.branch.ifBlank {"Рабочий каталог"})},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text("Каталог: "+project.path)
                Text("Локальный SHA: "+project.commit.ifBlank {"Не проверен"})
                Text(WorktreeStatusText.describe(project.dirty,project.changesCount))
                Text("Отправка в GitHub здесь не проверяется. "+
                    "Другие рабочие каталоги не переключаются и не изменяются автоматически.")
            }
        },
        confirmButton={TextButton(onClick={
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Termux worktree",project.path))
            onDismiss()
        }) {Text("Копировать путь")}},
        dismissButton={TextButton(onClick=onDismiss) {Text("Закрыть")}}
    )
}
