package com.animacode.animadev.repos

import com.animacode.animadev.projects.ProjectPolicy
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.animacode.animadev.termux.TermuxGateway
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

class RepoGateway(private val context: Context) {
    companion object {
        const val VERSION = "0.4.1"
        const val LEGACY_SHA = "e96b432836ff0fb1f23f9455a1440a73fbcb7a675720871d323c4209bb178a4e"
        const val PROTOCOL = 2
        const val PROJECT = "animaquiz"
        const val RUNNER_ROOT = "/data/data/com.termux/files/home/.local/share/animadev"
        const val CALLBACK = "com.animacode.animadev.REPO_RESULT"
        /** Evidence of a rejected job, never a license to cancel a running one. */
        fun historyCapacityFailure(record: JSONObject): Boolean {
            if(record.optBoolean("history_full_recovery"))return true
            val reply=runCatching {JSONObject(record.optString("transport_stdout"))}.getOrNull()
                ?:return false
            return RepoPolicy.journalCapacityRejected(
                record.optString("state"),record.optInt("callback_exit",-99),
                record.optInt("termux_err",-99),reply.optString("error_code"),
                reply.optBoolean("ok",true))
        }

    }
    private val store = RepoStore(context)
    private fun id() = UUID.randomUUID().toString().replace("-", "")
    /** A catalog-validated, local-only project may identify an orphaned CHECK.
     * Retain the original branch/profile on reconciliation: QUERY is not a
     * retry or a reason to reset the on-device journal.
     */
    private fun localRecoveryProfile(r: JSONObject): JSONObject? {
        if(r.optString("state")!="UNKNOWN" || r.optString("action")!="CHECK")return null
        val pid=r.optString("project_id")
        if(pid.isBlank() || pid==PROJECT || !r.optString("job_id").matches(Regex("[a-f0-9]{32}")))return null
        val project=com.animacode.animadev.projects.ProjectStore(context).all()
            .firstOrNull {it.id==pid && it.localOnly && !it.worktree} ?:return null
        val saved=r.optJSONObject("profile")
        if(saved!=null) {
            if(saved.optString("id")!=pid || saved.optString("root")!=project.path ||
                saved.optString("remote").isNotBlank())return null
            return saved
        }
        return JSONObject().put("id",project.id).put("root",project.path)
            .put("remote","").put("name",project.name).put("branch",project.branch)
    }
    fun mayUpgradeRecoveryHelper(r:JSONObject=store.read()):Boolean =
        RepoPolicy.mayUpgradeUnknownLocalCheck(r.optBoolean("pending"),
            store.blocked(),r.optString("state"),r.optString("action"),
            r.optString("project_id",PROJECT),localRecoveryProfile(r)!=null)
    private fun sha(): String = context.assets.open("runner/runner.sha256").bufferedReader().use { it.readText().trim() }
    private fun path(hash: String) = "$RUNNER_ROOT/releases/$hash/animadev_runner.py"
    init {
        val r = store.read()
        if (r.optBoolean("ready") && r.optString("installed_sha").isEmpty()) r.put("installed_sha", LEGACY_SHA)
        if (r.optBoolean("ready") && r.optString("installed_sha") != sha() && !r.optBoolean("pending") && (!store.blocked() || historyCapacityFailure(r) || mayUpgradeRecoveryHelper(r))) {
            r.put("ready", false).put("message", "Обновите локальный помощник кнопкой ниже. История заданий сохранена.")
        }
        store.write(r)
    }

    fun install() {
        val r = store.read()
        if (r.optBoolean("pending") || (store.blocked() && !historyCapacityFailure(r) && !mayUpgradeRecoveryHelper(r)) || com.animacode.animadev.projects.ReleaseStore(context).busy()) return
        val request = id()
        val hash = sha()
        val bytes = context.assets.open("runner/animadev_runner.py").use { it.readBytes() }
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (hash != actual || !hash.matches(Regex("[a-f0-9]{64}"))) {
            store.edit { it.put("ready", false).put("message", "Контрольная сумма встроенного runner не совпадает.") }; return
        }
        val helper = context.assets.open("runner/gradle_tests.py").use { it.readBytes() }
        val helperSha = context.assets.open("runner/gradle.sha256").bufferedReader().use { it.readText().trim() }
        val helperActual = MessageDigest.getInstance("SHA-256").digest(helper).joinToString("") { "%02x".format(it) }
        if (helperSha != helperActual || !helperSha.matches(Regex("[a-f0-9]{64}"))) {
            store.edit { it.put("ready", false).put("message", "Контрольная сумма модуля тестов не совпадает.") }; return
        }
        val monitor = context.assets.open("runner/job_monitor.py").use { it.readBytes() }
        val monitorSha = context.assets.open("runner/monitor.sha256").bufferedReader().use { it.readText().trim() }
        check(MessageDigest.getInstance("SHA-256").digest(monitor).joinToString("") { "%02x".format(it) } == monitorSha)
        val monitorData = Base64.encodeToString(monitor, Base64.NO_WRAP)
        val helperData = Base64.encodeToString(helper, Base64.NO_WRAP)
        val data = Base64.encodeToString(bytes, Base64.NO_WRAP)
        // Fixed bootstrap. No repository content, commands, paths or configuration from the UI.
        val script = """
            import pathlib,os,hashlib,base64,json,sys,tempfile
            os.umask(0o077)
            request_id,expected,payload,helper_sha,helper_payload,monitor_sha,monitor_payload=sys.argv[1:]
            try:
                root=pathlib.Path('$RUNNER_ROOT')
                for p in [root,root/'releases',root/'releases'/expected]:
                    p.mkdir(parents=True,exist_ok=True,mode=0o700)
                    if p.resolve()!=p.absolute() or not p.is_dir(): raise ValueError('PATH_DENIED')
                    os.chmod(p,0o700)
                for name,digest,encoded in [('animadev_runner.py',expected,payload),('gradle_tests.py',helper_sha,helper_payload),('job_monitor.py',monitor_sha,monitor_payload)]:
                    content=base64.b64decode(encoded,validate=True)
                    if len(content)>65536 or hashlib.sha256(content).hexdigest()!=digest: raise ValueError('RUNNER_CHECKSUM_MISMATCH')
                    dest=root/'releases'/expected/name
                    if dest.is_symlink(): raise ValueError('PATH_DENIED')
                    if dest.exists():
                        if hashlib.sha256(dest.read_bytes()).hexdigest()!=digest: raise ValueError('RUNNER_CHECKSUM_MISMATCH')
                    else:
                        fd,tmp=tempfile.mkstemp(prefix='.install-',dir=dest.parent)
                        with os.fdopen(fd,'wb') as f: f.write(content); f.flush(); os.fsync(f.fileno())
                        os.replace(tmp,dest)
                        dfd=os.open(dest.parent,os.O_RDONLY); os.fsync(dfd); os.close(dfd)
                print(json.dumps({'protocol_version':2,'runner_version':'$VERSION','request_id':request_id,'ok':True,'sha256':expected}))
            except Exception as e:
                print(json.dumps({'protocol_version':2,'runner_version':'$VERSION','request_id':request_id,'ok':False,'error_code':str(e) if isinstance(e,ValueError) else 'STORAGE_UNAVAILABLE','message':'Не удалось установить локальный runner.'}))
                sys.exit(1)
        """.trimIndent()
        if(historyCapacityFailure(r))r.put("history_full_recovery",true)
        r.put("transport_id", request).put("mode", "INSTALL").put("pending", true)
            .put("transport_runner_version", VERSION)
            .put("sent_at", System.currentTimeMillis()).put("message", "Устанавливаем локальный помощник вне Git-репозиториев…")
        if (!store.write(r)) return
        send(request, arrayOf("-c", script, request, hash, data, helperSha, helperData, monitorSha, monitorData))
    }
    fun start(action: String, projectId: String, projectPath: String) {
        synchronized(com.animacode.animadev.projects.BuildConcurrencyPolicy.launchMonitor) {
        val project=com.animacode.animadev.projects.ProjectStore(context).all().find {it.id==projectId && it.path==projectPath} ?: return
        if (!ProjectPolicy.canExecute(projectId,projectPath,action,project.worktree,project.remote)) return
        val r = store.read()
        if (action !in setOf("STATUS", "CHECK", "SYNC", "TEST", "TEST_DEVICE", "BUILD_DEBUG") || !r.optBoolean("ready") || r.optBoolean("pending") || store.blocked() || com.animacode.animadev.projects.ReleaseStore(context).busy()) return
        if (action == "SYNC" && !maySync(r,projectId)) return
        // TEST_DEVICE performs fresh Git/ADB validation in the Termux runner.
        // Requiring a cached UI snapshot silently prevented legitimate launches.
        if (RepoPolicy.requiresPreparedSnapshot(action) && !mayTest(r,projectId)) return
        if (action == "BUILD_DEBUG" && !mayBuild(r,projectId)) return
        val previousProject = r.optString("project_id",PROJECT)
        if (previousProject != projectId) {
            r.remove("snapshot")
            r.remove("job")
            r.remove("test_job")
            r.remove("build_job")
        }
        r.put("project_id",projectId)
        if(action=="BUILD_DEBUG") {
            val folder=com.animacode.animadev.projects.ExportDestination(context).artifactDirectory() ?:return
            r.put("output_dir",folder)
        } else r.remove("output_dir")
        if (projectId == PROJECT) r.remove("profile")
        else r.put("profile",JSONObject().put("id",project.id).put("name",project.name)
            .put("root",project.path).put("remote",project.remote).put("branch",project.branch))
        val job = id()
        r.put("job_id", job).put("action", action).put("state", "REQUESTED")
            .put("job_runner_sha", sha())
            .put("message", "Отправляем операцию проекта. Успех ещё не подтверждён.")
            .remove("job")
        if (!store.write(r)) return
        dispatch("START")
        }
    }
    /**
     * Show a synchronous receipt on the Tests screen. START only acknowledges
     * a local durable request; a separate Termux callback confirms execution.
     * Never retry a potentially dispatched job just because its result is late.
     */
    fun startDeviceWithFeedback(projectId: String, projectPath: String): String {
        val project = com.animacode.animadev.projects.ProjectStore(context).all()
            .firstOrNull { it.id == projectId && it.path == projectPath }
            ?: return "Команда не отправлена: проект отсутствует в сохранённом каталоге."
        if (!ProjectPolicy.canExecute(projectId,projectPath,"TEST_DEVICE",project.worktree,project.remote))
            return "Команда не отправлена: профиль проекта или путь не разрешён."
        val initial = store.read()
        val reason = when {
            !initial.optBoolean("ready") -> "Помощник Termux не готов. Сначала обновите его."
            initial.optBoolean("pending") -> "Предыдущая команда ещё ожидает ответа Termux."
            store.blocked() -> "Предыдущее задание не завершено или его результат неизвестен. Проверьте статус по ID."
            com.animacode.animadev.projects.ReleaseStore(context).busy() ->
                "Другая Release-сборка занимает очередь. Проверьте её статус перед запуском тестов."
            else -> ""
        }
        if (reason.isNotEmpty()) return "Команда не отправлена: $reason"
        val oldId = initial.optString("job_id")
        start("TEST_DEVICE",projectId,projectPath)
        val current = store.read()
        val accepted = current.optString("job_id").matches(Regex("[a-f0-9]{32}")) &&
            current.optString("job_id") != oldId &&
            current.optString("project_id") == projectId &&
            current.optString("action") == "TEST_DEVICE"
        return when {
            !accepted -> "Команда не принята. Проверьте предыдущие задания, разрешение Termux и очередь Release."
            current.optString("state") == "UNKNOWN" ->
                "Задание " + current.optString("job_id").take(12) +
                    ": Android не подтвердил отправку. Проверяйте состояние по ID, не запускайте повторно."
            else -> "Запрос записан: " + current.optString("job_id").take(12) +
                ". Ожидаем подтверждение Termux; это ещё не результат тестов."
        }
    }
    fun cancel() {
        val r=store.read()
        if(!r.optBoolean("ready") ||
            r.optString("action") !in setOf("TEST","TEST_DEVICE","BUILD_DEBUG") ||
            r.optString("state") !in setOf("REQUESTED","QUEUED","RUNNING") ||
            r.optString("job_id").isBlank()) return
        if(r.optBoolean("pending")) {
            // Polling is transport activity, not a reason to flicker the control.
            // Persist the confirmed intent bound to this job; never cancel a new job.
            if(r.optString("mode")!="CANCEL")
                store.edit { it.put("cancel_after_job",r.optString("job_id")) }
            return
        }
        store.edit { it.remove("cancel_after_job") }
        dispatch("CANCEL")
    }
    fun drainCancel() {
        val r=store.read()
        if(r.optBoolean("pending"))return
        val target=r.optString("cancel_after_job")
        if(target.isEmpty())return
        if(target!=r.optString("job_id") || r.optString("state") !in setOf("REQUESTED","QUEUED","RUNNING")) {
            store.edit {it.remove("cancel_after_job")};return
        }
        cancel()
    }
    fun query(manual: Boolean = false) {
        drainCancel()
        val r = store.read()
        // A past job must be queryable even after the embedded runner was
        // upgraded. Otherwise UNKNOWN permanently blocks helper installation.
        if (!RepoPolicy.canRecoverJob(r.optBoolean("pending"),
                r.optString("job_id"),r.optString("job_runner_sha"),
                r.optString("installed_sha"))) return
        if (mayUpgradeRecoveryHelper(r) && r.optString("installed_sha") != sha()) {
            store.edit { it.put("message","Обновите локального помощника, затем проверьте прежний Job ID. Старое задание не запускается повторно.") }
            return
        }
        store.edit { it.put("manual_query", manual) }
        dispatch("QUERY")
    }
    fun journal(mode: String, job: String, action: String, offset: Long, view: String) {
        val r = store.read()
        if (r.optBoolean("pending") || !r.optBoolean("ready") || r.optString("installed_sha") != sha()) return
        if (mode !in setOf("LOG", "HISTORY") || !job.matches(Regex("[a-f0-9]{32}")) || action !in setOf("STATUS","CHECK","SYNC","TEST","TEST_DEVICE","BUILD_DEBUG")) return
        dispatch(mode, job, action, offset, view)
    }
    private fun dispatch(mode: String, target: String = "", targetAction: String = "", offset: Long = 0, view: String = "ALL") {
        val r = store.read(); val request = id()
        val localProfile=if(mode=="QUERY")localRecoveryProfile(r) else null
        val useCurrentLocalQuery=RepoPolicy.useCurrentForLocalQuery(mode,
            r.optString("state"),r.optString("installed_sha"),sha(),localProfile!=null)
        val usingCurrentRunner=mode=="QUERY" && historyCapacityFailure(r) &&
            r.optString("installed_sha")==sha()
        val hash = if (mode in setOf("LOG","HISTORY") || usingCurrentRunner || useCurrentLocalQuery) sha()
            else RepoPolicy.runnerForQuery(mode, sha(), r.optString("job_runner_sha"), r.optString("installed_sha"), LEGACY_SHA)
        if (!hash.matches(Regex("[a-f0-9]{64}"))) return
        val runnerVersion = RepoPolicy.runnerVersion(hash, VERSION)
        val payload = JSONObject().put("request_id", request).put("mode", mode)
            .put("job_id", target.ifEmpty { r.getString("job_id") }).put("action", targetAction.ifEmpty { r.getString("action") })
            .put("project_id", r.optString("project_id",PROJECT))
        if (mode=="START" && r.has("profile")) payload.put("profile",r.getJSONObject("profile"))
        if (mode=="QUERY" && useCurrentLocalQuery && localProfile!=null)
            payload.put("profile",localProfile)
        if (mode=="START" && r.optString("action")=="BUILD_DEBUG")
            payload.put("output_dir",r.optString("output_dir","/storage/emulated/0/Download"))
        if (mode=="START" && r.optString("action")=="TEST_DEVICE") {
            val endpoint=com.animacode.animadev.projects.AdbDeviceGateway(context).rememberedEndpoint()
            if(endpoint.isNotEmpty()) payload.put("adb_endpoint",endpoint)
        }
        if (mode in setOf("LOG","HISTORY")) payload.put("offset",offset).put("view",view)
        val script = """
            import pathlib,hashlib,json,sys
            source=pathlib.Path(sys.argv[1]); expected=sys.argv[2]; payload=sys.argv[3]
            request_id=json.loads(payload)['request_id']
            try:
                data=source.read_bytes()
                if source.resolve()!=source.absolute() or hashlib.sha256(data).hexdigest()!=expected:
                    raise ValueError('RUNNER_CHECKSUM_MISMATCH')
            except Exception:
                print(json.dumps({'protocol_version':2,'runner_version':'$runnerVersion','request_id':request_id,'ok':False,'error_code':'RUNNER_CHECKSUM_MISMATCH','message':'Runner отсутствует или изменён. Git не запускался.'}))
                sys.exit(1)
            sys.argv=[str(source),payload]
            exec(compile(data,str(source),'exec'),{'__name__':'__main__','__file__':str(source),'EXPECTED_SHA':expected})
        """.trimIndent()
        r.put("transport_id", request).put("mode", mode).put("page_job", target).put("page_view", view).put("page_offset",offset).put("pending", true).put("sent_at", System.currentTimeMillis())
            .put("transport_runner_version", runnerVersion)
        if (!store.write(r)) return
        send(request, arrayOf("-c", script, path(hash), hash, payload.toString()))
    }
    private fun send(request: String, args: Array<String>) {
        val callbackIntent = Intent(context, RepoResultReceiver::class.java).apply {
            action = CALLBACK; data = Uri.parse("animadev://repos/$request")
        }
        val flags = PendingIntent.FLAG_ONE_SHOT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(context, request.hashCode(), callbackIntent, flags)
        val command = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", TermuxGateway.PYTHON)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args)
            putExtra("com.termux.RUN_COMMAND_WORKDIR", TermuxGateway.HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "AnimaDev · операция проекта")
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
        }
        try { check(context.startService(command) != null) }
        catch (_: Exception) {
            callback.cancel()
            store.edit {
                if(it.optString("mode") in setOf("LOG","HISTORY")) {
                    it.put("pending",false).put("journal_message","Android не подтвердил чтение журнала. Сохранённые данные доступны.").remove("transport_id")
                } else it.put("pending", false).put("state", "UNKNOWN")
                    .put("message", "Android не подтвердил отправку. Результат неизвестен. Проверьте разрешение Termux; запуск не повторяем.")
            }
        }
    }
    fun mayTest(r: JSONObject, projectId:String=PROJECT): Boolean {
        if(r.optString("project_id",PROJECT)!=projectId)return false
        val snapshot = r.optJSONObject("snapshot") ?: return false
        return RepoPolicy.mayTest(snapshot.optString("branch"), snapshot.optBoolean("remote_ok"),
            r.optBoolean("ready"), store.blocked() || r.optBoolean("pending"),projectId,
            snapshot.optBoolean("local_only"),snapshot.optBoolean("checked"),
            snapshot.optString("local_commit"))
    }
    fun mayBuild(r: JSONObject, projectId:String=PROJECT): Boolean {
        if(r.optString("project_id",PROJECT)!=projectId)return false
        val s = r.optJSONObject("snapshot") ?: return false
        return RepoPolicy.mayBuild(s.optString("branch"),s.optBoolean("dirty",true),s.optBoolean("remote_ok"),
            r.optBoolean("ready"),store.blocked() || r.optBoolean("pending"),s.optBoolean("checked"),
            s.optInt("ahead",-1),s.optInt("behind",-1),s.optString("local_commit"),s.optString("remote_commit"),projectId,
            s.optBoolean("local_only"))
    }
    fun maySync(r: JSONObject, projectId:String=PROJECT): Boolean {
        if(r.optString("project_id",PROJECT)!=projectId)return false
        val s = r.optJSONObject("snapshot") ?: return false
        return RepoPolicy.maySync(s.optString("branch"), s.optBoolean("dirty", true), s.optBoolean("remote_ok"),
            s.optInt("ahead", -1), s.optInt("behind", -1), s.optBoolean("checked"), s.optBoolean("can_sync"),
            store.blocked() || r.optBoolean("pending") || !r.optBoolean("ready"),projectId)
    }
}
