package com.animacode.animadev.projects

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.animacode.animadev.termux.TermuxGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import java.util.concurrent.ConcurrentHashMap

/** The public key comes from a one-time Termux RUN_COMMAND callback.
 * No secret is placed in a command before its RSA-OAEP/AES-GCM envelope is created.
 * The one-use private key is kept inside Termux and deleted during decrypt.
 */
object ReleaseHybridTransport {
    private val waits=ConcurrentHashMap<String,CompletableDeferred<String?>>()
    private val requestPattern=Regex("[a-f0-9]{32}")
    private val shaPattern=Regex("[a-f0-9]{64}")

    private val bootstrap="""
import base64,hashlib,json,os,pathlib,re,subprocess,sys
os.umask(0o077)
request_id=sys.argv[1] if len(sys.argv)>1 else ""
try:
    if len(sys.argv)!=4 or not re.fullmatch(r"[a-f0-9]{32}",request_id):
        raise ValueError()
    expected,content=sys.argv[2:]
    if not re.fullmatch(r"[a-f0-9]{64}",expected) or len(content)>16000:
        raise ValueError()
    source=base64.b64decode(content,validate=True)
    if hashlib.sha256(source).hexdigest()!=expected:
        raise ValueError()
    state=pathlib.Path("/data/data/com.termux/files/home/.local/share/animadev")
    if not state.is_dir() or state.is_symlink():raise ValueError()
    target=state/"ReleaseTransport.java"
    if target.is_symlink():raise ValueError()
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest()!=expected:
        import tempfile
        fd,tmp=tempfile.mkstemp(prefix=".release-transport-",dir=state)
        try:
            with os.fdopen(fd,"wb") as out:out.write(source);out.flush();os.fsync(out.fileno())
            os.replace(tmp,target)
        finally:
            if os.path.exists(tmp):os.unlink(tmp)
    result=subprocess.run(["java",str(target),"enroll",request_id],
        stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,
        timeout=70,check=False)
    if result.returncode or not 400<len(result.stdout)<2000:raise ValueError()
    key=result.stdout.decode("ascii")
    if not re.fullmatch(r"[A-Za-z0-9+/=]+",key):raise ValueError()
    print(json.dumps({"version":1,"request_id":request_id,"ok":True,"public_key":key}))
except Exception:
    print(json.dumps({"version":1,"request_id":request_id,"ok":False,"error_code":"ENROLLMENT_UNAVAILABLE"}))
""".trimIndent()

    fun sourceHash(context:Context):String {
        val bytes=context.assets.open("release/ReleaseTransport.java").use {it.readBytes()}
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    }

    suspend fun enroll(context:Context,request:String):String? {
        if(!requestPattern.matches(request))return null
        val bytes=runCatching {context.assets.open("release/ReleaseTransport.java").use {it.readBytes()}}.getOrNull() ?: return null
        if(bytes.size>10_000)return null
        val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        if(!shaPattern.matches(sha))return null
        val deferred=CompletableDeferred<String?>()
        if(waits.putIfAbsent(request,deferred)!=null)return null
        val callback=PendingIntent.getBroadcast(context,request.hashCode(),
            Intent(context,ReleaseEnrollmentReceiver::class.java).apply {
                action="com.animacode.animadev.RELEASE_ENROLLMENT"
                data=Uri.parse("animadev://release-enroll/$request")
            },PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        try {
            val intent=Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux","com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH",TermuxGateway.PYTHON)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-c",bootstrap,request,sha,Base64.encodeToString(bytes,Base64.NO_WRAP)))
                putExtra("com.termux.RUN_COMMAND_WORKDIR",TermuxGateway.HOME)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL","0")
                putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","AnimaDev · одноразовое шифрование Release")
                putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
            }
            if(context.startService(intent)==null)return null
            return withTimeoutOrNull(90_000) {deferred.await()}
        } catch(_:Exception) {return null}
        finally {waits.remove(request);callback.cancel();bytes.fill(0)}
    }

    fun receive(request:String,publicKey:String?) {
        if(requestPattern.matches(request))waits[request]?.complete(publicKey)
    }

    fun seal(request:String,publicKey:String,json:JSONObject):String? = runCatching {
        if(!requestPattern.matches(request) || publicKey.length>2000)return null
        val public=KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.decode(publicKey,Base64.DEFAULT))) as RSAPublicKey
        if(public.modulus.bitLength()!=3072)return null
        val key=KeyGenerator.getInstance("AES").apply {init(256)}.generateKey()
        val iv=ByteArray(12).also {SecureRandom().nextBytes(it)}
        val aes=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE,key,GCMParameterSpec(128,iv))
            updateAAD(request.toByteArray(Charsets.US_ASCII))
        }
        val plaintext=json.toString().toByteArray(Charsets.UTF_8)
        val body=try {aes.doFinal(plaintext)}finally {plaintext.fill(0)}
        val oaep=OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT)
        val rsa=Cipher.getInstance("RSA/ECB/OAEPPadding").apply {init(Cipher.ENCRYPT_MODE,public,oaep)}
        val wrapped=rsa.doFinal(key.encoded)
        val envelope=listOf(
            Base64.encodeToString(wrapped,Base64.NO_WRAP),
            Base64.encodeToString(iv,Base64.NO_WRAP),
            Base64.encodeToString(body,Base64.NO_WRAP)).joinToString("\n")
        if(envelope.length>230000)return null
        envelope
    }.getOrNull()
}

class ReleaseEnrollmentReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val uri=intent.data ?:return
        if(intent.action!="com.animacode.animadev.RELEASE_ENROLLMENT" ||
            uri.scheme!="animadev" || uri.host!="release-enroll")return
        val request=uri.lastPathSegment ?:return
        var key:String?=null
        try {
            val result=intent.getBundleExtra("result") ?:error("missing")
            val stdout=result.getString("stdout","") ?: ""
            check(stdout.length<2500 && result.getInt("exitCode")==0 && result.getInt("err")==-1)
            val reply=JSONObject(stdout)
            check(reply.optInt("version")==1 && reply.getString("request_id")==request && reply.getBoolean("ok"))
            key=reply.getString("public_key").takeIf {it.length in 400..1800}
        } catch(_:Exception) {}
        ReleaseHybridTransport.receive(request,key)
    }
}
