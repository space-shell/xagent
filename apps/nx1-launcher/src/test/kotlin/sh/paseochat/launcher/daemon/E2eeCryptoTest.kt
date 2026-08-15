package sh.paseochat.launcher.daemon

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Known-answer tests against libsodium 1.0.20. Vectors in
 * src/test/resources/e2ee-vectors.properties were generated directly by
 * libsodium (crypto_box_keypair / crypto_box_beforenm /
 * crypto_box_easy_afternm) — never hand-edited. These pin the daemon wire
 * format: any drift in the BouncyCastle composition breaks relay pairing.
 */
class E2eeCryptoTest {

    private fun h(s: String): ByteArray =
        ByteArray(s.length / 2) { i ->
            ((Character.digit(s[i * 2], 16) shl 4) + Character.digit(s[i * 2 + 1], 16)).toByte()
        }

    private val props: Map<String, String> =
        javaClass.classLoader!!.getResourceAsStream("e2ee-vectors.properties")!!
            .bufferedReader().readLines()
            .filter { it.contains('=') }
            .associate { line ->
                val idx = line.indexOf('=')
                line.substring(0, idx) to line.substring(idx + 1)
            }

    private val daemonPk = h(props["daemonPk"]!!)
    private val clientSk = h(props["clientSk"]!!)
    private val sharedKey = h(props["shared"]!!)
    private val nonce = h(props["nonce"]!!)

    private val vectors: List<Pair<ByteArray, ByteArray>> =
        (0 until props["count"]!!.toInt()).map { i ->
            h(props["msg$i"]!!) to h(props["box$i"]!!)
        }

    @Test
    fun `vector fixture sanity`() {
        assertEquals(32, daemonPk.size)
        assertEquals(24, nonce.size)
        assertTrue(vectors.isNotEmpty())
        vectors.forEach { (m, box) -> assertEquals(m.size + 16, box.size) }
    }

    @Test
    fun `deriveSharedKey matches libsodium crypto_box_beforenm`() {
        assertArrayEquals(sharedKey, E2eeCrypto.deriveSharedKey(daemonPk, clientSk))
    }

    @Test
    fun `decrypt opens libsodium crypto_box_easy_afternm output`() {
        vectors.forEach { (m, box) ->
            val bundle = nonce + box // our frame: nonce(24) || mac(16) || ct
            val opened = E2eeCrypto.decrypt(bundle, sharedKey)
            assertNotNull("decrypt failed for ${m.size}-byte message", opened)
            assertArrayEquals(m, opened)
        }
    }

    @Test
    fun `encrypt then decrypt roundtrips`() {
        vectors.forEach { (m, _) ->
            val bundle = E2eeCrypto.encrypt(m, sharedKey)
            assertEquals(24 + 16 + m.size, bundle.size)
            assertArrayEquals(m, E2eeCrypto.decrypt(bundle, sharedKey))
        }
    }

    @Test
    fun `tampered mac fails authentication`() {
        val (m, _) = vectors[1]
        val bundle = E2eeCrypto.encrypt(m, sharedKey)
        bundle[25] = (bundle[25].toInt() xor 1).toByte()
        assertNull(E2eeCrypto.decrypt(bundle, sharedKey))
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val (m, _) = vectors[0]
        val bundle = E2eeCrypto.encrypt(m, sharedKey)
        bundle[bundle.size - 1] = (bundle[bundle.size - 1].toInt() xor 1).toByte()
        assertNull(E2eeCrypto.decrypt(bundle, sharedKey))
    }

    @Test
    fun `wrong key fails authentication`() {
        val (m, _) = vectors[1]
        val bundle = E2eeCrypto.encrypt(m, sharedKey)
        assertNull(E2eeCrypto.decrypt(bundle, ByteArray(32) { 7 }))
    }

    @Test
    fun `short bundle returns null`() {
        assertNull(E2eeCrypto.decrypt(ByteArray(30), sharedKey))
    }

    @Test
    fun `decrypt with truncated libsodium box returns null`() {
        val (_, box) = vectors[1]
        assertNull(E2eeCrypto.decrypt(nonce + box.copyOf(box.size - 1), sharedKey))
    }

    @Test
    fun `keypair is valid curve25519 pair`() {
        val kp = E2eeCrypto.generateKeyPair()
        assertEquals(32, kp.publicKey.size)
        assertEquals(32, kp.secretKey.size)
        // X25519 + HSalsa20 is symmetric: both directions must agree.
        val other = E2eeCrypto.generateKeyPair()
        assertArrayEquals(
            E2eeCrypto.deriveSharedKey(kp.publicKey, other.secretKey),
            E2eeCrypto.deriveSharedKey(other.publicKey, kp.secretKey),
        )
    }

    @Test
    fun `base64 roundtrips`() {
        val key = E2eeCrypto.generateKeyPair().publicKey
        assertArrayEquals(key, E2eeCrypto.decodePublicKeyBase64(E2eeCrypto.encodePublicKeyBase64(key)))
    }
}
