package com.animacode.animadev.projects

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Opt-in, device-bound encrypted credentials. Nothing is backed up or sent to GitHub.
 * The JKS itself is never stored here. AAD binds ciphertext to exact project + URI + alias.
 */
class SigningSecretVault(context:Context) {
    data class Credentials(val storePassword:String,val keyPassword:String)
    private val prefs=context.getSharedPreferences("signing_secrets_v1",Context.MODE_PRIVATE)
    private val keyAlias="com.animacode.animadev.signing-secrets-v1"
    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (ks.getKey(keyAlias,null) as? SecretKey)?.let {return it}
        val kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(keyAlias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        return kg.generateKey()
    }
    private fun validId(id:String)=id.matches(Regex("[A-Za-z0-9_.-]{1,90}"))
    private fun aad(id:String,uri:String,alias:String)=
        ("AnimaDev:JKS:v1:"+id+":"+uri+":"+alias).toByteArray(StandardCharsets.UTF_8)
    fun has(id:String):Boolean=validId(id) && prefs.contains(id)
    fun forget(id:String):Boolean=validId(id) && prefs.edit().remove(id).commit()
    fun save(id:String,uri:String,alias:String,storePassword:String,keyPassword:String):Boolean {
        if(!validId(id) || !uri.startsWith("content://") || alias.isBlank() ||
           storePassword.isEmpty() || storePassword.length>1024 || keyPassword.length>1024)return false
        return try {
            val plaintext=JSONObject().put("v",1).put("store",storePassword).put("key",keyPassword).toString().toByteArray(StandardCharsets.UTF_8)
            try {
                val cipher=Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE,key())
                cipher.updateAAD(aad(id,uri,alias))
                val body=cipher.doFinal(plaintext)
                prefs.edit().putString(id,JSONObject().put("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
                    .put("data",Base64.encodeToString(body,Base64.NO_WRAP)).toString()).commit()
            } finally {plaintext.fill(0)}
        } catch(_:Exception) {false}
    }
    fun load(id:String,uri:String,alias:String):Credentials? {
        if(!validId(id) || uri.isBlank() || alias.isBlank())return null
        return try {
            val blob=JSONObject(prefs.getString(id,"") ?: "")
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(blob.getString("iv"),Base64.NO_WRAP)))
            cipher.updateAAD(aad(id,uri,alias))
            val plaintext=cipher.doFinal(Base64.decode(blob.getString("data"),Base64.NO_WRAP))
            try {
                val j=JSONObject(String(plaintext,StandardCharsets.UTF_8))
                if(j.getInt("v")!=1) null else Credentials(j.getString("store"),j.getString("key"))
            } finally {plaintext.fill(0)}
        } catch(_:Exception) {null}
    }
}
