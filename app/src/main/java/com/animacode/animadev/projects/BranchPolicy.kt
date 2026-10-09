package com.animacode.animadev.projects

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BranchRef(val name:String,val sha:String,val date:String,val local:Boolean,val active:Boolean=false,val default:Boolean=false)
data class BranchRow(val name:String,val local:BranchRef?,val remote:BranchRef?) {
    val active get()=local?.active==true
    val default get()=local?.default==true || remote?.default==true
    val comparison get()=when {local==null->"Только origin";remote==null->"Только локальная";local.sha==remote.sha->"SHA совпадают в локальном снимке";else->"SHA различаются · требуется проверка обновлений"}
}
object BranchPolicy {
    fun workingTree(dirty:Boolean?,metadataError:Boolean=false):String=when {
        metadataError || dirty==null->"Не подтверждено"
        dirty->"Есть локальные изменения"
        else->"Чисто"
    }
    fun merge(refs:List<BranchRef>,query:String=""):List<BranchRow> = refs.groupBy {it.name}.map {(name,values)->BranchRow(name,values.lastOrNull {it.local},values.lastOrNull {!it.local})}.filter {it.name.contains(query.trim(),true)}.sortedWith(compareBy<BranchRow> {!it.default}.thenBy {!it.active}.thenBy {it.name})
    fun date(value:String,zone:ZoneId=ZoneId.systemDefault()):String=try {OffsetDateTime.parse(value).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm",Locale.getDefault()))} catch(_:Exception){"Дата не подтверждена"}
}
