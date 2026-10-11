package com.animacode.animadev.projects

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun EmeraldWorktreeOverview(
    context:Context,root:LocalProject,related:List<LocalProject>,
    permitted:Boolean,pending:Boolean,scanned:Boolean,onRefresh:()->Unit
) {
    var selected by remember {mutableStateOf<LocalProject?>(null)}
    NeonPanel(Modifier.fillMaxWidth()) {
        NeonSection("Рабочие каталоги",AppIcons.Folder,
            "Ветки одного репозитория могут находиться в разных каталогах Termux")
        Text("СЕЙЧАС ОТКРЫТ",color=NeonGlass.softGreen,
            style=MaterialTheme.typography.labelMedium)
        Text(root.path.removePrefix(ProjectPolicy.HOME+"/"),
            color=NeonGlass.white,style=MaterialTheme.typography.bodyMedium)
        Text("Ветка: "+root.branch.ifBlank {"Не определена"}+
            " · SHA "+root.commit.take(8).ifBlank {"не проверен"},
            color=NeonGlass.muted,style=MaterialTheme.typography.bodySmall)
        Text(WorktreeStatusText.describe(root.dirty,root.changesCount),
            color=if(root.dirty==true)MaterialTheme.colorScheme.error else NeonGlass.softGreen,
            style=MaterialTheme.typography.bodySmall)
        HorizontalDivider(color=NeonGlass.outline)
        Text("Другие рабочие каталоги: ${related.size}",
            color=NeonGlass.white,style=MaterialTheme.typography.titleMedium,
            fontWeight=FontWeight.Bold)
        if(related.isEmpty()) Text(if(scanned)
            "Других каталогов не обнаружено в последнем поиске."
            else "Поиск ещё не выполнялся. Обновите список.",
            color=NeonGlass.muted,style=MaterialTheme.typography.bodySmall)
        related.forEach {wt->
            Surface(onClick={selected=wt},color=NeonGlass.raised,
                shape=RoundedCornerShape(16.dp),
                border=BorderStroke(1.dp,NeonGlass.outline)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(AppIcons.Git,null,tint=NeonGlass.green)
                        Text(wt.branch.ifBlank {"Неизвестная ветка"},
                            modifier=Modifier.weight(1f),maxLines=3,
                            overflow=TextOverflow.Ellipsis,
                            color=NeonGlass.white,fontWeight=FontWeight.Bold)
                        Icon(AppIcons.Chevron,"Подробнее",tint=NeonGlass.green)
                    }
                    Text(wt.path.removePrefix(ProjectPolicy.HOME+"/"),
                        color=NeonGlass.muted,style=MaterialTheme.typography.bodySmall,
                        maxLines=3,overflow=TextOverflow.Ellipsis)
                    Text(WorktreeStatusText.describe(wt.dirty,wt.changesCount),
                        color=if(wt.dirty==true)MaterialTheme.colorScheme.error
                            else NeonGlass.softGreen)
                    Text("SHA "+wt.commit.take(12)+" · GitHub здесь не проверялся",
                        color=NeonGlass.muted,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Список отражает локальные файлы. Совпадение коммитов в другом каталоге не подтверждает отправку этой ветки на GitHub.",
            color=NeonGlass.muted,style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick=onRefresh,enabled=permitted && !pending,
            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
            Icon(AppIcons.Refresh,null)
            Spacer(Modifier.width(8.dp))
            Text(if(pending)"Читаем Termux…" else "Обновить рабочие каталоги")
        }
        if(pending)LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    selected?.let {wt->
        AlertDialog(onDismissRequest={selected=null},
            title={Text(wt.branch.ifBlank {"Рабочий каталог"})},
            text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text("Каталог: "+wt.path)
                Text("Локальный SHA: "+wt.commit.ifBlank {"Не проверен"})
                Text(WorktreeStatusText.describe(wt.dirty,wt.changesCount))
                Text("Отправка в GitHub не проверена. Этот worktree не является текущим main. Файлы не удаляются и ветка не переключается.")
            }},
            confirmButton={TextButton(onClick={
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Termux worktree",wt.path))
                selected=null
            }) {Text("Копировать путь")}},
            dismissButton={TextButton(onClick={selected=null}) {Text("Закрыть")}})
    }
}
