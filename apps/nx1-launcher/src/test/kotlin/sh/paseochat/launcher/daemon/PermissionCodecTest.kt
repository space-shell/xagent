package sh.paseochat.launcher.daemon

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Exact wire-format tests for permission responses (review finding F-01:
 * a selected deny action was hardcoded to "behavior":"allow").
 */
class PermissionCodecTest {

    private fun encode(msg: JsonObject): String =
        sh.paseochat.launcher.daemon.models.DaemonJson
            .encodeToString(JsonObject.serializer(), msg)

    private fun decode(json: String): JsonObject =
        sh.paseochat.launcher.daemon.models.DaemonJson
            .parseToJsonElement(json).jsonObject

    private fun envelopeOf(json: String): JsonObject =
        decode(json)["message"]!!.jsonObject

    private fun responseOf(json: String): JsonObject =
        envelopeOf(json)["response"]!!.jsonObject

    @Test
    fun `allow without action`() {
        val json = encode(PermissionCodec.permissionResponse("a1", "r1", allow = true))
        val message = envelopeOf(json)
        assertEquals("agent_permission_response", message["type"]!!.jsonPrimitive.content)
        assertEquals("a1", message["agentId"]!!.jsonPrimitive.content)
        assertEquals("r1", message["requestId"]!!.jsonPrimitive.content)
        val response = responseOf(json)
        assertEquals("allow", response["behavior"]!!.jsonPrimitive.content)
    }

    @Test
    fun `deny without action`() {
        val json = encode(PermissionCodec.permissionResponse("a1", "r1", allow = false))
        assertEquals("deny", responseOf(json)["behavior"]!!.jsonPrimitive.content)
    }

    @Test
    fun `selected allow action keeps allow behavior`() {
        val json = encode(
            PermissionCodec.permissionResponseWithAction("a1", "r1", "act_yes", allow = true),
        )
        val response = responseOf(json)
        assertEquals("allow", response["behavior"]!!.jsonPrimitive.content)
        assertEquals("act_yes", response["selectedActionId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `selected deny action is encoded as deny — F-01 regression`() {
        val json = encode(
            PermissionCodec.permissionResponseWithAction("a1", "r1", "act_no", allow = false),
        )
        val response = responseOf(json)
        assertEquals(
            "deny",
            response["behavior"]?.jsonPrimitive?.contentOrNull,
            "selecting a server-provided deny action must transmit behavior=deny",
        )
        assertEquals("act_no", response["selectedActionId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `custom answer`() {
        val json = encode(
            PermissionCodec.permissionResponseWithCustomAnswer("a1", "r1", "do the thing"),
        )
        val response = responseOf(json)
        assertEquals("allow", response["behavior"]!!.jsonPrimitive.content)
        assertEquals(
            "do the thing",
            response["updatedInput"]!!.jsonObject["answer"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `question answers map onto headers`() {
        val json = encode(
            PermissionCodec.permissionResponseWithQuestionAnswers(
                "a1", "r1", mapOf("Scope" to "repo only", "Confirm" to "yes"),
            ),
        )
        val answers = responseOf(json)["updatedInput"]!!.jsonObject["answers"]!!.jsonObject
        assertEquals("repo only", answers["Scope"]!!.jsonPrimitive.content)
        assertEquals("yes", answers["Confirm"]!!.jsonPrimitive.content)
    }

    @Test
    fun `envelope shape is stable`() {
        val json = encode(PermissionCodec.permissionResponse("a", "r", allow = true))
        val root = decode(json)
        assertEquals("session", root["type"]!!.jsonPrimitive.content)
        assertNotNull(root["message"])
    }
}
