package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object SigningPolicy {
    fun hasPath(path: String): Boolean {
        val root = path.startsWith(ProjectPolicy.HOME + "/") || path.startsWith("/storage/emulated/0/Download/")
        return root && !path.split('/').contains("..") && (path.endsWith(".jks",true) || path.endsWith(".keystore",true))
    }
    fun mayRelease(path: String, secureChannel: Boolean) = hasPath(path) && secureChannel
}
class SigningStore(context: Context) {
    private val prefs=context.getSharedPreferences("signing_paths_v1",Context.MODE_PRIVATE)
    fun path(id:String)=prefs.getString(id+".path","") ?: ""
    fun alias(id:String)=prefs.getString(id+".alias","") ?: ""
    fun save(id:String,path:String,alias:String):Boolean {
        if(!SigningPolicy.hasPath(path))return false
        return prefs.edit().putString(id+".path",path.trim()).putString(id+".alias",alias.trim()).commit()
    }
}
@Composable fun SigningSettings(context:Context) {
    val store=remember {SigningStore(context)}
    val supported=remember {ProjectStore(context).all().filter {it.supported}}
    var currentId by remember {mutableStateOf(supported.firstOrNull()?.id ?: "")}
    var path by remember(currentId) {mutableStateOf(store.path(currentId))}
    var alias by remember(currentId) {mutableStateOf(store.alias(currentId))}
    var message by remember {mutableStateOf("")}
    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Ключ подписи Release AAB",style=MaterialTheme.typography.titleMedium)
        supported.forEach {p->Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected=currentId==p.id,onClick={currentId=p.id;message=""})
            Text(p.name)
        }}
        OutlinedTextField(path,{path=it},label={Text("Полный путь к .jks или .keystore")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(alias,{alias=it},label={Text("Псевдоним ключа (alias)")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={message=if(store.save(currentId,path,alias))"Путь и alias сохранены" else "Недопустимый путь к файлу"},
            enabled=currentId.isNotBlank() && path.isNotBlank()) { Text("Сохранить путь и alias") }
        if(message.isNotEmpty())Text(message)
        Text("Пароли здесь пока не принимаются: проверка безопасного канала Android Keystore → Termux не завершена. Release AAB заблокирован. Debug APK доступен без ключа.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
