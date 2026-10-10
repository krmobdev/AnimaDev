package com.animacode.animadev.projects

/** Search is an index of existing preferences and honest, read-only information. */
data class SettingEntry(val section:String,val id:String,val title:String,val description:String)
object SettingsPolicy {
    val entries=listOf(
        SettingEntry("appearance","theme","Тема","Системная, светлая или тёмная"),
        SettingEntry("appearance","dynamic","Динамические цвета","Палитра обоев Android"),
        SettingEntry("git","direction","Получение изменений","GitHub → Termux, только безопасный fast-forward"),
        SettingEntry("git","projects","Локальные проекты","Репозитории и worktree, без удаления файлов"),
        SettingEntry("profiles","test","Unit-тесты Debug","AnimaQuiz, test/decomposition-v1, офлайн Gradle"),
        SettingEntry("profiles","build","Профили сборки","Debug APK после проверки GitHub"),
        SettingEntry("signing","keystore","Выбор файла ключа","Системный проводник Android · .jks или .keystore"),
        SettingEntry("signing","alias","Псевдоним ключа","Alias выбранного ключа подписи"),
        SettingEntry("jobs","warning","Предупреждение об отсутствии прогресса","5, 10, 15, 20 или 30 минут; не останавливает задание"),
        SettingEntry("jobs","limit","Защитный предел TEST","90 минут; при неопределённом результате повтор заблокирован"),
        SettingEntry("notifications","permission","Разрешение уведомлений","Управляется Android; отказ не блокирует задания"),
        SettingEntry("files","folder","Папка сохранения","Отчёты и будущие артефакты; по умолчанию Загрузки"),
        SettingEntry("files","default","Использовать Загрузки","Вернуться к системной папке, не удаляя файлы"),
        SettingEntry("about","version","Версия AnimaDev","Версия приложения и Android"),
        SettingEntry("about","diagnostics","Диагностика","Связь Termux и очищенные журналы заданий")
    )
    fun search(query:String)=query.trim().takeIf {it.isNotEmpty()}?.let {q->entries.filter {(it.title+" "+it.description).contains(q,true)}} ?: emptyList()
    fun toolbarTitle(target:String)=when(section(target)) {
        "appearance"->"Внешний вид";"git"->"Проекты и Git";"profiles"->"Профили";"signing"->"Подпись AAB";"jobs"->"Ожидание заданий";"notifications"->"Уведомления";"files"->"Файлы и загрузки";"about"->"О приложении";else->"Настройки"
    }
    fun section(target:String)=target.substringBefore('/')
    fun index(target:String)=entries.filter {it.section==section(target)}.indexOfFirst {it.id==target.substringAfter('/',"")}.coerceAtLeast(0)
}
