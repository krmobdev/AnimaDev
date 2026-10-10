package com.animacode.animadev.projects

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.animacode.animadev.termux.TermuxGateway
import org.json.JSONObject
import java.util.UUID

/** Read-only ADB discovery inside Termux; never starts an activity on the device. */
class AdbDeviceGateway(private val context:Context) {
    val prefs=context.getSharedPreferences("adb_status_v1",Context.MODE_PRIVATE)
    fun recentDevice():Boolean {
        return prefs.getInt("count",0)==1 && !prefs.getBoolean("pending",false) &&
            System.currentTimeMillis()-prefs.getLong("verified_at",0) in 0..90_000
    }
    companion object {
        /** Android IMEs can insert invisible separators into pasted IP:port text.
         *  Normalize only whitespace, then re-validate the complete IPv4 LAN address.
         */
        fun normalizedEndpoint(value:String):String=value.filterNot { it.isWhitespace() ||
            it=='\u200B' || it=='\u200C' || it=='\u200D' || it=='\uFEFF' }
        /** IPv4 LAN endpoint only. Pairing ports must not be guessed or scanned. */
        fun validLanEndpoint(value:String):Boolean {
            val segments=normalizedEndpoint(value).split(":")
            if(segments.size!=2)return false
            val octets=segments[0].split(".")
            if(octets.size!=4 || octets.any {it.isEmpty() || it.length>3 || it.any {c->!c.isDigit()} })return false
            val n=octets.mapNotNull {it.toIntOrNull()}
            if(n.size!=4 || n.any {it !in 0..255})return false
            val port=segments[1].toIntOrNull() ?: return false
            if(port !in 1024..65535)return false
            return n[0]==10 || (n[0]==172 && n[1] in 16..31) ||
                (n[0]==192 && n[1]==168)
        }
    }
    fun connect(endpoint:String) {
        if(!validLanEndpoint(endpoint)) {
            prefs.edit().putString("message",
                "Укажи IP и порт подключения с основного экрана Pixel, например 192.168.1.132:37123. "+
                "Порт из окна «Сопряжение» не подходит.").apply()
            return
        }
        refresh(normalizedEndpoint(endpoint))
    }
    fun refresh(endpoint:String="") {
        if(prefs.getBoolean("pending",false) &&
            System.currentTimeMillis()-prefs.getLong("sent_at",0)<45_000)return
        val request=UUID.randomUUID().toString().replace("-","")
        val code="""
import json,re,subprocess,sys
req=sys.argv[1]
endpoint=sys.argv[2] if len(sys.argv)>2 else ''
ADB='/data/data/com.termux/files/usr/bin/adb'
def scan():
    p=subprocess.run([ADB,'devices','-l'],stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,timeout=9,check=False)
    if p.returncode!=0 or len(p.stdout)>16000:
        raise RuntimeError('adb devices failed')
    connected=[]
    offline=0
    unauthorized=0
    for line in p.stdout.decode('utf-8','replace').splitlines()[1:]:
        fields=line.split()
        if len(fields)<2 or not re.fullmatch(r'[A-Za-z0-9._:-]{4,128}',fields[0]) or fields[0].startswith('emulator-'):
            continue
        if fields[1]=='device':connected.append(fields[0])
        elif fields[1]=='offline':offline+=1
        elif fields[1]=='unauthorized':unauthorized+=1
    return connected,offline,unauthorized
try:
    devices_before,_,_=scan()
    if endpoint:
        import ipaddress
        host,port=endpoint.rsplit(':',1)
        ip=ipaddress.ip_address(host)
        if ip.version!=4 or not ip.is_private or not 1024<=int(port)<=65535:
            raise ValueError('endpoint')
        if len(devices_before)==1:
            # Reuse the established ADB server/transport. Re-connecting to
            # a stale port would needlessly disturb a working Termux session.
            connect_status='ALREADY_CONNECTED'
        else:
            connection=subprocess.run([ADB,'connect',endpoint],
                stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,timeout=12,check=False)
            response=(connection.stdout+connection.stderr).decode('utf-8','replace').lower()
            connect_status=('CONNECTED' if connection.returncode==0 and
                            'connected' in response and 'failed' not in response
                            else 'CONNECT_REFUSED' if 'refused' in response else 'CONNECT_FAILED')
    else:
        connect_status='NOT_ATTEMPTED'
    devices,offline,unauthorized=scan()
    print(json.dumps({'request_id':req,'ok':True,'count':len(devices),
        'device':devices[0] if len(devices)==1 else '',
        'offline':offline,'unauthorized':unauthorized,
        'connect_status':connect_status}))
except Exception:
    print(json.dumps({'request_id':req,'ok':False,'count':0,'device':'',
        'offline':0,'unauthorized':0,'connect_status':'TRANSPORT_ERROR'}))
""".trimIndent()
        if(!prefs.edit().putString("request",request).putLong("sent_at",System.currentTimeMillis())
            .putBoolean("pending",true).putString("message",if(endpoint.isBlank())"Проверяем adb devices -l в Termux…" else "Подключаем Pixel и проверяем ADB…").commit())return
        val callback=PendingIntent.getBroadcast(context,request.hashCode(),
            Intent(context,AdbDeviceReceiver::class.java).apply {
                action="com.animacode.animadev.ADB_DEVICES"
                data=Uri.parse("animadev://adb-devices/$request")
            },PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        val call=Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux","com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH",TermuxGateway.PYTHON)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-c",code,request,endpoint))
            putExtra("com.termux.RUN_COMMAND_WORKDIR",TermuxGateway.HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","AnimaDev · обнаружение устройства ADB")
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
        }
        try {
            if(context.startService(call)==null)error("Termux unavailable")
        } catch(_:Exception) {
            callback.cancel()
            prefs.edit().putBoolean("pending",false).putInt("count",0)
                .putString("message","Нет ответа Termux. Проверьте разрешение RUN_COMMAND.").commit()
        }
    }
    fun expire() {
        if(prefs.getBoolean("pending",false) && System.currentTimeMillis()-prefs.getLong("sent_at",0)>45_000)
            prefs.edit().putBoolean("pending",false).putInt("count",0)
                .putString("message","ADB не ответил вовремя. Повторите проверку.").commit()
    }
}

class AdbDeviceReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val id=intent.data?.lastPathSegment ?:return
        if(intent.action!="com.animacode.animadev.ADB_DEVICES" ||
            intent.data?.scheme!="animadev" || intent.data?.host!="adb-devices" ||
            !id.matches(Regex("[a-f0-9]{32}")))return
        val gateway=AdbDeviceGateway(context)
        if(gateway.prefs.getString("request","")!=id)return
        var count=0;var serial="";var connection="NOT_ATTEMPTED"
        var offline=0;var unauthorized=0
        try {
            val bundle=intent.getBundleExtra("result") ?:error("No reply")
            val out=bundle.getString("stdout","") ?: ""
            check(bundle.getInt("exitCode")==0 && bundle.getInt("err")==-1 && out.length<2000)
            val result=JSONObject(out)
            check(result.getString("request_id")==id && result.getBoolean("ok"))
            count=result.getInt("count")
            check(count in 0..32)
            offline=result.optInt("offline",0)
            unauthorized=result.optInt("unauthorized",0)
            check(offline in 0..32 && unauthorized in 0..32)
            serial=result.optString("device").take(128)
            connection=result.optString("connect_status","NOT_ATTEMPTED").takeIf {
                it in setOf("CONNECTED","ALREADY_CONNECTED","CONNECT_REFUSED","CONNECT_FAILED","NOT_ATTEMPTED","TRANSPORT_ERROR")
            } ?: "TRANSPORT_ERROR"
            if(count==1)check(serial.matches(Regex("[A-Za-z0-9._:-]{4,128}")))
        } catch(_:Exception) {count=0;serial="";offline=0;unauthorized=0;connection="TRANSPORT_ERROR"}
        gateway.prefs.edit().putBoolean("pending",false).remove("request")
            .putInt("count",count).putLong("verified_at",System.currentTimeMillis())
            .putString("message",when(count) {
                1->"Устройство готово: $serial"
                0->when(connection) {
                    "CONNECT_REFUSED"->"Pixel отклонил ADB-соединение: Connection refused. Проверь актуальный порт беспроводной отладки — он мог измениться."
                    "CONNECT_FAILED"->"ADB не смог подключиться к Pixel. Проверь актуальный IP:порт на основном экране беспроводной отладки и доступ к локальной сети (в том числе через VPN)."
                    "TRANSPORT_ERROR"->"Нет подтверждённого ответа ADB из Termux. Проверьте разрешение RUN_COMMAND."
                    else->when {
                        offline>0 -> "Pixel обнаружен, но ADB показывает offline ($offline). Соединение потеряно: проверь актуальный порт и нажми «Подключить Pixel»."
                        unauthorized>0 -> "ADB видит устройство без разрешения (unauthorized). Подтверди отладку на Pixel."
                        else -> "ADB не видит подключённых устройств. Для беспроводной отладки нужен актуальный порт подключения Pixel."
                    }
                }
                else->"Обнаружено $count устройств. Отключите лишние: разрешено одно физическое устройство."
            }).commit()
    }
}
