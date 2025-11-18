@file:Suppress("PropertyName")

package vip

import kotlin.time.Clock
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement

@Serializable
data class GetSharedSecretResponse(
    val Status: Status,
    val SecretContainer: SecretContainer,
)

@Serializable
data class Status(
    @XmlElement(true) val StatusMessage: String,
)

@Serializable
data class SecretContainer(
    val EncryptionMethod: EncryptionMethod,
    val Device: Device,
)

@Serializable
data class EncryptionMethod(
    @XmlElement(true) val IV: String,
)

@Serializable data class Device(val Secret: Secret)

@Serializable
data class Secret(
    val Id: String,
    val Usage: Usage,
    val Data: Data,
)

@Serializable
data class Usage(
    val AI: AI,
    @XmlElement(true) val TimeStep: Int,
)

@Serializable data class AI(val type: String)

@Serializable data class Data(@XmlElement(true) val Cipher: String)

@Serializable
data class Token(
    val id: String,
    val secret: String,
    val period: Int = 30,
    val counter: Int? = null,
    val algorithm: String = "sha1",
    val digits: Int = 6,
) {
  /** Gets the remaining seconds until the current OTP expires. */
  val remainingSeconds
    get() = period - (Clock.System.now().epochSeconds % period).toInt()
}

sealed class TokenResult(val res: String) {
  data object Success : TokenResult("VIP Credential is working correctly")

  data object NeedsSync : TokenResult("VIP credential needs to be sync")

  data class Failed(val error: String) : TokenResult("")
}
