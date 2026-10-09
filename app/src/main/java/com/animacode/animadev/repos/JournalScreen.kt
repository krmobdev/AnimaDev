package com.animacode.animadev.repos

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.animacode.animadev.projects.AppIcons

@OptIn(ExperimentalLayoutApi::class,ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(context: Context, requestedJob: String = "") {
    val store=remember { RepoStore(context) }; val gateway=remember { RepoGateway(context) }
    val cache=remember { JournalCache(context) }; val prefs=remember { context.getSharedPreferences("monitor_settings",Context.MODE_PRIVATE) }
    val scope=rememberCoroutineScope()
    var record by remember { mutableStateOf(store.read()) }
    var jobs by remember { mutableStateOf(emptyList<JSONObject>()) }
    var jobId by rememberSaveable { mutableStateOf(requestedJob.ifEmpty { prefs.getString("journal_job",store.read().optString("job_id")) ?: "" }) }
    var view by rememberSaveable { mutableStateOf(prefs.getString("journal_view","IMPORTANT") ?: "IMPORTANT") }
    var search by rememberSaveable { mutableStateOf("") }
    var text by remember { mutableStateOf("") }; var notice by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") };var menu by remember { mutableStateOf(false) };var showSearch by rememberSaveable { mutableStateOf(false) }
    var loadAll by rememberSaveable { mutableStateOf(true) }
    var historyLoading by remember { mutableStateOf(false) }
    val scroll=remember(jobId,view) { LazyListState() }
    val savedPosition=remember(jobId,view) { prefs.getInt("scroll_${jobId}_$view",0) }
    var positionRestored by remember(jobId,view) { mutableStateOf(false) }
    var contentLoaded by remember(jobId,view) { mutableStateOf(false) }
    LaunchedEffect(scroll,jobId,view) {
        snapshotFlow { scroll.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
            if(positionRestored)prefs.edit().putInt("scroll_${jobId}_$view",index).apply()
        }
    }
    var displayed by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(text,search) { displayed=withContext(Dispatchers.Default) {
        val filtered=if(search.isEmpty())text else text.lineSequence().filter { it.contains(search,ignoreCase=true) }.joinToString("\n")
        filtered.ifEmpty { if(search.isNotEmpty())"Совпадений нет" else "Журнал ещё не загружен" }.chunked(3000)
    } }
    LaunchedEffect(displayed,jobId,view,contentLoaded) {
        if(!positionRestored && contentLoaded && displayed.isNotEmpty()) {
            scroll.scrollToItem(minOf(savedPosition,maxOf(0,displayed.size-1)));positionRestored=true
        }
    }
    LaunchedEffect(jobId) {
        while(true) {
            delay(5000)
            val r=store.read()
            if(jobId==r.optString("job_id") && r.optString("state")=="RUNNING" && !r.optBoolean("pending") && !loadAll && !historyLoading)loadAll=true
        }
    }
    DisposableEffect(Unit) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_ -> record=store.read() }
        store.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.prefs.unregisterOnSharedPreferenceChangeListener(listener); cache.close() }
    }
    LaunchedEffect(requestedJob) { if(requestedJob.isNotEmpty())jobId=requestedJob }
    LaunchedEffect(jobId,view,record.optLong("journal_generation")) {
        prefs.edit().putString("journal_job",jobId).putString("journal_view",view).apply()
        withContext(Dispatchers.IO) {
            val history=cache.jobs(); var content=cache.text(jobId,view); var n=cache.notice(jobId,view)
            if(cache.cursor(jobId,view)==0L && record.optString("runner_version") != RepoGateway.VERSION) {
                val legacy=listOfNotNull(record.optJSONObject("job"),record.optJSONObject("test_job")).find { it.optString("job_id")==jobId }
                if(legacy!=null) {
                    n="Старый runner сохранил только ограниченный фрагмент. Полного вывода этого задания не существует."
                    content="AnimaQuiz · ${legacy.optString("action")}\nJob ID: $jobId\n${legacy.optString("state")} · exit=${legacy.optString("exit_code")}\nJUnit: ${legacy.optJSONObject("tests") ?: "—"}\n"+
                        RepoResultReceiver.redact(legacy.optString("stdout"))+"\n"+RepoResultReceiver.redact(legacy.optString("stderr"))
                }
            }
            withContext(Dispatchers.Main) { jobs=history; text=content;notice=n;contentLoaded=true }
        }
    }
    fun actionForJob(): String = jobs.find { it.optString("job_id")==jobId }?.optString("action")
        ?: if(jobId==record.optString("job_id"))record.optString("action","STATUS") else "STATUS"
    LaunchedEffect(jobId,view,loadAll,historyLoading) {
        var historyOffset=0L
        while(true) {
            delay(500)
            val r=store.read()
            if(r.optBoolean("pending"))continue
            if(historyLoading) {
                if(historyOffset>0 && !r.optBoolean("journal_more")) { historyLoading=false;continue }
                val id=jobId.ifEmpty { "00000000000000000000000000000000" }
                gateway.journal("HISTORY",id,actionForJob(),historyOffset,view)
                if(store.read().optBoolean("pending")) {
                    while(store.read().optBoolean("pending")) { delay(500);store.expire() }
                    val done=store.read()
                    if(done.optString("journal_message")=="Журнал получен") historyOffset=done.optLong("history_next")
                    else { historyLoading=false;message="История пока недоступна. Сохранённые журналы доступны." }
                } else { historyLoading=false }
            } else if(loadAll && jobId.isNotEmpty()) {
                val offset=withContext(Dispatchers.IO) { cache.cursor(jobId,view) }
                gateway.journal("LOG",jobId,actionForJob(),offset,view)
                if(!store.read().optBoolean("pending")) { loadAll=false;message="Обновите локального помощника для чтения журнала.";continue }
                while(store.read().optBoolean("pending")) { delay(500);store.expire() }
                val done=store.read()
                if(!done.optBoolean("journal_more") || done.optString("journal_message")!="Журнал получен")loadAll=false
            }
        }
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if(uri!=null) scope.launch { message=withContext(Dispatchers.IO) { try { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("storage"); "Отчёт сохранён" } catch(_:Exception) { "Не удалось сохранить отчёт" } } }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("${actionForJob()} · ${jobId.take(8)}",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                IconButton(onClick={showSearch=!showSearch;if(!showSearch)search=""}) {Icon(AppIcons.Search,"Поиск в журнале")}
                IconButton(onClick={loadAll=true},enabled=!loadAll && jobId.isNotEmpty()){Icon(AppIcons.Refresh,"Обновить журнал")}
            IconButton(onClick={scope.launch {
                try { val intent=withContext(Dispatchers.IO) { ReportSharing.prepare(context,jobId,view,notice+"\n"+text) };context.startActivity(Intent.createChooser(intent,"Поделиться диагностикой")) }
                catch(_:Exception) { message="Не удалось подготовить отчёт" }
            }},enabled=text.isNotEmpty() && jobId.matches(Regex("[a-f0-9]{32}"))) { Icon(AppIcons.Share,"Поделиться диагностикой") }
                Box {
                    IconButton(onClick={menu=true}) {Icon(AppIcons.More,"Действия с журналом")}
                    DropdownMenu(menu,{menu=false}) {
                        DropdownMenuItem(text={Text("Копировать доступный журнал")},leadingIcon={Icon(AppIcons.Copy,null)},enabled=text.isNotEmpty(),onClick={menu=false;context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AnimaDev",notice+"\n"+text));message="Журнал скопирован"})
            DropdownMenuItem(text={Text("Сохранить в Загрузки")},leadingIcon={Icon(AppIcons.Download,null)},onClick={
                menu=false;
                if(Build.VERSION.SDK_INT<29)export.launch("AnimaDev_${jobId.take(8)}_${view}.txt")
                else scope.launch { message=withContext(Dispatchers.IO) {
                    var uri:android.net.Uri?=null
                    try {
                        val values=ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME,"AnimaDev_${jobId.take(8)}_${view}_${System.currentTimeMillis()}.txt");put(MediaStore.Downloads.MIME_TYPE,"text/plain");put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS);put(MediaStore.Downloads.IS_PENDING,1) }
                        uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: error("storage")
                        context.contentResolver.openOutputStream(uri!!)?.use { it.write(text.toByteArray()) } ?: error("storage")
                        context.contentResolver.update(uri!!,ContentValues().apply { put(MediaStore.Downloads.IS_PENDING,0) },null,null)
                        "Отчёт сохранён в Загрузки"
                    } catch(_:Exception) { uri?.let { context.contentResolver.delete(it,null,null) };"Не удалось сохранить отчёт" }
                } }
            },enabled=text.isNotEmpty())
                    }
                }
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=view=="IMPORTANT",onClick={view="IMPORTANT";loadAll=true},label={Text("Важное")})
                FilterChip(selected=view=="ALL",onClick={view="ALL";loadAll=true},label={Text("Всё")})
            }
            if(showSearch)OutlinedTextField(search,{search=it},label={Text("Поиск по доступному журналу")},singleLine=true,modifier=Modifier.fillMaxWidth())
            if(notice.isNotEmpty())Text(notice,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            if(message.isNotEmpty())Text(message,style=MaterialTheme.typography.bodySmall)
        }
        if(loadAll || historyLoading)LinearProgressIndicator(Modifier.fillMaxWidth()) else HorizontalDivider()
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),state=scroll,contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(displayed) {chunk->SelectionContainer {Text(chunk,modifier=Modifier.fillMaxWidth(),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall,softWrap=true)} }
            }
    val thumb=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.55f)
    Canvas(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(5.dp)) {
        val count=scroll.layoutInfo.totalItemsCount
        if(count>1) {
            val visible=maxOf(1,scroll.layoutInfo.visibleItemsInfo.size)
            val height=maxOf(36.dp.toPx(),size.height*visible/count).coerceAtMost(size.height)
            val fraction=scroll.firstVisibleItemIndex.toFloat()/maxOf(1,count-visible)
            drawRoundRect(thumb,Offset(0f,(size.height-height)*fraction.coerceIn(0f,1f)),Size(size.width,height),CornerRadius(size.width/2))
        }
    }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(if(loadAll)"Читаем сохранённый журнал…" else "Удерживайте текст для выделения",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick={scope.launch {scroll.scrollToItem(0)}}) {Icon(AppIcons.Top,"В начало журнала")}
        IconButton(onClick={scope.launch {scroll.scrollToItem(maxOf(0,scroll.layoutInfo.totalItemsCount-1))}}) {Icon(AppIcons.Bottom,"В конец журнала")}
    }
    }
}
