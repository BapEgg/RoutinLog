package com.bapegg.routinlog.auth

import android.content.Context
import android.content.ContextWrapper
import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Exercises the real Android Keystore while isolating both its alias and the ciphertext directory. */
@RunWith(AndroidJUnit4::class)
class EncryptedSessionStoreTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var alias: String
    private lateinit var store: EncryptedSessionStore
    private val fakeSession = StoredSession(
        accessToken = "instrumentation-only-fake-access-token",
        refreshToken = "instrumentation-only-fake-refresh-token",
        expiresAtEpochSeconds = 1_900_000_000L,
        userId = "instrumentation-only-fake-user",
    )

    @Before fun setUp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val id = UUID.randomUUID().toString()
        alias = "routinlog.test.session.$id"
        directory = File(app.noBackupFilesDir, "session-store-test-$id")
        check(directory.mkdirs())
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = directory
        }
        store = EncryptedSessionStore(context, alias)
        assertNull(store.read())
        assertFalse(keyStore().containsAlias(alias))
    }

    @After fun tearDown() {
        // No production alias or production session file is read, removed, or overwritten by these tests.
        if (::alias.isInitialized) {
            check(alias.startsWith("routinlog.test.session."))
            keyStore().deleteEntry(alias)
        }
        if (::directory.isInitialized) {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            check(directory.parentFile?.canonicalFile == app.noBackupFilesDir.canonicalFile)
            check(directory.name.startsWith("session-store-test-"))
            directory.deleteRecursively()
        }
    }

    @Test fun roundTripPersistsAcrossInstancesWithoutPlaintextTokens() {
        store.write(fakeSession)
        val firstCiphertext = ciphertextFile().readBytes()
        val rawText = firstCiphertext.toString(Charsets.ISO_8859_1)
        assertFalse(rawText.contains(fakeSession.accessToken))
        assertFalse(rawText.contains(fakeSession.refreshToken))
        assertFalse(rawText.contains(fakeSession.userId))
        assertEquals(fakeSession, EncryptedSessionStore(context, alias).read())
        val key = keyStore().getKey(alias, null)
        assertEquals("AES", key.algorithm)
        assertNull("Android Keystore key material must not be exportable", key.encoded)

        store.write(fakeSession)
        assertFalse("Each encrypted write must use a fresh IV", firstCiphertext.contentEquals(ciphertextFile().readBytes()))
        assertEquals(fakeSession, store.read())
    }

    @Test fun clearRemovesPersistedSessionAndAllowsLaterSignIn() {
        store.write(fakeSession)
        store.clear()
        assertFalse(ciphertextFile().exists())
        assertNull(EncryptedSessionStore(context, alias).read())
        // Normal logout clears the ciphertext; the app-owned key can encrypt the next login safely.
        assertTrue(keyStore().containsAlias(alias))
        val next = fakeSession.copy(accessToken = "fake-access-next", refreshToken = "fake-refresh-next")
        store.write(next)
        assertEquals(next, EncryptedSessionStore(context, alias).read())
    }

    @Test fun tamperedCiphertextCannotRestoreSessionAndRecoversWithNewKey() {
        store.write(fakeSession)
        val bytes = ciphertextFile().readBytes()
        // Preserve the file header and damage the authenticated ciphertext/tag, exercising GCM verification.
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        ciphertextFile().writeBytes(bytes)
        assertNull(EncryptedSessionStore(context, alias).read())
        assertFalse(ciphertextFile().exists())
        assertFalse(keyStore().containsAlias(alias))

        store.write(fakeSession)
        assertTrue(keyStore().containsAlias(alias))
        assertEquals(fakeSession, store.read())
    }

    @Test fun interruptedAtomicWritePreservesLastCompletedSession() {
        store.write(fakeSession)
        val interrupted = AtomicFile(ciphertextFile())
        interrupted.startWrite().use { it.write(byteArrayOf(1, 2, 3)) }
        // Deliberately do not call finishWrite/failWrite: simulate a process stopping mid-write.
        assertEquals(fakeSession, EncryptedSessionStore(context, alias).read())
        assertTrue(keyStore().containsAlias(alias))
    }

    private fun ciphertextFile() = File(directory, "account-session.v1")
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
