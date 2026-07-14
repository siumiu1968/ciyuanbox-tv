package com.jing.sakura.auth

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

class SecureAuthStorage(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun loadSession(): AuthSession? {
        val encrypted = preferences.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            gson.fromJson(decrypt(encrypted), AuthSession::class.java)
        }.getOrElse {
            resetCryptoMaterial()
            null
        }
    }

    fun saveSession(session: AuthSession) {
        preferences.edit().putString(KEY_SESSION, encrypt(gson.toJson(session))).apply()
    }

    fun clearSession() {
        preferences.edit().remove(KEY_SESSION).apply()
    }

    private fun resetCryptoMaterial() {
        preferences.edit()
            .remove(KEY_SESSION)
            .remove(KEY_WRAPPED_DATA_KEY)
            .commit()
        runCatching {
            KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    fun stableDeviceId(): String {
        preferences.getString(KEY_DEVICE_ID, null)?.let { return it }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val seed = androidId?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
            ?.let { "${context.packageName}:$it" }
            ?: UUID.randomUUID().toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        val id = "tv-" + digest.take(16).joinToString("") { "%02x".format(it) }
        preferences.edit().putString(KEY_DEVICE_ID, id).commit()
        return id
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateDataKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(cipher.iv, cipherText).joinToString(":") {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':')
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateDataKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    private fun getOrCreateDataKey(): SecretKeySpec {
        val wrapped = preferences.getString(KEY_WRAPPED_DATA_KEY, null)
        if (wrapped != null) {
            return SecretKeySpec(unwrap(Base64.decode(wrapped, Base64.NO_WRAP)), "AES")
        }
        val keyBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val encoded = Base64.encodeToString(wrap(keyBytes), Base64.NO_WRAP)
        preferences.edit().putString(KEY_WRAPPED_DATA_KEY, encoded).commit()
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun wrap(value: ByteArray): ByteArray = rsaCipher(Cipher.ENCRYPT_MODE).doFinal(value)

    private fun unwrap(value: ByteArray): ByteArray = rsaCipher(Cipher.DECRYPT_MODE).doFinal(value)

    private fun rsaCipher(mode: Int): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) createRsaKeyPair()
        val key = if (mode == Cipher.ENCRYPT_MODE) {
            keyStore.getCertificate(KEY_ALIAS).publicKey
        } else {
            keyStore.getKey(KEY_ALIAS, null)
        }
        return Cipher.getInstance(RSA_TRANSFORMATION).apply { init(mode, key) }
    }

    @Suppress("DEPRECATION")
    private fun createRsaKeyPair() {
        val generator = KeyPairGenerator.getInstance("RSA", ANDROID_KEY_STORE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
                    .build()
            )
        } else {
            val start = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
            generator.initialize(
                KeyPairGeneratorSpec.Builder(context)
                    .setAlias(KEY_ALIAS)
                    .setSubject(X500Principal("CN=Aulama Anime TV"))
                    .setSerialNumber(BigInteger.ONE)
                    .setStartDate(start.time)
                    .setEndDate(end.time)
                    .setKeySize(2048)
                    .build()
            )
        }
        generator.generateKeyPair()
    }

    companion object {
        private const val PREFERENCES = "aulama_auth_secure"
        private const val KEY_SESSION = "session"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_WRAPPED_DATA_KEY = "wrapped_data_key"
        private const val KEY_ALIAS = "aulama_tv_auth_key"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    }
}
