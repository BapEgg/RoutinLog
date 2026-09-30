package com.bapegg.routinlog.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES/GCM ciphertext only, stored in app-private noBackupFilesDir. No deprecated security-crypto. */
class EncryptedSessionStore internal constructor(context: Context, private val alias: String) : SessionStore {
    constructor(context: Context) : this(context, "routinlog.account.session.v1")

    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "account-session.v1"))
    private val gson = Gson()
    private val aad = "routinlog-session-v1".toByteArray(Charsets.UTF_8)

    @Synchronized override fun read(): StoredSession? {
        return try {
            // Let AtomicFile recover an interrupted write before deciding that the session is absent.
            val encrypted = file.openRead().use { stream ->
                require(stream.channel.size() in 1..65536)
                stream.readBytes()
            }
            val payload = DataInputStream(ByteArrayInputStream(encrypted))
            require(payload.readInt() == 1)
            val ivLength = payload.readInt(); require(ivLength == 12)
            val iv = ByteArray(ivLength).also(payload::readFully)
            val ciphertextLength = payload.readInt(); require(ciphertextLength in 16..60000)
            val ciphertext = ByteArray(ciphertextLength).also(payload::readFully)
            require(payload.available() == 0)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)); cipher.updateAAD(aad)
            val plain = cipher.doFinal(ciphertext)
            try {
                gson.fromJson(String(plain, Charsets.UTF_8), StoredSession::class.java).also {
                    require(it != null && it.accessToken.isNotBlank() && it.refreshToken.isNotBlank() && it.userId.isNotBlank())
                }
            } finally { plain.fill(0) }
        } catch (_: FileNotFoundException) {
            null
        } catch (_: Exception) {
            // Unreadable/restored/corrupt ciphertext cannot establish an authenticated session.
            clear()
            runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
            null
        }
    }

    @Synchronized override fun write(session: StoredSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(aad)
        val plain = gson.toJson(session).toByteArray(Charsets.UTF_8)
        val ciphertext = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(1); output.writeInt(cipher.iv.size); output.write(cipher.iv)
                output.writeInt(ciphertext.size); output.write(ciphertext)
            }
        }.toByteArray()
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }

    @Synchronized override fun clear() { file.delete() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
}
