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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.animacode.animadev.projects.AppIcons

@Composable
private fun HighlightedLogChunk(chunk:String) {
    val scheme=MaterialTheme.colorScheme
    val light=scheme.surface.luminance()>0.5f
    val success=if(light)Color(0xFF257A3B) else Color(0xFF81C784)
    val warning=if(light)Color(0xFF996000) else Color(0xFFFFD54F)
    val annotated=remember(chunk,scheme) {
        buildAnnotatedString {
            append(chunk)
            JournalStylePolicy.styles(chunk).forEach { line ->
                val color=when(line.tone) { LogTone.SUCCESS->success;LogTone.ERROR->scheme.error;LogTone.WARNING->warning;else->scheme.onSurface }
                if(line.tone!=LogTone.NORMAL)addStyle(SpanStyle(color=color,fontWeight=FontWeight.SemiBold),line.start,line.end)
                if(line.timestampEnd>line.start)addStyle(SpanStyle(color=if(light)Color(0xFF161616) else Color(0xFFF5F5F5),fontWeight=FontWeight.Bold),line.start,line.timestampEnd)
            }
        }
    }
    SelectionContainer { Text(annotated,modifier=Modifier.fillMaxWidth(),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall,softWrap=true) }
}

@OptIn(ExperimentalLayoutApi::class,ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(context: Context, requestedJob: String = "") {
    val store=remember { RepoStore(context) }; val gateway=remember { RepoGateway(context) }
    val cache=remember { JournalCache(context) }; val prefs=remember { context.getSharedPreferences("monitor_settings",Context.MODE_PRIVATE) }
    val scope=rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var record by remember { mutableStateOf(store.read()) }
    var jobs by remember { mutableStateOf(emptyList<JSONObject>()) }
    var jobId by rememberSaveable { mutableStateOf(requestedJob.ifEmpty { prefs.getString("journal_job",store.read().optString("job_id")) ?: "" }) }
    var view by rememberSaveable { mutableStateOf(prefs.getString("journal_view","IMPORTANT") ?: "IMPORTANT") }
    var search by rememberSaveable { mutableStateOf("") }
    var text by remember { mutableStateOf("") }; var notice by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") };var menu by remember { mutableStateOf(false) };var showSearch by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState=remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val shown=message
        if(shown.isNotBlank()) {
            snackbarHostState.showSnackbar(shown,duration=SnackbarDuration.Short)
            if(message==shown)message=""
        }
    }
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
        filtered.ifEmpty { if(search.isNotEmpty())"Совпадений нет" else "Журнал ещё не загружен" }.let { JournalStylePolicy.chunks(it) }
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
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("${actionForJob()} · ${jobId.take(8)}",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                IconButton(onClick={showSearch=!showSearch;if(!showSearch)search=""}) {Icon(AppIcons.Search,"Поиск в журнале")}
                IconButton(onClick={loadAll=true},enabled=!loadAll && jobId.isNotEmpty()){Icon(AppIcons.Refresh,"Обновить журнал")}
            IconButton(onClick={scope.launch {
                val content=RepoResultReceiver.redact(notice+"\n"+text)
                try {
                    val intent=withContext(Dispatchers.IO) { ReportSharing.prepare(context,jobId,view,content) }
                    context.startActivity(intent)
                } catch(error:Exception) {
                    android.util.Log.w("AnimaDevShare","File attachment failed: "+error.javaClass.simpleName,error)
                    try {
                        val body=if(content.length>100_000)content.take(100_000)+"\n\n[Текст сокращён. Полный отчёт доступен через «Сохранить отчёт».]" else content
                        val fallback=Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT,body)
                            .putExtra(Intent.EXTRA_SUBJECT,"AnimaDev · журнал "+jobId.take(8))
                        context.startActivity(Intent.createChooser(fallback,"Поделиться текстом"))
                        message="Вложение недоступно ("+error.javaClass.simpleName+"). Открыта отправка текстом."
                    } catch(second:Exception) {
                        android.util.Log.e("AnimaDevShare","Text sharing also failed",second)
                        message="Не удалось поделиться ("+second.javaClass.simpleName+"). Сохраните отчёт через меню журнала."
                    }
                }
            }},enabled=text.isNotEmpty() && jobId.matches(Regex("[a-f0-9]{32}"))) { Icon(AppIcons.Share,"Поделиться отчётом") }
                Box {
                    IconButton(onClick={menu=true}) {Icon(AppIcons.More,"Действия с журналом")}
                    DropdownMenu(menu,{menu=false}) {
                        DropdownMenuItem(text={Text("Копировать доступный журнал")},leadingIcon={Icon(AppIcons.Copy,null)},enabled=text.isNotEmpty(),onClick={menu=false;context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AnimaDev",notice+"\n"+text));message="Журнал скопирован"})
                        DropdownMenuItem(text={Text("Сохранить отчёт")},leadingIcon={Icon(AppIcons.Download,null)},enabled=text.isNotEmpty() && !exporting,onClick={
                            menu=false;exporting=true
                            scope.launch {
                                message=withContext(Dispatchers.IO) {
                                    val destination=com.animacode.animadev.projects.ExportDestination(context)
                                    try {
                                        val safe=RepoResultReceiver.redact(notice+"\n"+text)
                                        destination.save("AnimaDev_${jobId.take(8)}_$view","txt","text/plain",safe.toByteArray(Charsets.UTF_8))
                                        "Сохранено: ${destination.label()} · "+java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date())
                                    } catch(_:Exception) { "Папка недоступна или запись не удалась. Проверьте «Настройки → Файлы и загрузки»." }
                                }
                                exporting=false
                            }
                        })
                    }
                }
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=view=="IMPORTANT",onClick={view="IMPORTANT";loadAll=true},label={Text("Важное")})
                FilterChip(selected=view=="ALL",onClick={view="ALL";loadAll=true},label={Text("Всё")})
            }
            if(showSearch)OutlinedTextField(search,{search=it},label={Text("Поиск по доступному журналу")},singleLine=true,modifier=Modifier.fillMaxWidth())
            if(notice.isNotEmpty())Text(notice,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        }
        if(loadAll || historyLoading || exporting)LinearProgressIndicator(Modifier.fillMaxWidth()) else HorizontalDivider()
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),state=scroll,contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(displayed) {chunk->HighlightedLogChunk(chunk)}
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
    SnackbarHost(snackbarHostState,Modifier.align(Alignment.BottomCenter).padding(horizontal=16.dp).padding(bottom=8.dp))
    }
}
