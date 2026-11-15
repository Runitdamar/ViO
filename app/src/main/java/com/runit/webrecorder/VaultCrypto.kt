package com.runit.webrecorder

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-CTR helpers for the vault. CTR lets a video be decrypted from any position (seeking). */
object VaultCrypto {

    /** 128-bit big-endian counter: iv + blocks. */
    fun addCounter(iv: ByteArray, blocks: Long): ByteArray {
        val out = iv.copyOf()
        var carry = blocks
        var i = 15
        while (i >= 0 && carry != 0L) {
            val sum = (out[i].toInt() and 0xff) + (carry and 0xff).toInt()
            out[i] = (sum and 0xff).toByte()
            carry = (carry ushr 8) + (sum shr 8)
            i--
        }
        return out
    }

    /** A cipher positioned at byte offset [pos] of the stream. */
    fun cipherAt(key: ByteArray, iv: ByteArray, pos: Long): Cipher {
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(addCounter(iv, pos / 16)))
        val skip = (pos % 16).toInt()
        if (skip > 0) c.update(ByteArray(skip))
        return c
    }
}
