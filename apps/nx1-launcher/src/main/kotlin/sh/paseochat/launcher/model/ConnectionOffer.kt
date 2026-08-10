package sh.paseochat.launcher.model

import android.util.Base64
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import sh.paseochat.launcher.daemon.models.DaemonJson

private const val TAG = "ConnectionOffer"

@Serializable
data class RelayEndpoint(
    val endpoint: String,
    val useTls: Boolean? = null,
)

@Serializable
data class ConnectionOffer(
    val v: Int = 2,
    val serverId: String,
    @SerialName("daemonPublicKeyB64") val daemonPublicKeyB64: String,
    val relay: RelayEndpoint,
)

fun parseOfferFromUrl(url: String): ConnectionOffer? {
    val fragment = url.substringAfter("#offer=", "")
    if (fragment.isBlank()) return null
    return try {
        val json = String(
            Base64.decode(fragment, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
            Charsets.UTF_8,
        )
        DaemonJson.decodeFromString(ConnectionOffer.serializer(), json)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse offer from URL: ${url.take(80)}", e)
        null
    }
}
