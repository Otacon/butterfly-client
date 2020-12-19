import features.EscargotApplication
import javafx.application.Application.launch
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import protocol.NotificationTransport
import protocol.ProtocolVersion
import protocol.commands.SendCommand
import protocol.security.TicketEncoder
import protocol.soap.RequestSecurityTokenParser
import java.lang.Exception
import java.lang.Thread.sleep
import java.util.*


fun main(args: Array<String>) {
    try {
        launch(EscargotApplication::class.java)
    } catch (e: Exception){
        e.printStackTrace()
    }
    val transport = NotificationTransport()
    transport.connect()
    runBlocking {
        val verResponse = transport.sendVer(SendCommand.VER(listOf(ProtocolVersion.MSNP18)))
        val cvrResponse = transport.sendCvr(SendCommand.CVR("0x0809", "WINNT", "6.2.0", "i386", "CyanoMSGR", "1.0.0", "orfeo18@hotmail.it"))
        val usrResponse = transport.sendUsrSSOInit(SendCommand.USRSSOInit("orfeo18@hotmail.it"))
        val parser = RequestSecurityTokenParser()
        val httpClient = OkHttpClient.Builder()
            .build()
        val request = Request.Builder()
            .url("https://m1.escargot.log1p.xyz/RST.srf")
            .post(DOC.toRequestBody("application/xml".toMediaType()))
            .build()
        val response = httpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val xml = response.body!!.string()
            println("XML: $xml")
            val token = parser.parse(xml)
            println("Token: $token")
            val decodedToken = TicketEncoder().encode(token!!.secret, usrResponse.nonce)
            val authResponse = transport.sendUsrSSOStatus(SendCommand.USRSSOStatus(token.nonce, decodedToken, UUID.randomUUID().toString()))
            println(authResponse)
            sleep(100000)
        }
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
                   <wsse:Username>orfeo18@hotmail.it</wsse:Username>
                   <wsse:Password>bt5DIT1d</wsse:Password>
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