package com.animacode.animadev.repos

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
        const val VERSION = "0.3.0"
        const val LEGACY_SHA = "e96b432836ff0fb1f23f9455a1440a73fbcb7a675720871d323c4209bb178a4e"
        const val PROTOCOL = 2
        const val PROJECT = "animaquiz"
        const val RUNNER_ROOT = "/data/data/com.termux/files/home/.local/share/animadev"
        const val CALLBACK = "com.animacode.animadev.REPO_RESULT"
    }
    private val store = RepoStore(context)
    private fun id() = UUID.randomUUID().toString().replace("-", "")
    private fun sha(): String = context.assets.open("runner/runner.sha256").bufferedReader().use { it.readText().trim() }
    private fun path(hash: String) = "$RUNNER_ROOT/releases/$hash/animadev_runner.py"
    init {
        val r = store.read()
        if (r.optBoolean("ready") && r.optString("installed_sha").isEmpty()) r.put("installed_sha", LEGACY_SHA)
        if (r.optBoolean("ready") && r.optString("installed_sha") != sha() && !r.optBoolean("pending") && !store.blocked()) {
            r.put("ready", false).put("message", "Обновите локальный помощник кнопкой ниже. История заданий сохранена.")
        }
        store.write(r)
    }

    fun install() {
        val r = store.read()
        if (r.optBoolean("pending") || store.blocked()) return
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
        val helperData = Base64.encodeToString(helper, Base64.NO_WRAP)
        val data = Base64.encodeToString(bytes, Base64.NO_WRAP)
        // Fixed bootstrap. No repository content, commands, paths or configuration from the UI.
        val script = """
            import pathlib,os,hashlib,base64,json,sys,tempfile
            os.umask(0o077)
            request_id,expected,payload,helper_sha,helper_payload=sys.argv[1:]
            try:
                root=pathlib.Path('$RUNNER_ROOT')
                for p in [root,root/'releases',root/'releases'/expected]:
                    p.mkdir(parents=True,exist_ok=True,mode=0o700)
                    if p.resolve()!=p.absolute() or not p.is_dir(): raise ValueError('PATH_DENIED')
                    os.chmod(p,0o700)
                for name,digest,encoded in [('animadev_runner.py',expected,payload),('gradle_tests.py',helper_sha,helper_payload)]:
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
        r.put("transport_id", request).put("mode", "INSTALL").put("pending", true)
            .put("transport_runner_version", VERSION)
            .put("sent_at", System.currentTimeMillis()).put("message", "Устанавливаем локальный помощник вне Git-репозиториев…")
        if (!store.write(r)) return
        send(request, arrayOf("-c", script, request, hash, data, helperSha, helperData))
    }
    fun start(action: String) {
        val r = store.read()
        if (action !in setOf("STATUS", "CHECK", "SYNC", "TEST") || !r.optBoolean("ready") || r.optBoolean("pending") || store.blocked()) return
        if (action == "SYNC" && !maySync(r)) return
        if (action == "TEST" && !mayTest(r)) return
        val job = id()
        r.put("job_id", job).put("action", action).put("state", "REQUESTED")
            .put("job_runner_sha", sha())
            .put("message", "Отправляем операцию проекта. Успех ещё не подтверждён.")
            .remove("job")
        if (!store.write(r)) return
        dispatch("START")
    }
    fun query() {
        val r = store.read()
        if (!r.optBoolean("ready") || r.optBoolean("pending") || r.optString("job_id").isEmpty()) return
        dispatch("QUERY")
    }
    private fun dispatch(mode: String) {
        val r = store.read(); val request = id()
        val hash = RepoPolicy.runnerForQuery(mode, sha(), r.optString("job_runner_sha"), r.optString("installed_sha"), LEGACY_SHA)
        if (!hash.matches(Regex("[a-f0-9]{64}"))) return
        val runnerVersion = RepoPolicy.runnerVersion(hash, VERSION)
        val payload = JSONObject().put("request_id", request).put("mode", mode)
            .put("job_id", r.getString("job_id")).put("action", r.getString("action"))
            .put("project_id", PROJECT).toString()
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
        r.put("transport_id", request).put("mode", mode).put("pending", true).put("sent_at", System.currentTimeMillis())
            .put("transport_runner_version", runnerVersion)
        if (!store.write(r)) return
        send(request, arrayOf("-c", script, path(hash), hash, payload))
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
                it.put("pending", false).put("state", "UNKNOWN")
                    .put("message", "Android не подтвердил отправку. Результат неизвестен. Проверьте разрешение Termux; запуск не повторяем.")
            }
        }
    }
    fun mayTest(r: JSONObject): Boolean {
        val snapshot = r.optJSONObject("snapshot") ?: return false
        return RepoPolicy.mayTest(snapshot.optString("branch"), snapshot.optBoolean("remote_ok"),
            r.optBoolean("ready"), store.blocked() || r.optBoolean("pending"))
    }
    fun maySync(r: JSONObject): Boolean {
        val s = r.optJSONObject("snapshot") ?: return false
        return RepoPolicy.maySync(s.optString("branch"), s.optBoolean("dirty", true), s.optBoolean("remote_ok"),
            s.optInt("ahead", -1), s.optInt("behind", -1), s.optBoolean("checked"), s.optBoolean("can_sync"),
            store.blocked() || r.optBoolean("pending") || !r.optBoolean("ready"))
    }
}
