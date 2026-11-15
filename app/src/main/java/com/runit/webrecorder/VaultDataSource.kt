package com.runit.webrecorder

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile
import javax.crypto.Cipher

/**
 * Lets the player stream an encrypted vault file: reads the encrypted bytes and decrypts them on the fly,
 * with full seeking (AES-CTR). The URI looks like vio-vault://<id>.
 */
class VaultDataSource(private val ctx: android.content.Context) : BaseDataSource(false) {

    private var raf: RandomAccessFile? = null
    private var cipher: Cipher? = null
    private var openUri: Uri? = null
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val u = dataSpec.uri
        openUri = u
        transferInitializing(dataSpec)
        val id = u.host ?: u.schemeSpecificPart.trimStart('/')
        val entry = Vault.entry(ctx, id) ?: throw IOException("Vault file not found")
        val file = Vault.fileFor(ctx, entry)
        val f = RandomAccessFile(file, "r")
        val len = f.length()
        val pos = dataSpec.position
        if (pos > len) {
            f.close()
            throw IOException("Position out of range")
        }
        f.seek(pos)
        raf = f
        cipher = VaultCrypto.cipherAt(entry.key, entry.iv, pos)
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) len - pos else minOf(dataSpec.length, len - pos)
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val f = raf ?: throw IOException("Not open")
        val c = cipher ?: throw IOException("Not open")
        val want = minOf(length.toLong(), remaining).toInt()
        val n = f.read(buffer, offset, want)
        if (n < 0) throw EOFException()
        c.update(buffer, offset, n, buffer, offset)
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = openUri

    override fun close() {
        openUri = null
        cipher = null
        try {
            raf?.close()
        } catch (_: Exception) {
        }
        raf = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(private val ctx: android.content.Context) : DataSource.Factory {
        override fun createDataSource(): DataSource = VaultDataSource(ctx.applicationContext)
    }
}
