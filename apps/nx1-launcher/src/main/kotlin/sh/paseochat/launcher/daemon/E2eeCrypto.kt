package sh.paseochat.launcher.daemon

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.crypto.engines.XSalsa20Engine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.math.ec.rfc7748.X25519

/**
 * NaCl crypto_box constructions over BouncyCastle primitives.
 *
 * Wire format is byte-identical to libsodium:
 *  - deriveSharedKey  == crypto_box_beforenm  (X25519 + HSalsa20(zero16))
 *  - encrypt/decrypt  == crypto_box_easy_afternm output, framed as
 *    nonce(24) || mac(16) || ciphertext
 */
object E2eeCrypto {

    private const val PUBLIC_KEY_BYTES = 32
    private const val SECRET_KEY_BYTES = 32
    private const val SHARED_KEY_BYTES = 32
    private const val NONCE_BYTES = 24
    private const val MAC_BYTES = 16

    private val random = SecureRandom()

    data class KeyPairData(val publicKey: ByteArray, val secretKey: ByteArray)

    fun generateKeyPair(): KeyPairData {
        val sk = ByteArray(SECRET_KEY_BYTES)
        X25519.generatePrivateKey(random, sk)
        val pk = ByteArray(PUBLIC_KEY_BYTES)
        X25519.generatePublicKey(sk, 0, pk, 0)
        return KeyPairData(pk, sk)
    }

    fun deriveSharedKey(daemonPublicKey: ByteArray, clientSecretKey: ByteArray): ByteArray {
        require(daemonPublicKey.size == PUBLIC_KEY_BYTES) {
            "daemon public key must be $PUBLIC_KEY_BYTES bytes, got ${daemonPublicKey.size}"
        }
        require(clientSecretKey.size == SECRET_KEY_BYTES) {
            "client secret key must be $SECRET_KEY_BYTES bytes, got ${clientSecretKey.size}"
        }
        val agreed = ByteArray(SHARED_KEY_BYTES)
        if (!X25519.calculateAgreement(clientSecretKey, 0, daemonPublicKey, 0, agreed, 0)) {
            throw RuntimeException("Failed to derive shared key (X25519 agreement)")
        }
        return hsalsa20(agreed, ByteArray(16))
    }

    fun encrypt(plaintext: ByteArray, sharedKey: ByteArray): ByteArray {
        require(sharedKey.size == SHARED_KEY_BYTES)
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val ciphertext = ByteArray(plaintext.size)
        val polyKey = streamXor(sharedKey, nonce, plaintext, ciphertext)
        val mac = ByteArray(MAC_BYTES)
        poly1305(polyKey, ciphertext, mac)
        return nonce + mac + ciphertext
    }

    fun decrypt(bundle: ByteArray, sharedKey: ByteArray): ByteArray? {
        require(sharedKey.size == SHARED_KEY_BYTES)
        if (bundle.size < NONCE_BYTES + MAC_BYTES) return null
        val nonce = bundle.copyOfRange(0, NONCE_BYTES)
        val mac = bundle.copyOfRange(NONCE_BYTES, NONCE_BYTES + MAC_BYTES)
        val ciphertext = bundle.copyOfRange(NONCE_BYTES + MAC_BYTES, bundle.size)

        val engine = XSalsa20Engine()
        engine.init(false, ParametersWithIV(KeyParameter(sharedKey), nonce))
        val polyKey = ByteArray(32)
        engine.processBytes(polyKey, 0, 32, polyKey, 0)
        val plaintext = ByteArray(ciphertext.size)
        engine.processBytes(ciphertext, 0, ciphertext.size, plaintext, 0)

        val expected = ByteArray(MAC_BYTES)
        poly1305(polyKey, ciphertext, expected)
        return if (MessageDigest.isEqual(mac, expected)) plaintext else null
    }

    fun encodePublicKeyBase64(key: ByteArray): String =
        Base64.getEncoder().encodeToString(key)

    fun decodePublicKeyBase64(b64: String): ByteArray =
        Base64.getDecoder().decode(b64)

    private fun streamXor(
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        output: ByteArray,
    ): ByteArray {
        val engine = XSalsa20Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))
        val polyKey = ByteArray(32)
        engine.processBytes(polyKey, 0, 32, polyKey, 0)
        engine.processBytes(input, 0, input.size, output, 0)
        return polyKey
    }

    private fun poly1305(key: ByteArray, message: ByteArray, out: ByteArray) {
        val mac = Poly1305()
        mac.init(KeyParameter(key))
        mac.update(message, 0, message.size)
        mac.doFinal(out, 0)
    }

    private fun rotl(v: Int, n: Int): Int = (v shl n) or (v ushr (32 - n))

    private fun leInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or
            ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or
            ((b[off + 3].toInt() and 0xff) shl 24)

    private fun putLeInt(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xff).toByte()
        b[off + 1] = ((v ushr 8) and 0xff).toByte()
        b[off + 2] = ((v ushr 16) and 0xff).toByte()
        b[off + 3] = ((v ushr 24) and 0xff).toByte()
    }

    /**
     * HSalsa20 extract function (RFC-draft bernstein-crypto-hash-hsalsa20):
     * 20 Salsa20 rounds over (sigma, key, input), output words
     * x0, x5, x10, x15, x6, x7, x8, x9. Matches crypto_core_hsalsa20
     * with the standard sigma constant and a 16-byte zero input.
     */
    private fun hsalsa20(key: ByteArray, input16: ByteArray): ByteArray {
        var x0 = 0x61707865.toInt()
        var x1 = leInt(key, 0)
        var x2 = leInt(key, 4)
        var x3 = leInt(key, 8)
        var x4 = leInt(key, 12)
        var x5 = 0x3320646e.toInt()
        var x6 = leInt(input16, 0)
        var x7 = leInt(input16, 4)
        var x8 = leInt(input16, 8)
        var x9 = leInt(input16, 12)
        var x10 = 0x79622d32.toInt()
        var x11 = leInt(key, 16)
        var x12 = leInt(key, 20)
        var x13 = leInt(key, 24)
        var x14 = leInt(key, 28)
        var x15 = 0x6b206574.toInt()

        repeat(10) {
            // column rounds
            x4 = x4 xor rotl(x0 + x12, 7)
            x8 = x8 xor rotl(x4 + x0, 9)
            x12 = x12 xor rotl(x8 + x4, 13)
            x0 = x0 xor rotl(x12 + x8, 18)
            x9 = x9 xor rotl(x5 + x1, 7)
            x13 = x13 xor rotl(x9 + x5, 9)
            x1 = x1 xor rotl(x13 + x9, 13)
            x5 = x5 xor rotl(x1 + x13, 18)
            x14 = x14 xor rotl(x10 + x6, 7)
            x2 = x2 xor rotl(x14 + x10, 9)
            x6 = x6 xor rotl(x2 + x14, 13)
            x10 = x10 xor rotl(x6 + x2, 18)
            x3 = x3 xor rotl(x15 + x11, 7)
            x7 = x7 xor rotl(x3 + x15, 9)
            x11 = x11 xor rotl(x7 + x3, 13)
            x15 = x15 xor rotl(x11 + x7, 18)
            // row rounds
            x1 = x1 xor rotl(x0 + x3, 7)
            x2 = x2 xor rotl(x1 + x0, 9)
            x3 = x3 xor rotl(x2 + x1, 13)
            x0 = x0 xor rotl(x3 + x2, 18)
            x6 = x6 xor rotl(x5 + x4, 7)
            x7 = x7 xor rotl(x6 + x5, 9)
            x4 = x4 xor rotl(x7 + x6, 13)
            x5 = x5 xor rotl(x4 + x7, 18)
            x11 = x11 xor rotl(x10 + x9, 7)
            x8 = x8 xor rotl(x11 + x10, 9)
            x9 = x9 xor rotl(x8 + x11, 13)
            x10 = x10 xor rotl(x9 + x8, 18)
            x12 = x12 xor rotl(x15 + x14, 7)
            x13 = x13 xor rotl(x12 + x15, 9)
            x14 = x14 xor rotl(x13 + x12, 13)
            x15 = x15 xor rotl(x14 + x13, 18)
        }

        val out = ByteArray(32)
        putLeInt(out, 0, x0)
        putLeInt(out, 4, x5)
        putLeInt(out, 8, x10)
        putLeInt(out, 12, x15)
        putLeInt(out, 16, x6)
        putLeInt(out, 20, x7)
        putLeInt(out, 24, x8)
        putLeInt(out, 28, x9)
        return out
    }
}
