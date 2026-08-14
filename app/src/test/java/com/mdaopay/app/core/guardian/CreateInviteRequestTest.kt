package com.mdaopay.app.core.guardian

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TD-13/4.3a: wire contract between app CreateInviteRequest and relay POST /guardian/invite.
 * Relay (relay/src/index.ts L135) requires non-empty guardianPubKeyX/Y; verifyP256Signature
 * (relay/src/auth.ts L2-9) strips an optional 0x prefix, so no-prefix 64-char hex is expected.
 */
class CreateInviteRequestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serializes guardianPubKeyX and guardianPubKeyY with relay field names`() {
        val pubKeyX = "a".repeat(64)
        val pubKeyY = "b".repeat(64)
        val request = CreateInviteRequest(
            walletAddress = "0x123",
            guardianLabel = "guardian-a",
            encryptedShare = "0xenc",
            shareIndex = 0,
            fcmToken = "tok",
            guardianPubKeyX = pubKeyX,
            guardianPubKeyY = pubKeyY
        )

        val obj = json.parseToJsonElement(json.encodeToString(request)).jsonObject
        assertEquals(pubKeyX, obj["guardianPubKeyX"]?.jsonPrimitive?.content)
        assertEquals(pubKeyY, obj["guardianPubKeyY"]?.jsonPrimitive?.content)
    }
}
