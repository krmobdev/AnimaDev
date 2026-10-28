package com.animacode.animadev.projects

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.runtime.mutableIntStateOf
import com.animacode.animadev.repos.RepoStore
import com.animacode.animadev.termux.TermuxGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Standalone APK signing does not check out, compile or alter any Git repository. */
class ManualApkSignStore(private val context: Context) {
    companion object {
        val generation = mutableIntStateOf(0)
        const val CALLBACK = "com.animacode.animadev.MANUAL_APK_SIGN_RESULT"
        private const val PREF = "manual_apk_sign_v1"
        val running = setOf("REQUESTED", "UNKNOWN")
    }
    private val pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    fun read(): JSONObject = runCatching {
        val entry = JSONObject(pref.getString("job", "{}") ?: "{}")
        val phase = entry.optString("phase")
        if (entry.optString("state") == "REQUESTED" &&
            phase in setOf("PREPARING", "ENROLLING") &&
            System.currentTimeMillis() - entry.optLong("started") > 300_000L) {
            entry.put("state", "UNKNOWN")
                .put("message", "Прерванная подготовка подписи. Проверьте Загрузки перед повтором.")
            write(entry)
        }
        entry
    }.getOrElse { JSONObject().put("state", "UNKNOWN") }
    fun busy() = read().optString("state") in running
    fun write(data: JSONObject): Boolean = pref.edit().putString("job", data.toString())
        .commit().also { if (it) generation.intValue++ }
    fun acknowledgeUncertain(): Boolean {
        val previous = read()
        if (previous.optString("state") != "UNKNOWN") return false
        // Preserve evidence of previous request in the last-job field.
        return write(previous.put("state", "ACKNOWLEDGED")
            .put("message", "Неопределённое задание подтверждено пользователем. Перед повтором проверьте Загрузки."))
    }
    fun reserve(id: String, project: String, output: Uri): Boolean {
        if (busy()) return false
        return write(JSONObject().put("id", id).put("project", project)
            .put("destination", output.toString()).put("state", "REQUESTED")
            .put("phase", "PREPARING").put("started", System.currentTimeMillis())
            .put("message", "Копируем выбранный APK. Оригинал не изменяется."))
    }
    fun fail(id: String, message: String, uncertain: Boolean = false) {
        val j = read()
        if (j.optString("id") == id && j.optString("state") == "REQUESTED")
            write(j.put("state", if (uncertain) "UNKNOWN" else "FAILED")
                .put("message", message).put("ended", System.currentTimeMillis()))
    }
}

/** Staged input and output are Android-owned MediaStore Downloads rows. Their
 * fixed, random filenames are read/written by Termux, while Android retains
 * scoped-storage access to copy the verified output to a user-chosen SAF document.
 */
class ManualApkSignGateway(private val context: Context) {
    private val store = ManualApkSignStore(context)
    private val resolver get() = context.contentResolver
    private fun mediaFile(name: String): Uri {
        val data = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, data)
            ?: error("MEDIASTORE_UNAVAILABLE")
    }
    private fun digest(stream: java.io.InputStream, output: java.io.OutputStream?): Pair<String, Long> {
        val sha = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024)
        var total = 0L
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            check(total <= 512L * 1024 * 1024) { "FILE_TOO_LARGE" }
            sha.update(buffer, 0, count)
            output?.write(buffer, 0, count)
        }
        return sha.digest().joinToString("") { "%02x".format(it) } to total
    }
    private fun safeDelete(raw: String) {
        if (raw.startsWith("content://media/"))
            runCatching { resolver.delete(Uri.parse(raw), null, null) }
    }

    suspend fun launch(
        project: String, source: Uri, destination: Uri,
        profile: SigningKeyring.Profile, password: String, keyPassword: String,
    ): String {
        if (source.scheme != "content" || destination.scheme != "content" ||
            !SigningKeyring(context).hasAccess(profile)) return "Нет доступа к выбранному файлу или JKS."
        if (password.isEmpty() || password.length > 1024 || keyPassword.length > 1024)
            return "Введите пароль ключа; слишком длинные пароли не принимаются."
        if (context.checkSelfPermission(TermuxGateway.PERMISSION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            TermuxGateway(context).installedVersion() == null)
            return "Сначала подключите Termux и предоставьте RUN_COMMAND."
        if (ReleaseStore(context).busy() || RepoStore(context).blocked())
            return "Termux занят сборкой. Дождитесь подтверждения её завершения."
        val id = UUID.randomUUID().toString().replace("-", "")
        if (!store.reserve(id, project, destination)) return "Предыдущее задание не завершено. Проверьте результат."
        var sourceMedia: Uri? = null
        var outputMedia: Uri? = null
        var dispatched = false
        try {
            val sourceInfo = withContext(Dispatchers.IO) {
                val input = mediaFile("AnimaDev_unsigned_$id.apk")
                sourceMedia = input
                val sourceStream = resolver.openInputStream(source) ?: error("INPUT_UNAVAILABLE")
                val result = sourceStream.use { stream ->
                    resolver.openOutputStream(input, "w")?.use { digest(stream, it) }
                        ?: error("STAGING_UNAVAILABLE")
                }
                check(result.second > 100L) { "SOURCE_INVALID" }
                result
            }
            val output = mediaFile("AnimaDev_signed_$id.apk")
            outputMedia = output
            store.write(store.read().put("source_media", sourceMedia.toString())
                .put("signed_media", output.toString()).put("source_sha", sourceInfo.first)
                .put("source_size", sourceInfo.second).put("phase", "ENROLLING")
                .put("message", "Готовим одноразовый защищённый канал подписи."))
            val keys = withContext(Dispatchers.IO) {
                resolver.openInputStream(Uri.parse(profile.uri))?.use {
                    it.readNBytes(ReleaseWire.MAX_KEY_BYTES + 1)
                }
            } ?: error("KEY_UNAVAILABLE")
            try {
                check(keys.size in 32..ReleaseWire.MAX_KEY_BYTES &&
                    JksInspector.isJks(keys)) { "KEY_INVALID" }
                val passwordChars = password.toCharArray()
                val outcome = try {
                    JksInspector.inspect(keys, profile.alias, passwordChars)
                } finally { passwordChars.fill(0.toChar()) }
                check(outcome == SigningVerification.Outcome.JKS_STORE_AND_ALIAS_VERIFIED) {
                    "KEY_PASSWORD_INVALID"
                }
                val signer = context.assets.open("release/release_signer.py")
                    .bufferedReader().use { it.readText() }
                val worker = context.assets.open("release/manual_apk_sign.py")
                    .bufferedReader().use { it.readText() }
                val script = signer + "\n" + worker
                check(script.length < 60000) { "SIGNER_UNAVAILABLE" }
                val public = ReleaseHybridTransport.enroll(context, id)
                    ?: error("ENROLLMENT_UNAVAILABLE")
                val payload = JSONObject().put("request_id", id).put("variant", "MANUAL_APK")
                    .put("alias", profile.alias)
                    .put("keystore", Base64.encodeToString(keys, Base64.NO_WRAP))
                    .put("store_password", password).put("key_password", keyPassword)
                    .put("source_sha256", sourceInfo.first).put("source_size", sourceInfo.second)
                val sealed = ReleaseHybridTransport.seal(id, public, payload)
                    ?: error("ENCRYPTION_UNAVAILABLE")
                val transportSha = ReleaseHybridTransport.sourceHash(context)
                check(store.write(store.read().put("phase", "DISPATCHING")
                    .put("message", "Подписываем APK в Termux без Gradle.")))
                val intent = Intent(context, ManualApkSignResultReceiver::class.java).apply {
                    action = ManualApkSignStore.CALLBACK
                    data = Uri.parse("animadev://manual-apk/$id")
                }
                val flags = PendingIntent.FLAG_ONE_SHOT or
                    if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val callback = PendingIntent.getBroadcast(context, id.hashCode(), intent, flags)
                val command = Intent("com.termux.RUN_COMMAND").apply {
                    setClassName("com.termux", "com.termux.app.RunCommandService")
                    putExtra("com.termux.RUN_COMMAND_PATH", TermuxGateway.PYTHON)
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script, id, transportSha))
                    putExtra("com.termux.RUN_COMMAND_STDIN", sealed)
                    putExtra("com.termux.RUN_COMMAND_WORKDIR", TermuxGateway.HOME)
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL", "0")
                    putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "AnimaDev · APK signing")
                    putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
                }
                try {
                    check(context.startService(command) != null) { "TERMUX_UNAVAILABLE" }
                    dispatched = true
                } catch (e: Exception) {
                    callback.cancel()
                    throw e
                }
                return "Подпись запущена. Файл будет проверен и сохранён в выбранную папку."
            } finally { keys.fill(0) }
        } catch (e: Exception) {
            val message = when (e.message) {
                "KEY_INVALID", "KEY_PASSWORD_INVALID" -> "Проверьте JKS, alias и пароль."
                "SOURCE_INVALID", "FILE_TOO_LARGE" -> "APK повреждён или слишком велик (макс. 512 МБ)."
                "ENROLLMENT_UNAVAILABLE", "ENCRYPTION_UNAVAILABLE" -> "Защищённое соединение с Termux не установлено."
                else -> "Не удалось подготовить безопасную подпись. Оригинал не изменён."
            }
            store.fail(id, message, dispatched)
            if (!dispatched) {
                sourceMedia?.let { safeDelete(it.toString()) }
                outputMedia?.let { safeDelete(it.toString()) }
                runCatching { DocumentsContract.deleteDocument(resolver, destination) }
            }
            return message
        }
    }

    /** Called only for a response matching the durable request ID. */
    suspend fun complete(id: String, payload: JSONObject, resultOk: Boolean) {
        val record = store.read()
        if (record.optString("id") != id || record.optString("state") != "REQUESTED") return
        val signed = record.optString("signed_media")
        val staged = record.optString("source_media")
        val destination = record.optString("destination")
        try {
            check(resultOk && payload.optBoolean("ok")) {
                if (!resultOk) "RESULT_UNCONFIRMED"
                else payload.optString("error_code", "RESULT_UNCONFIRMED")
            }
            val artifact = payload.getJSONObject("artifact")
            val expectedPath = "/storage/emulated/0/Download/AnimaDev_signed_$id.apk"
            check(artifact.getString("kind") == "MANUAL_APK" &&
                artifact.getString("path") == expectedPath &&
                artifact.getString("sha256").matches(Regex("[a-f0-9]{64}")) &&
                artifact.getLong("size") > 100L &&
                artifact.getString("signer_sha256").matches(Regex("[a-f0-9]{64}"))) {
                "RESULT_MALFORMED"
            }
            val output = Uri.parse(destination)
            val source = Uri.parse(signed)
            val actual = withContext(Dispatchers.IO) {
                val input = resolver.openInputStream(source) ?: error("SIGNED_UNAVAILABLE")
                val computed = input.use { stream ->
                    resolver.openOutputStream(output, "wt")?.use {
                        digest(stream, it)
                    } ?: error("OUTPUT_UNAVAILABLE")
                }
                check(computed.first == artifact.getString("sha256") &&
                    computed.second == artifact.getLong("size")) { "OUTPUT_MISMATCH" }
                resolver.openInputStream(output)?.use { digest(it, null) }
                    ?: error("VERIFY_UNAVAILABLE")
            }
            check(actual.first == artifact.getString("sha256") &&
                actual.second == artifact.getLong("size")) { "VERIFY_MISMATCH" }
            store.write(record.put("state", "SUCCEEDED").put("phase", "DONE")
                .put("signer", artifact.getString("signer_sha256"))
                .put("sha256", artifact.getString("sha256"))
                .put("package", artifact.getString("applicationId"))
                .put("message", "APK подписан и проверен. Сохранён в выбранный файл.")
                .put("ended", System.currentTimeMillis()))
            safeDelete(staged);safeDelete(signed)
        } catch (e: Exception) {
            val code = e.message.orEmpty()
            val failed = code.matches(Regex("[A-Z_]{3,50}"))
            val certain = failed && code !in setOf("OUTPUT_UNAVAILABLE", "VERIFY_UNAVAILABLE",
                "OUTPUT_MISMATCH", "VERIFY_MISMATCH", "RESULT_UNCONFIRMED")
            store.write(record.put("state", if (certain) "FAILED" else "UNKNOWN")
                .put("message", when (code) {
                    "ALREADY_SIGNED" -> "Этот APK уже подписан. Повторная подпись запрещена."
                    "KEY_OR_SIGNING_FAILED" -> "Неверный пароль ключа либо ошибка подписи."
                    "JOB_BUSY" -> "Termux занят другим заданием."
                    else -> "Подпись или сохранение не подтверждены: $code. Не повторяйте вслепую."
                }).put("ended", System.currentTimeMillis()))
            if (certain) {
                safeDelete(staged);safeDelete(signed)
                runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(destination)) }
            }
        }
    }
}

class ManualApkSignResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        val id = uri.lastPathSegment ?: return
        if (intent.action != ManualApkSignStore.CALLBACK || uri.scheme != "animadev" ||
            uri.host != "manual-apk" || !id.matches(Regex("[a-f0-9]{32}"))) return
        val rec = ManualApkSignStore(context).read()
        if (rec.optString("id") != id || rec.optString("state") != "REQUESTED") return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bundle = intent.getBundleExtra("result")
                val text = bundle?.getString("stdout", "").orEmpty()
                val payload = if (text.length <= 8192) JSONObject(text) else JSONObject()
                val ok = bundle?.getInt("exitCode") == 0 &&
                    bundle.getInt("err") == -1 &&
                    payload.optInt("version") == 1 &&
                    payload.optString("request_id") == id
                ManualApkSignGateway(context).complete(id, payload, ok)
            } catch (_: Exception) {
                ManualApkSignStore(context).fail(id,
                    "Результат не подтверждён. Проверьте файл перед повторным запуском.", true)
            } finally { pending.finish() }
        }
    }
}
