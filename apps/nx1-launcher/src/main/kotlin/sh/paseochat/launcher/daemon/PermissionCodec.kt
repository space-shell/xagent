package sh.paseochat.launcher.daemon

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Pure encoders for agent_permission_response envelopes.
 *
 * Extracted from PaseoDaemonClient so the exact wire format is unit-testable
 * (F-01 regression: a selected action with deny behavior must be encoded as
 * "deny", never coerced to "allow").
 */
object PermissionCodec {

    private fun envelope(
        agentId: String,
        requestId: String,
        response: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): JsonObject = kotlinx.serialization.json.buildJsonObject {
        put("type", "session")
        putJsonObject("message") {
            put("type", "agent_permission_response")
            put("agentId", agentId)
            put("requestId", requestId)
            putJsonObject("response", response)
        }
    }

    fun permissionResponse(agentId: String, requestId: String, allow: Boolean): JsonObject =
        envelope(agentId, requestId) {
            put("behavior", if (allow) "allow" else "deny")
        }

    fun permissionResponseWithAction(
        agentId: String,
        requestId: String,
        actionId: String,
        allow: Boolean,
    ): JsonObject = envelope(agentId, requestId) {
        put("behavior", if (allow) "allow" else "deny")
        put("selectedActionId", actionId)
    }

    fun permissionResponseWithCustomAnswer(
        agentId: String,
        requestId: String,
        answer: String,
    ): JsonObject = envelope(agentId, requestId) {
        put("behavior", "allow")
        putJsonObject("updatedInput") {
            put("answer", answer)
        }
    }

    fun permissionResponseWithQuestionAnswers(
        agentId: String,
        requestId: String,
        answers: Map<String, String>,
    ): JsonObject = envelope(agentId, requestId) {
        put("behavior", "allow")
        putJsonObject("updatedInput") {
            putJsonObject("answers") {
                answers.forEach { (header, answer) -> put(header, answer) }
            }
        }
    }
}
