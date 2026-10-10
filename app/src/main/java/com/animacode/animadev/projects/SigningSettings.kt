package com.animacode.animadev.projects

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object SigningPolicy {
    // Legacy Termux paths remain readable for migration, but the UI uses Android's document picker.
    fun hasPath(path: String): Boolean {
        val root = path.startsWith(ProjectPolicy.HOME + "/") || path.startsWith("/storage/emulated/0/Download/")
        return root && !path.split('/').contains("..") && (path.endsWith(".jks",true) || path.endsWith(".keystore",true))
    }
    fun isKeyName(name: String): Boolean =
        name.length in 5..255 &&
            name.none { it == '/' || it == '\\' || it.code < 32 || it.code == 127 } &&
            (name.endsWith(".jks",true) || name.endsWith(".keystore",true))

    fun mayRelease(path: String, secureChannel: Boolean) = hasPath(path) && secureChannel
}

class SigningStore(private val context: Context) {
    private val prefs=context.getSharedPreferences("signing_paths_v1",Context.MODE_PRIVATE)
    fun path(id:String)=prefs.getString(id+".path","") ?: ""
    fun alias(id:String)=prefs.getString(id+".alias","") ?: ""
    fun documentUri(id:String)=prefs.getString(id+".uri","") ?: ""
    fun fileLabel(id:String):String =
        if(documentUri(id).isNotEmpty())prefs.getString(id+".name","").orEmpty() else ""

    fun hasDocument(id:String):Boolean {
        val raw=documentUri(id)
        if(raw.isEmpty() || !SigningPolicy.isKeyName(fileLabel(id)))return false
        return runCatching {
            val uri=Uri.parse(raw)
            uri.scheme=="content" && context.contentResolver.persistedUriPermissions.any {
                it.uri==uri && it.isReadPermission
            }
        }.getOrDefault(false)
    }

    // The document stays with its Android provider. Do not store, copy, or transmit key bytes.
    fun saveDocument(id:String,uri:Uri,name:String,alias:String):Boolean {
        if(id.isBlank() || uri.scheme!="content" || !SigningPolicy.isKeyName(name))return false
        return prefs.edit()
            .putString(id+".uri",uri.toString())
            .putString(id+".name",name)
            .remove(id+".path")
            .putString(id+".alias",alias.trim())
            .commit()
    }
    fun saveAlias(id:String,alias:String):Boolean {
        if(!hasDocument(id) || alias.isBlank())return false
        return prefs.edit().putString(id+".alias",alias.trim()).commit()
    }
    // Preserve old preferences during migration; no new UI asks users to enter a filesystem path.
    fun save(id:String,path:String,alias:String):Boolean {
        if(!SigningPolicy.hasPath(path))return false
        return prefs.edit().putString(id+".path",path.trim()).putString(id+".alias",alias.trim()).commit()
    }
}

@Composable fun SigningSettings(context:Context) {
    val store=remember {SigningStore(context)}
    val supported=remember {ProjectStore(context).all().filter {it.supported}}
    var currentId by remember {mutableStateOf(supported.firstOrNull()?.id ?: "")}
    var alias by remember(currentId) {mutableStateOf(store.alias(currentId))}
    var fileName by remember(currentId) {mutableStateOf(store.fileLabel(currentId))}
    var hasAccess by remember(currentId) {mutableStateOf(store.hasDocument(currentId))}
    var message by remember(currentId) {mutableStateOf("")}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->
        if(uri!=null) {
            val name=runCatching {
                context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor->
                    if(cursor.moveToFirst())cursor.getString(0) else null
                }.orEmpty()
            }.getOrDefault("")
            when {
                !SigningPolicy.isKeyName(name)->
                    message="Выберите файл с расширением .jks или .keystore."
                !runCatching {
                    context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.isSuccess->
                    message="Не удалось сохранить доступ к файлу. Выберите другой источник."
                !store.saveDocument(currentId,uri,name,alias)->
                    message="Не удалось сохранить выбранный файл."
                else->{
                    fileName=name
                    hasAccess=store.hasDocument(currentId)
                    message="Файл выбран. Укажите alias ключа и нажмите «Сохранить alias»."
                }
            }
        }
        // Canceling the Android picker never clears a previously selected key.
    }
    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Ключ подписи Release AAB",style=MaterialTheme.typography.titleMedium)
        if(supported.isEmpty()) Text("Нет поддерживаемых проектов.")
        supported.forEach {p->Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected=currentId==p.id,onClick={currentId=p.id})
            Text(p.name)
        }}
        Text("Файл ключа",style=MaterialTheme.typography.titleSmall)
        Text(
            when {
                fileName.isNotEmpty()->fileName
                store.path(currentId).isNotEmpty()->"Ранее сохранён путь: "+store.path(currentId)
                else->"Файл .jks или .keystore не выбран"
            },
            style=MaterialTheme.typography.bodyMedium
        )
        if(fileName.isNotEmpty() && !hasAccess)
            Text("Доступ к файлу утрачен. Выберите его повторно.",color=MaterialTheme.colorScheme.error)
        if(fileName.isEmpty() && store.path(currentId).isNotEmpty())
            Text("Для доступа через Android выберите этот ключ заново в проводнике.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick={picker.launch(arrayOf("*/*"))},
            enabled=currentId.isNotBlank(),
            modifier=Modifier.fillMaxWidth()
        ) {Text(if(fileName.isEmpty())"Выбрать файл ключа" else "Заменить файл ключа")}
        OutlinedTextField(alias,{alias=it},label={Text("Псевдоним ключа (alias)")},
            singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(
            onClick={message=if(store.saveAlias(currentId,alias))"Alias сохранён" else "Не удалось сохранить alias"},
            enabled=currentId.isNotBlank() && hasAccess && alias.isNotBlank()
        ) {Text("Сохранить alias")}
        if(message.isNotEmpty())Text(message)
        Text("Файл выбирается через системный проводник Android, без ручного ввода пути. Сам ключ не передаётся в Termux. Пароли здесь пока не принимаются: защищённый канал Android Keystore → Termux не готов, поэтому Release AAB заблокирован. Debug APK доступен без ключа.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
