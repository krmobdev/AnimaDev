package com.animacode.animadev.projects

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.UnrecoverableKeyException

/**
 * Inspects legacy JKS store integrity and entry metadata without decrypting a private key.
 * JKS is a legacy SHA-1 password-check format; this is NOT proof of a usable signing key.
 * The Release AAB button must not use this result as authorization to build.
 */
internal object JksInspector {
    private const val MAGIC = 0xFEEDFEED.toInt()
    private val pepper = "Mighty Aphrodite".toByteArray(Charsets.US_ASCII)

    fun isJks(bytes: ByteArray): Boolean = bytes.size >= 4 &&
        bytes[0] == 0xFE.toByte() && bytes[1] == 0xED.toByte() &&
        bytes[2] == 0xFE.toByte() && bytes[3] == 0xED.toByte()

    fun inspect(bytes: ByteArray, alias: String, password: CharArray): SigningVerification.Outcome {
        val corrupted = SigningVerification.Outcome.INVALID_FORMAT
        if (!isJks(bytes) || bytes.size < 32) return corrupted
        val passBytes = ByteArray(password.size * 2)
        try {
            password.forEachIndexed { index, value ->
                passBytes[index * 2] = (value.code ushr 8).toByte()
                passBytes[index * 2 + 1] = value.code.toByte()
            }
            val end = bytes.size - 20
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update(passBytes)
            digest.update(pepper)
            digest.update(bytes, 0, end)
            if (!MessageDigest.isEqual(digest.digest(), bytes.copyOfRange(end, bytes.size))) {
                return SigningVerification.Outcome.WRONG_STORE_PASSWORD_OR_DAMAGED
            }
            DataInputStream(ByteArrayInputStream(bytes, 0, end)).use { input ->
                if (input.readInt() != MAGIC) return corrupted
                val version = input.readInt()
                if (version != 1 && version != 2) return corrupted
                val count = input.readInt()
                if (count < 0 || count > end / 16) return corrupted
                var foundKey = false
                var foundCert = false
                repeat(count) {
                    val tag = input.readInt()
                    val entryAlias = input.readUTF()
                    input.readLong()
                    when (tag) {
                        1 -> {
                            val encrypted = input.readInt()
                            if (!skipChecked(input, encrypted)) return corrupted
                            val chainCount = input.readInt()
                            if (chainCount < 0 || chainCount > input.available() / 4) return corrupted
                            repeat(chainCount) {
                                if (version == 2) input.readUTF()
                                if (!skipChecked(input, input.readInt())) return corrupted
                            }
                            if (entryAlias == alias) foundKey = true
                        }
                        2 -> {
                            if (version == 2) input.readUTF()
                            if (!skipChecked(input, input.readInt())) return corrupted
                            if (entryAlias == alias) foundCert = true
                        }
                        else -> return corrupted
                    }
                }
                if (input.available() != 0) return corrupted
                return when {
                    foundKey -> SigningVerification.Outcome.JKS_STORE_AND_ALIAS_VERIFIED
                    foundCert -> SigningVerification.Outcome.NOT_A_PRIVATE_KEY
                    else -> SigningVerification.Outcome.ALIAS_NOT_FOUND
                }
            }
        } catch (_: IOException) {
            return corrupted
        } finally {
            passBytes.fill(0)
        }
    }

    private fun skipChecked(input: DataInputStream, length: Int): Boolean =
        length >= 0 && length <= input.available() && input.skipBytes(length) == length
}

/** Reads an Android SAF document without persisting or transferring its contents. */
object SigningVerification {
    const val MAX_KEYSTORE_BYTES = 2 * 1024 * 1024
    enum class Outcome {
        VERIFIED, INVALID_SELECTION, TOO_LARGE, INVALID_FORMAT,
        WRONG_STORE_PASSWORD_OR_DAMAGED, UNSUPPORTED_FORMAT,
        JKS_STORE_AND_ALIAS_VERIFIED, ALIAS_NOT_FOUND, NOT_A_PRIVATE_KEY,
        BAD_KEY_PASSWORD, UNSUPPORTED_KEY_ALGORITHM, FILE_UNREADABLE
    }

    fun check(stream: InputStream, fileName: String, alias: String,
              storePassword: CharArray, keyPassword: CharArray): Outcome {
        if (!SigningPolicy.isKeyName(fileName) || alias.isBlank() || storePassword.isEmpty()) {
            stream.close()
            return Outcome.INVALID_SELECTION
        }
        val bytes = stream.use { it.readNBytes(MAX_KEYSTORE_BYTES + 1) }
        if (bytes.size > MAX_KEYSTORE_BYTES) {
            bytes.fill(0)
            return Outcome.TOO_LARGE
        }
        try {
            val format = when {
                JksInspector.isJks(bytes) -> "JKS"
                bytes.size >= 2 && bytes[0] == 0x30.toByte() -> "PKCS12"
                else -> return Outcome.INVALID_FORMAT
            }

            if (format == "JKS") {
                val metadata = JksInspector.inspect(bytes, alias, storePassword)
                if (metadata != Outcome.JKS_STORE_AND_ALIAS_VERIFIED) return metadata
            }

            val store = try {
                KeyStore.getInstance(format)
            } catch (_: KeyStoreException) {
                // Android does not normally include the Java JKS provider.
                return if (format == "JKS") Outcome.JKS_STORE_AND_ALIAS_VERIFIED
                       else Outcome.UNSUPPORTED_FORMAT
            }
            try {
                ByteArrayInputStream(bytes).use { store.load(it, storePassword) }
            } catch (_: GeneralSecurityException) {
                return if (format == "JKS") Outcome.JKS_STORE_AND_ALIAS_VERIFIED
                       else Outcome.WRONG_STORE_PASSWORD_OR_DAMAGED
            } catch (_: IOException) {
                return if (format == "JKS") Outcome.JKS_STORE_AND_ALIAS_VERIFIED
                       else Outcome.WRONG_STORE_PASSWORD_OR_DAMAGED
            }
            if (!store.containsAlias(alias)) return Outcome.ALIAS_NOT_FOUND
            if (!store.isKeyEntry(alias)) return Outcome.NOT_A_PRIVATE_KEY
            val key = try {
                store.getKey(alias, keyPassword)
            } catch (_: UnrecoverableKeyException) {
                return Outcome.BAD_KEY_PASSWORD
            } catch (_: GeneralSecurityException) {
                return Outcome.UNSUPPORTED_KEY_ALGORITHM
            }
            return if (key is PrivateKey) Outcome.VERIFIED else Outcome.NOT_A_PRIVATE_KEY
        } finally {
            bytes.fill(0)
        }
    }

    fun message(outcome: Outcome): String = when (outcome) {
        Outcome.VERIFIED -> "Проверено: файл, alias и пароль закрытого ключа подходят. Введённые пароли не сохранялись. Подпись AAB проверяется отдельно в Termux."
        Outcome.JKS_STORE_AND_ALIAS_VERIFIED ->
            "Пароль хранилища JKS принят, alias найден. Пароль самого закрытого ключа Android проверить не смог из-за отсутствия JKS-провайдера. Это не ошибка пароля: окончательная проверка произойдёт при подписи AAB в Termux."
        Outcome.INVALID_SELECTION -> "Выберите файл ключа, укажите alias и пароль хранилища."
        Outcome.TOO_LARGE -> "Файл ключа превышает ограничение 2 МБ."
        Outcome.INVALID_FORMAT -> "Файл не распознан как JKS или PKCS12 либо его структура повреждена. Расширение .jks не гарантирует формат."
        Outcome.WRONG_STORE_PASSWORD_OR_DAMAGED -> "Не удалось подтвердить пароль хранилища. Проверьте его; также возможен повреждённый файл."
        Outcome.UNSUPPORTED_FORMAT -> "На этом Android отсутствует поддержка формата хранилища."
        Outcome.ALIAS_NOT_FOUND -> "Указанный alias отсутствует внутри файла."
        Outcome.NOT_A_PRIVATE_KEY -> "Указанный alias не является записью закрытого ключа."
        Outcome.BAD_KEY_PASSWORD -> "Пароль закрытого ключа не подошёл."
        Outcome.UNSUPPORTED_KEY_ALGORITHM -> "Алгоритм закрытого ключа не поддерживается на этом Android."
        Outcome.FILE_UNREADABLE -> "Не удалось прочитать выбранный файл через Android. Выберите его заново."
    }
}
