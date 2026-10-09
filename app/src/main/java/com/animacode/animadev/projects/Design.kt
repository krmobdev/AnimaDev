package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.animacode.animadev.repos.MonitorSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun SectionLabel(text:String) { Text(text,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=8.dp,bottom=4.dp)) }
@Composable fun DetailRow(label:String,value:String) {
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun AppearanceSettings(context:Context,theme:String,dynamic:Boolean,onTheme:(String)->Unit,onDynamic:(Boolean)->Unit) {
    var showCommand by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {SectionLabel("Оформление")}
        item { Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) { Column(Modifier.padding(12.dp)) {
            listOf("system" to "Как в системе","light" to "Светлая","dark" to "Тёмная").forEach { (id,label)->SingleChoiceRow(label,theme==id){onTheme(id)} }
            HorizontalDivider(Modifier.padding(vertical=8.dp))
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Text("Динамические цвета",Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge);Switch(dynamic,onDynamic) }
        } } }
        item { SectionLabel("Задания и тесты") }
        item { Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Профиль AnimaQuiz",style=MaterialTheme.typography.titleMedium)
            Text("Команда тестирования задана приложением. Отсюда её можно посмотреть, но не изменить.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick={showCommand=!showCommand},modifier=Modifier.fillMaxWidth()) {Text(if(showCommand)"Скрыть команду" else "Показать команду запуска")}
            if(showCommand)Text(":app:testDebugUnitTest\n--offline --no-daemon --rerun-tasks --console=plain",style=MaterialTheme.typography.bodySmall)
        } } }
        item { MonitorSettings(context,embedded=true) }
        item {Text("AnimaDev 0.4.0-beta.4",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
