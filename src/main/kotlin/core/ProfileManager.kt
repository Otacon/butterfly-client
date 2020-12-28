package core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import protocol.ProtocolVersion
import protocol.notification.NotificationSendCommand
import protocol.notification.NotificationTransportManager
import protocol.security.TicketEncoder
import protocol.soap.RequestSecurityTokenParser
import java.util.*

object ProfileManager {

    var onStatusChanged: (() -> Unit)? = null
    var onUserInfoChanged: (() -> Unit)? = null

    var passport: String = ""
    var nickname: String = ""
    var token: String = ""
    var status: Status = Status.OFFLINE
    var personalMessage: String = ""

    suspend fun login(username: String, password: String) {
        val transport = NotificationTransportManager.transport
        transport.connect()
        val verResponse = transport.sendVer(NotificationSendCommand.VER(listOf(ProtocolVersion.MSNP18)))
        val cvrResponse = transport.sendCvr(
            NotificationSendCommand.CVR(
                "0x0809",
                "WINNT",
                "6.2.0",
                "i386",
                "CyanoMSGR",
                "1.0.0",
                username
            )
        )
        val usrResponse = transport.sendUsrSSOInit(NotificationSendCommand.USRSSOInit(username))
        val requestBody = DOC.replace("!username", username)
            .replace("!password", password)
            .toRequestBody("application/xml".toMediaType())
        val request = Request.Builder()
            .url("https://m1.escargot.log1p.xyz/RST.srf")
            .post(requestBody)
            .build()
        val httpClient = OkHttpClient.Builder().build()
        val response = httpClient.newCall(request).execute()
        val xml = response.body!!.string()
        val token = RequestSecurityTokenParser().parse(xml)
        val decodedToken = TicketEncoder().encode(token!!.secret, usrResponse.nonce)
        val authResponse = transport.sendUsrSSOStatus(
            NotificationSendCommand.USRSSOStatus(
                token.nonce,
                decodedToken,
                UUID.randomUUID().toString()
            )
        )
        transport.waitForMsgHotmail()
        changeStatus(Status.ONLINE)
        passport = username
        onUserInfoChanged?.invoke()
    }

    suspend fun changeStatus(status: Status) {
        val literalStatus = when (status) {
            Status.ONLINE -> "NLN"
            Status.AWAY -> "AWY"
            Status.BE_RIGHT_BACK -> "BRB"
            Status.IDLE -> "IDL"
            Status.OUT_TO_LUNCH -> "LUN"
            Status.ON_THE_PHONE -> "PHN"
            Status.BUSY -> "BSY"
            Status.OFFLINE -> "FLN"
            Status.HIDDEN -> "HDN"
        }
        val transport = NotificationTransportManager.transport
        transport.sendChg(NotificationSendCommand.CHG(literalStatus))
        this.status = status
        onStatusChanged?.invoke()
    }

    suspend fun changeNick(nick: String) {

    }

    suspend fun changePersonalMessage(personalMessage: String) {

    }

}

val DOC = """
    <Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/"
       xmlns:wsse="http://schemas.xmlsoap.org/ws/2003/06/secext"
       xmlns:saml="urn:oasis:names:tc:SAML:1.0:assertion"
       xmlns:wsp="http://schemas.xmlsoap.org/ws/2002/12/policy"
       xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
       xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/03/addressing"
       xmlns:wssc="http://schemas.xmlsoap.org/ws/2004/04/sc"
       xmlns:wst="http://schemas.xmlsoap.org/ws/2004/04/trust">
       <Header>
           <ps:AuthInfo
               xmlns:ps="http://schemas.microsoft.com/Passport/SoapServices/PPCRL"
               Id="PPAuthInfo">
               <ps:HostingApp>{7108E71A-9926-4FCB-BCC9-9A9D3F32E423}</ps:HostingApp>
               <ps:BinaryVersion>4</ps:BinaryVersion>
               <ps:UIVersion>1</ps:UIVersion>
               <ps:Cookies></ps:Cookies>
               <ps:RequestParams>AQAAAAIAAABsYwQAAAAxMDMz</ps:RequestParams>
           </ps:AuthInfo>
           <wsse:Security>
               <wsse:UsernameToken Id="user">
                   <wsse:Username>!username</wsse:Username>
                   <wsse:Password>!password</wsse:Password>
               </wsse:UsernameToken>
           </wsse:Security>
       </Header>
       <Body>
           <ps:RequestMultipleSecurityTokens
               xmlns:ps="http://schemas.microsoft.com/Passport/SoapServices/PPCRL"
               Id="RSTS">
               <wst:RequestSecurityToken Id="RST0">
                   <wst:RequestType>http://schemas.xmlsoap.org/ws/2004/04/security/trust/Issue</wst:RequestType>
                   <wsp:AppliesTo>
                       <wsa:EndpointReference>
                           <wsa:Address>http://Passport.NET/tb</wsa:Address>
                       </wsa:EndpointReference>
                   </wsp:AppliesTo>
               </wst:RequestSecurityToken>
               <wst:RequestSecurityToken Id="RSTn">
                   <wst:RequestType>http://schemas.xmlsoap.org/ws/2004/04/security/trust/Issue</wst:RequestType>
                   <wsp:AppliesTo>
                       <wsa:EndpointReference>
                           <wsa:Address>messengerclear.live.com</wsa:Address>
                       </wsa:EndpointReference>
                   </wsp:AppliesTo>
                   <wsse:PolicyReference URI="policy parameter"></wsse:PolicyReference>
               </wst:RequestSecurityToken>
           </ps:RequestMultipleSecurityTokens>
       </Body>
    </Envelope>
""".trimIndent()

enum class Status {
    ONLINE,
    AWAY,
    BE_RIGHT_BACK,
    IDLE,
    OUT_TO_LUNCH,
    ON_THE_PHONE,
    BUSY,
    OFFLINE,
    HIDDEN
}



