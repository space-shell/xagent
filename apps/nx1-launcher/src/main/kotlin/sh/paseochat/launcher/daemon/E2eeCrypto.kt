package sh.paseochat.launcher.daemon

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid

object E2eeCrypto {

    private val sodium: LazySodiumAndroid by lazy { LazySodiumAndroid(SodiumAndroid()) }

    private const val PUBLIC_KEY_BYTES = 32
    private const val SECRET_KEY_BYTES = 32
    private const val SHARED_KEY_BYTES = 32
    private const val NONCE_BYTES = 24
    private const val MAC_BYTES = 16

    data class KeyPairData(val publicKey: ByteArray, val secretKey: ByteArray)

    fun generateKeyPair(): KeyPairData {
        val pk = ByteArray(PUBLIC_KEY_BYTES)
        val sk = ByteArray(SECRET_KEY_BYTES)
        if (!sodium.cryptoBoxKeypair(pk, sk)) {
            throw RuntimeException("Failed to generate Curve25519 keypair")
        }
        return KeyPairData(pk, sk)
    }

    fun deriveSharedKey(daemonPublicKey: ByteArray, clientSecretKey: ByteArray): ByteArray {
        require(daemonPublicKey.size == PUBLIC_KEY_BYTES) {
            "daemon public key must be $PUBLIC_KEY_BYTES bytes, got ${daemonPublicKey.size}"
        }
        require(clientSecretKey.size == SECRET_KEY_BYTES) {
            "client secret key must be $SECRET_KEY_BYTES bytes, got ${clientSecretKey.size}"
        }
        val shared = ByteArray(SHARED_KEY_BYTES)
        if (!sodium.cryptoBoxBeforeNm(shared, daemonPublicKey, clientSecretKey)) {
            throw RuntimeException("Failed to derive shared key (beforeNM)")
        }
        return shared
    }

    fun encrypt(plaintext: ByteArray, sharedKey: ByteArray): ByteArray {
        require(sharedKey.size == SHARED_KEY_BYTES)
        val nonce = sodium.nonce(NONCE_BYTES)
        val ciphertext = ByteArray(plaintext.size + MAC_BYTES)
        if (!sodium.cryptoBoxEasyAfterNm(
                ciphertext, plaintext, plaintext.size.toLong(), nonce, sharedKey,
            )
        ) {
            throw RuntimeException("E2EE encryption failed")
        }
        return nonce + ciphertext
    }

    fun decrypt(bundle: ByteArray, sharedKey: ByteArray): ByteArray? {
        require(sharedKey.size == SHARED_KEY_BYTES)
        if (bundle.size < NONCE_BYTES + MAC_BYTES) return null
        val nonce = bundle.copyOfRange(0, NONCE_BYTES)
        val ciphertext = bundle.copyOfRange(NONCE_BYTES, bundle.size)
        val plaintext = ByteArray(ciphertext.size - MAC_BYTES)
        return if (sodium.cryptoBoxOpenEasyAfterNm(
                plaintext, ciphertext, ciphertext.size.toLong(), nonce, sharedKey,
            )
        ) {
            plaintext
        } else {
            null
        }
    }

    fun encodePublicKeyBase64(key: ByteArray): String =
        Base64.encodeToString(key, Base64.NO_WRAP)

    fun decodePublicKeyBase64(b64: String): ByteArray =
        Base64.decode(b64, Base64.NO_WRAP)
}
