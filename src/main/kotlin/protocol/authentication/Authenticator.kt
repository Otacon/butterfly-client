package protocol.authentication

import core.ProfileManager.changeStatus
import core.ProfileManager.onUserInfoChanged
import core.ProfileManager.passport
import core.Status
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import protocol.Endpoints
import protocol.ProtocolVersion
import protocol.notification.NotificationTransport
import protocol.security.TicketEncoder
import protocol.soap.RequestSecurityTokenParser
import protocol.utils.SystemInfoRetriever
import java.util.*

class Authenticator(
    private val systemInfoRetriever: SystemInfoRetriever,
    private val transport: NotificationTransport,
    private val okHttpClient: OkHttpClient,
    private val ticketEncoder: TicketEncoder,
    private val multipleSecurityTokensRequestFactory: RequestMultipleSecurityTokensRequestFactory,
    private val requestSecurityTokenParser: RequestSecurityTokenParser
) {
    var clientName = "Escargot Messenger"
    var clientVersion = "1.0 (in-dev)"

    suspend fun authenticate(username: String, password: String): AuthenticationResult {
        transport.connect()

        val verResponse = transport.sendVer(
            protocols = listOf(ProtocolVersion.MSNP18)
        )

        val systemInfo = systemInfoRetriever.getSystemInfo()

        val cvrResponse = transport.sendCvr(
            locale = systemInfo.locale,
            osType = systemInfo.osType,
            osVersion = systemInfo.osVersion,
            arch = systemInfo.arch,
            clientName = clientName,
            clientVersion = clientVersion,
            passport = username
        )

        val usrResponse = transport.sendUsrSSOInit(username)

        val requestBody = multipleSecurityTokensRequestFactory.createRequest(
            username = username,
            password = password
        ).toRequestBody("application/xml".toMediaType())

        val request = Request.Builder()
            .url(Endpoints.RSTUrl)
            .post(requestBody)
            .build()

        val response = okHttpClient
            .newCall(request)
            .execute()

        val xml = response.body!!.string()
        val token = requestSecurityTokenParser.parse(xml)
        val decodedToken = ticketEncoder.encode(token!!.secret, usrResponse.nonce)

        transport.sendUsrSSOStatus(
            nonce = token.nonce,
            encryptedToken = decodedToken,
            machineGuid = UUID.randomUUID()
        )
        transport.waitForMsgHotmail()
        changeStatus(Status.ONLINE)
        passport = username
        onUserInfoChanged?.invoke()
        return AuthenticationResult.Success
    }
}

sealed class AuthenticationResult {
    object UnsupportedProtocol : AuthenticationResult()
    object Success: AuthenticationResult()
}