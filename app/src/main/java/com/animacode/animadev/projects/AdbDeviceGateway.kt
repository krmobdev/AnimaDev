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
    fun refresh() {
        if(prefs.getBoolean("pending",false) &&
            System.currentTimeMillis()-prefs.getLong("sent_at",0)<30_000)return
        val request=UUID.randomUUID().toString().replace("-","")
        val code="""
import json,re,subprocess,sys
req=sys.argv[1]
try:
    p=subprocess.run(['/data/data/com.termux/files/usr/bin/adb','devices','-l'],
       stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,
       timeout=18,check=False)
    lines=p.stdout.decode('utf-8','replace').splitlines()
    devices=[]
    if p.returncode==0 and len(p.stdout)<16000:
        for line in lines[1:]:
            fields=line.split()
            if len(fields)>1 and fields[1]=='device' and re.fullmatch(r'[A-Za-z0-9._:-]{4,128}',fields[0]) and not fields[0].startswith('emulator-'):
                devices.append(fields[0])
    print(json.dumps({'request_id':req,'ok':p.returncode==0,'count':len(devices),
       'device':devices[0] if len(devices)==1 else ''}))
except Exception:
    print(json.dumps({'request_id':req,'ok':False,'count':0,'device':''}))
""".trimIndent()
        if(!prefs.edit().putString("request",request).putLong("sent_at",System.currentTimeMillis())
            .putBoolean("pending",true).putString("message","Проверяем adb devices -l в Termux…").commit())return
        val callback=PendingIntent.getBroadcast(context,request.hashCode(),
            Intent(context,AdbDeviceReceiver::class.java).apply {
                action="com.animacode.animadev.ADB_DEVICES"
                data=Uri.parse("animadev://adb-devices/$request")
            },PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        val call=Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux","com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH",TermuxGateway.PYTHON)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-c",code,request))
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
        if(prefs.getBoolean("pending",false) && System.currentTimeMillis()-prefs.getLong("sent_at",0)>30_000)
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
        var count=0;var serial=""
        try {
            val bundle=intent.getBundleExtra("result") ?:error("No reply")
            val out=bundle.getString("stdout","") ?: ""
            check(bundle.getInt("exitCode")==0 && bundle.getInt("err")==-1 && out.length<2000)
            val result=JSONObject(out)
            check(result.getString("request_id")==id && result.getBoolean("ok"))
            count=result.getInt("count")
            check(count in 0..32)
            serial=result.optString("device").take(128)
            if(count==1)check(serial.matches(Regex("[A-Za-z0-9._:-]{4,128}")))
        } catch(_:Exception) {count=0;serial=""}
        gateway.prefs.edit().putBoolean("pending",false).remove("request")
            .putInt("count",count).putLong("verified_at",System.currentTimeMillis())
            .putString("message",when(count) {
                1->"Устройство готово: $serial"
                0->"Устройств нет. После adb pair выполните adb connect IP:ПОРТ и проверьте adb devices -l."
                else->"Обнаружено $count устройств. Отключите лишние: разрешено одно физическое устройство."
            }).commit()
    }
}
