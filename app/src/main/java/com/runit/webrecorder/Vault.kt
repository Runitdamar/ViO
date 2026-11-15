package com.runit.webrecorder

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VaultEntry(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val cat: Cat,
    val addedAt: Long,
    val key: ByteArray,
    val iv: ByteArray
) {
    val title: String get() = name.substringBeforeLast('.')
    val ext: String get() = name.substringAfterLast('.', "").lowercase(Locale.US)
}

/**
 * Encrypted on-device storage. Every file gets its own random AES-256 key (AES-CTR, so videos can be
 * seeked while decrypting). The per-file keys live in an index that is itself encrypted (AES-GCM) with
 * a master key held in the Android Keystore. Files live in the app's private folder, so they are not
 * visible to the gallery or other apps.
 */
object Vault {

    private const val ALIAS = "vio_vault_master"
    private val lock = Any()
    private var cache: MutableList<VaultEntry>? = null
    private val adding = java.util.concurrent.atomic.AtomicInteger(0)

    private fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "vault")
        d.mkdirs()
        return d
    }

    private fun indexFile(ctx: Context) = File(dir(ctx), "index.bin")

    fun fileFor(ctx: Context, e: VaultEntry): File = File(dir(ctx), e.id + ".vio")

    fun tempDir(ctx: Context): File {
        val d = File(ctx.cacheDir, "vault_tmp")
        d.mkdirs()
        return d
    }

    // ---- master key ----

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** False if this phone can't give us a Keystore key (very rare). */
    fun isAvailable(): Boolean = try {
        masterKey()
        true
    } catch (_: Exception) {
        false
    }

    // ---- index ----

    private fun load(ctx: Context): MutableList<VaultEntry> {
        cache?.let { return it }
        val list = ArrayList<VaultEntry>()
        val f = indexFile(ctx)
        if (f.exists()) {
            try {
                val raw = f.readBytes()
                val iv = raw.copyOfRange(0, 12)
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
                val json = String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        VaultEntry(
                            o.getString("id"), o.getString("name"), o.optString("mime", "application/octet-stream"),
                            o.optLong("size", 0L), catOf(o.optString("cat", "OTHER")), o.optLong("added", 0L),
                            Base64.decode(o.getString("key"), Base64.NO_WRAP),
                            Base64.decode(o.getString("iv"), Base64.NO_WRAP)
                        )
                    )
                }
            } catch (_: Exception) {
                // unreadable index: start empty rather than crash (the encrypted files stay on disk)
            }
        }
        cache = list
        return list
    }

    private fun catOf(s: String): Cat = try {
        Cat.valueOf(s)
    } catch (_: Exception) {
        Cat.OTHER
    }

    private fun save(ctx: Context, list: List<VaultEntry>) {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("name", e.name)
            o.put("mime", e.mime)
            o.put("size", e.size)
            o.put("cat", e.cat.name)
            o.put("added", e.addedAt)
            o.put("key", Base64.encodeToString(e.key, Base64.NO_WRAP))
            o.put("iv", Base64.encodeToString(e.iv, Base64.NO_WRAP))
            arr.put(o)
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, masterKey())
        val enc = c.doFinal(arr.toString().toByteArray(Charsets.UTF_8))
        val out = c.iv + enc
        val tmp = File(dir(ctx), "index.tmp")
        tmp.writeBytes(out)
        val dst = indexFile(ctx)
        if (!tmp.renameTo(dst)) {
            dst.delete()
            if (!tmp.renameTo(dst)) throw IOException("Cannot save vault index")
        }
    }

    fun entries(ctx: Context): List<VaultEntry> = synchronized(lock) {
        load(ctx).sortedByDescending { it.addedAt }
    }

    fun entry(ctx: Context, id: String): VaultEntry? = synchronized(lock) {
        load(ctx).firstOrNull { it.id == id }
    }

    fun totalBytes(ctx: Context): Long = synchronized(lock) { load(ctx).sumOf { it.size } }

    // ---- add / remove ----

    /** Encrypts [src] into the vault. Throws if anything goes wrong (nothing is left behind). */
    fun add(ctx: Context, src: InputStream, name: String, mime: String, cat: Cat): VaultEntry {
        val rnd = SecureRandom()
        val key = ByteArray(32)
        val iv = ByteArray(16)
        rnd.nextBytes(key)
        rnd.nextBytes(iv)
        val idBytes = ByteArray(8)
        rnd.nextBytes(idBytes)
        val id = idBytes.joinToString("") { String.format("%02x", it.toInt() and 0xff) }

        val tmp = File(dir(ctx), "$id.vio")
        var plain = 0L
        adding.incrementAndGet()
        try {
            val c = VaultCrypto.cipherAt(key, iv, 0)
            CipherOutputStream(tmp.outputStream().buffered(256 * 1024), c).use { out ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    plain += n
                }
            }
            if (tmp.length() != plain) throw IOException("Encrypted size mismatch")
            if (plain == 0L) throw IOException("File is empty")
            val e = VaultEntry(id, name, mime, plain, cat, System.currentTimeMillis(), key, iv)
            synchronized(lock) {
                val list = load(ctx)
                list.add(e)
                try {
                    save(ctx, list)
                } catch (ex: Exception) {
                    list.remove(e)
                    throw ex
                }
            }
            return e
        } catch (ex: Exception) {
            try {
                tmp.delete()
            } catch (_: Exception) {
            }
            throw ex
        } finally {
            adding.decrementAndGet()
        }
    }

    /** Deletes half-written files left behind if the app was killed while moving something in. */
    fun cleanOrphans(ctx: Context) {
        if (adding.get() > 0) return
        synchronized(lock) {
            val known = load(ctx).map { it.id + ".vio" }.toSet()
            dir(ctx).listFiles()?.forEach {
                if (it.name.endsWith(".vio") && it.name !in known && adding.get() == 0) it.delete()
                if (it.name == "index.tmp") it.delete()
            }
        }
    }

    fun remove(ctx: Context, id: String) {
        synchronized(lock) {
            val list = load(ctx)
            val e = list.firstOrNull { it.id == id } ?: return
            list.remove(e)
            try {
                save(ctx, list)
            } catch (_: Exception) {
            }
            try {
                fileFor(ctx, e).delete()
            } catch (_: Exception) {
            }
        }
    }

    // ---- reading back ----

    /** Plain stream of the decrypted file. */
    fun open(ctx: Context, e: VaultEntry): InputStream {
        val c = VaultCrypto.cipherAt(e.key, e.iv, 0)
        return CipherInputStream(fileFor(ctx, e).inputStream().buffered(256 * 1024), c)
    }

    /** Decrypts into the cache folder (for image / PDF viewers). Cleared on lock and exit. */
    fun decryptToTemp(ctx: Context, e: VaultEntry): File {
        val safe = e.name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(60)
        val out = File(tempDir(ctx), e.id + "_" + safe)
        if (out.exists() && out.length() == e.size) return out
        open(ctx, e).use { i -> out.outputStream().use { o -> i.copyTo(o, 128 * 1024) } }
        return out
    }

    fun clearTemp(ctx: Context) {
        try {
            tempDir(ctx).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    /** Moves a file out of the vault back into the public library. Returns the saved name. */
    fun restore(ctx: Context, e: VaultEntry): String {
        val tmp = File(tempDir(ctx), "restore_" + e.id)
        try {
            open(ctx, e).use { i -> tmp.outputStream().use { o -> i.copyTo(o, 128 * 1024) } }
            if (tmp.length() != e.size) throw IOException("Decrypted size mismatch")
            val saved = Publisher.publish(ctx, tmp, e.name, Library.mimeFor(e.name, e.cat), e.cat)
            remove(ctx, e.id)
            return saved
        } finally {
            try {
                tmp.delete()
            } catch (_: Exception) {
            }
        }
    }
}
