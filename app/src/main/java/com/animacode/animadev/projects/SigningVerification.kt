package com.animacode.animadev.projects

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.GeneralSecurityException

/**
 * Checks the actual private signing key without exporting it.
 * Passwords are supplied by the caller and must never be persisted or logged.
 * This check takes place entirely inside Android; it does not send the file to Termux.
 */
object SigningVerification {
    const val MAX_KEYSTORE_BYTES = 2 * 1024 * 1024
    enum class Outcome {
        VERIFIED, INVALID_SELECTION, TOO_LARGE, UNSUPPORTED_OR_BAD_STORE_PASSWORD,
        ALIAS_NOT_FOUND, NOT_A_PRIVATE_KEY, BAD_KEY_PASSWORD
    }

    fun check(stream: InputStream, fileName: String, alias: String,
              storePassword: CharArray, keyPassword: CharArray): Outcome {
        if (!SigningPolicy.isKeyName(fileName) || alias.isBlank() || storePassword.isEmpty())
            return Outcome.INVALID_SELECTION
        val bytes = stream.use { it.readNBytes(MAX_KEYSTORE_BYTES + 1) }
        if (bytes.size > MAX_KEYSTORE_BYTES) {
            bytes.fill(0)
            return Outcome.TOO_LARGE
        }
        try {
            // Some old .keystore files are JKS, while recent keytool defaults to PKCS12.
            for (type in listOf("JKS", "PKCS12")) {
                val ks = try {
                    KeyStore.getInstance(type).also { keyStore ->
                        ByteArrayInputStream(bytes).use { keyStore.load(it, storePassword) }
                    }
                } catch (_: GeneralSecurityException) {
                    continue
                } catch (_: java.io.IOException) {
                    continue
                }
                if (!ks.containsAlias(alias)) return Outcome.ALIAS_NOT_FOUND
                if (!ks.isKeyEntry(alias)) return Outcome.NOT_A_PRIVATE_KEY
                val key = try { ks.getKey(alias, keyPassword) }
                    catch (_: GeneralSecurityException) { return Outcome.BAD_KEY_PASSWORD }
                return if (key is PrivateKey) Outcome.VERIFIED else Outcome.NOT_A_PRIVATE_KEY
            }
            return Outcome.UNSUPPORTED_OR_BAD_STORE_PASSWORD
        } finally {
            bytes.fill(0)
        }
    }

    fun message(outcome: Outcome): String = when(outcome) {
        Outcome.VERIFIED -> "Ключ подписи и alias проверены. Пароли не сохранены. Release AAB пока заблокирован до проверки защищённой сборки."
        Outcome.INVALID_SELECTION -> "Выберите файл ключа, укажите alias и пароль хранилища."
        Outcome.TOO_LARGE -> "Файл ключа превышает безопасный предел 2 МБ."
        Outcome.UNSUPPORTED_OR_BAD_STORE_PASSWORD -> "Не удалось открыть хранилище: неверный пароль, неподдерживаемый формат или повреждённый файл."
        Outcome.ALIAS_NOT_FOUND -> "Указанный alias отсутствует внутри выбранного файла ключа."
        Outcome.NOT_A_PRIVATE_KEY -> "Alias не содержит закрытого ключа для подписи."
        Outcome.BAD_KEY_PASSWORD -> "Не удалось открыть закрытый ключ: проверьте пароль ключа."
    }
}
