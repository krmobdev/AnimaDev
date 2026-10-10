package com.animacode.animadev.projects

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Uses the tracked documentation included at build time — no live network required. */
@Composable fun SupportScreen(context:Context,screen:String) {
    val english=AppLanguage.english(LocalAppLanguage.current,LocalConfiguration.current.locales[0].language)
    val filename=when(screen) {
        "changelog"->"CHANGELOG.md"
        "termux_setup"->if(english)"TERMUX_SETUP_EN.md" else "TERMUX_SETUP.md"
        else->if(english)"USER_GUIDE_EN.md" else "USER_GUIDE.md"
    }
    val contents=remember(filename) {
        runCatching {context.assets.open("support/$filename").bufferedReader().use {it.readText()}}
            .getOrElse {if(english)"Documentation unavailable in this build." else "Документация отсутствует в этой сборке."}
    }
    if(screen=="about") {
        val version=remember(context.packageName) {
            runCatching {context.packageManager.getPackageInfo(context.packageName,0).versionName}.getOrNull().orEmpty()
        }
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {Text("AnimaDev",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)}
            item {Text(if(english)"Local Git and Android builds with Termux" else "Локальный Git и Android-сборки через Termux")}
            item {Text((if(english)"Version: " else "Версия: ")+version,style=MaterialTheme.typography.titleMedium)}
            item {Text("Android ${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}")}
            item {Text(if(english)
                "Kotlin / Jetpack Compose. Safe Git operations, tests, verified APK installation, signed AAB and persistent history."
                else "Kotlin / Jetpack Compose. Защищённые Git-операции, тесты, установка проверенных APK, подписанные AAB и история заданий.")}
            item {OutlinedButton(onClick={
                context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/krmobdev/AnimaDev")))
            },modifier=Modifier.fillMaxWidth()) {Text(if(english)"GitHub repository" else "Репозиторий GitHub")}}
            item {Text(if(english)
                "Changelog and user guides are bundled from GitHub and work offline. Installation and device testing require explicit user approval."
                else "История изменений и руководства включены из GitHub и доступны без сети. Установка и тестирование на устройстве требуют подтверждения пользователя.",
                style=MaterialTheme.typography.bodyMedium)}
        }
        return
    }
    val lines=remember(contents){contents.lines().filterNot {it.isBlank()}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        itemsIndexed(lines) {_,raw->
            val t=raw.trim()
            when {
                t.startsWith("# ")->Text(t.removePrefix("# "),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                t.startsWith("## ")->Text(t.removePrefix("## "),style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
                t.startsWith("### ")->Text(t.removePrefix("### "),style=MaterialTheme.typography.titleMedium)
                t=="---"->HorizontalDivider()
                t.startsWith("- ")->Text("• "+t.removePrefix("- "),style=MaterialTheme.typography.bodyMedium)
                t.startsWith("1. ") || t.startsWith("2. ") || t.startsWith("3. ") ||
                t.startsWith("4. ") || t.startsWith("5. ")->Text(t,style=MaterialTheme.typography.bodyMedium)
                t.startsWith("```")->Unit
                else->Text(t.replace("`",""),style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
