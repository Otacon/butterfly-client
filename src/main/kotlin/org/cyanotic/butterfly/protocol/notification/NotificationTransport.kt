package org.cyanotic.butterfly.protocol.notification

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.cyanotic.butterfly.protocol.ProtocolVersion
import org.cyanotic.butterfly.protocol.utils.Arch
import org.cyanotic.butterfly.protocol.utils.LocaleId
import org.cyanotic.butterfly.protocol.utils.OSType
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class TransportException(
    val code: Int
) : Exception()

private val logger = KotlinLogging.logger("Notification")

@ExperimentalCoroutinesApi
@FlowPreview
class NotificationTransport {

    private val socket: NotificationSocket = NotificationSocket()
    private val parser = ReceiveCommandParser()
    private val commandFactory = CommandFactory()
    private val continuations: MutableMap<Int, Continuation<NotificationReceiveCommand>> = mutableMapOf()
    private var continuationMspAuthToken: Continuation<String>? = null
    private var sequence: Int = 1
    private val contactChanged = BroadcastChannel<ProfileData>(capacity = Channel.CONFLATED)
    private val contactRequest = BroadcastChannel<ContactRequest>(capacity = Channel.CONFLATED)
    private val switchboardInvites = BroadcastChannel<SwitchboardInvite>(capacity = Channel.CONFLATED)

    fun connect(endpoint: String, port: Int) {
        socket.connect(endpoint, port)
        GlobalScope.launch {
            var reading = true
            while (reading) {
                val message = socket.readMessage()
                if (message != null) {
                    processMessage(message)
                } else {
                    logger.warn { "Socket closed." }
                    reading = false
                }
            }
        }
    }

    fun disconnect() {
        socket.close()
    }

    suspend fun sendVer(protocols: List<ProtocolVersion>): NotificationReceiveCommand.VER =
        suspendCoroutine { cont ->
            val protocolsStr = protocols.joinToString(" ") {
                when (it) {
                    ProtocolVersion.MSNP18 -> "MSNP18"
                    ProtocolVersion.UNKNOWN -> ""
                }
            }
            sendMessage("VER $sequence $protocolsStr", cont)
        }

    suspend fun sendCvr(
        locale: LocaleId,
        osType: OSType,
        osVersion: String,
        arch: Arch,
        clientName: String,
        clientVersion: String,
        passport: String
    ): NotificationReceiveCommand.CVR =
        suspendCoroutine { cont ->
            val message =
                commandFactory.createCvr(sequence, locale, osType, osVersion, arch, clientName, clientVersion, passport)
            sendMessage(message, cont)
        }

    suspend fun sendUsrSsoInit(passport: String): NotificationReceiveCommand.USRSSOStatus =
        suspendCoroutine { cont ->
            val message = commandFactory.createUsrSsoInit(sequence, passport)
            sendMessage(message, cont)
        }

    suspend fun sendUsrSSOStatus(
        nonce: String,
        encryptedToken: String,
        machineGuid: UUID
    ): NotificationReceiveCommand.USRSSOAck =
        suspendCoroutine { cont ->
            val message = commandFactory.createUsrSsoStatus(sequence, nonce, encryptedToken, machineGuid)
            sendMessage(message, cont)
        }

    suspend fun sendChg(status: String): NotificationReceiveCommand.CHG =
        suspendCoroutine { cont ->
            //TODO set the client's capabilities
            val capabilities = 0x90000000
            val message = commandFactory.createChg(sequence, status, capabilities)
            sendMessage(message, cont)
        }

    suspend fun sendXfr(): NotificationReceiveCommand.XFR =
        suspendCoroutine { cont ->
            val message = commandFactory.createXfr(sequence)
            sendMessage(message, cont)
        }

    suspend fun sendUux(psm: String): NotificationReceiveCommand.UUX =
        suspendCoroutine { cont ->
            //TODO add support to CurrentMedia
            val message = commandFactory.createUux(sequence, psm, "")
            continuations[sequence] = cont as Continuation<NotificationReceiveCommand>
            socket.sendMessage(message, sendNewLine = false)
            sequence++
        }

    suspend fun sendAdl(email: String, list: ListType, contact: ContactType): NotificationReceiveCommand.ADL =
        suspendCoroutine { cont ->
            val emailRegex = Regex("""([a-zA-Z0-9+._-]+)@([a-zA-Z0-9._-]+)""")
            emailRegex.find(email)?.let {
                val prefix = it.groupValues[1]
                val domain = it.groupValues[2]
                val message = commandFactory.createAdl(sequence, prefix, domain, list, contact)
                sendMessage(message, cont)
            } ?: cont.resumeWithException(IllegalArgumentException("Invalid email: $email"))
        }

    suspend fun sendFln(account: String, networkId: String) {
        //TODO
    }

    suspend fun waitForMspAuthToken(): String =
        suspendCoroutine { cont ->
            continuationMspAuthToken = cont
        }

    fun contactChanged() = contactChanged.asFlow()

    fun contactRequests() = contactRequest.asFlow()

    fun switchboardInvites() = switchboardInvites.asFlow()

    private fun sendMessage(message: String, continuation: Continuation<*>) {
        continuations[sequence] = continuation as Continuation<NotificationReceiveCommand>
        socket.sendMessage(message)
        sequence++
    }

    private fun processMessage(message: String) {
        when (val command = parser.parse(message)) {
            is NotificationReceiveCommand.VER -> resumeContinuation(command.sequence, command)
            is NotificationReceiveCommand.USRSSOStatus -> resumeContinuation(command.sequence, command)
            is NotificationReceiveCommand.CVR -> resumeContinuation(command.sequence, command)
            is NotificationReceiveCommand.GCF -> socket.readRaw(command.length)
            is NotificationReceiveCommand.USRSSOAck -> resumeContinuation(command.sequence, command)
            is NotificationReceiveCommand.MSG -> {
                val profileInfo = socket.readRaw(command.length)
                val token = parseProfileInfo(profileInfo)
                token?.let {
                    continuationMspAuthToken?.resume(token)
                    continuationMspAuthToken = null
                }
            }
            is NotificationReceiveCommand.UBX -> {
                if (command.length > 0) {
                    val body = socket.readRaw(command.length)
                    val data = UbxBodyParser().parse(body)
                    val profileData = ProfileData(
                        passport = command.email,
                        personalMessage = data.personalMessage ?: "",
                        nickname = null,
                        status = null
                    )
                    contactChanged.offer(profileData)
                }
            }
            is NotificationReceiveCommand.CHG -> resumeContinuation(command.sequence, command)
            is NotificationReceiveCommand.RNG -> switchboardInvites.offer(
                SwitchboardInvite(
                    command.sessionId,
                    command.address,
                    command.port,
                    command.passport,
                    command.auth
                )
            )
            is NotificationReceiveCommand.XFR -> resumeContinuation(command.sequence, command)
            is NotificationReceiveCommand.NLN -> {
                val profileData = ProfileData(
                    passport = command.passport,
                    status = command.status,
                    nickname = command.displayName,
                    personalMessage = null,
                )
                contactChanged.offer(profileData)
            }
            is NotificationReceiveCommand.FLN -> {
                val profileData = ProfileData(
                    passport = command.passport,
                    status = "FLN",
                    nickname = null,
                    personalMessage = null,
                )
                contactChanged.offer(profileData)
            }
            is NotificationReceiveCommand.ADL -> {
                resumeContinuation(command.sequence, command)
            }
            is NotificationReceiveCommand.ADLAccept -> {
                if (command.length > 0) {
                    val body = socket.readRaw(command.length)
                    val newContact = AdlBodyParser().parse(body)
                    val invite = ContactRequest(
                        passport = "${newContact.data.contact.name}@${newContact.data.emailDomain}",
                        nickname = newContact.data.contact.displayName
                    )
                    contactRequest.offer(invite)
                }
            }
            is NotificationReceiveCommand.NOT -> {
                if (command.length > 0) {
                    socket.readRaw(command.length)
                }
                println("NT - NOT command received. Undocumented behaviour: SKIPPING.")
            }
            is NotificationReceiveCommand.Error -> resumeErrorContinuation(command.sequence, command)
            is NotificationReceiveCommand.Unknown -> println("NT - Command not supported: $message")
        }
    }

    private fun resumeContinuation(sequence: Int, command: NotificationReceiveCommand) {
        continuations[sequence]!!.resume(command)
        continuations.remove(sequence)
    }

    private fun resumeErrorContinuation(sequence: Int, error: NotificationReceiveCommand.Error) {
        continuations[sequence]!!.resumeWithException(TransportException(error.code))
    }

    private fun parseProfileInfo(content: String): String? {
        val lines = content.split("\n")
        val keyValues = lines.mapNotNull {
            if (it.isBlank()) {
                null
            } else {
                val keyValue = it.split(": ")
                if (keyValue.size == 1) {
                    keyValue[0] to null
                } else {
                    keyValue[0] to keyValue[1].trim()
                }
            }
        }.toMap()
        val contentType = keyValues["Content-Type"].orEmpty()
        if (contentType.contains("text/x-msmsgsprofile")) {
            val profile = ProfileInformation(
                MIMEVersion = keyValues["MIME-Version"].orEmpty(),
                loginTime = keyValues["LoginTime"]?.toLong() ?: -1,
                emailEnabled = keyValues["EmailEnabled"] == "1",
                memberHighId = keyValues["MemberIdHigh"]?.toLong() ?: -1,
                memberLowId = keyValues["MemberIdLow"]?.toLong() ?: -1,
                langPreference = keyValues["lang_preference"]?.toInt() ?: -1,
                preferredEmail = keyValues["preferredEmail"],
                country = keyValues["country"],
                postalCode = keyValues["PostalCode"],
                gender = keyValues["Gender"],
                isKid = keyValues["Kid"] == "1",
                age = keyValues["Age"].orEmpty(),
                birthDayPresent = keyValues["BDayPre"].orEmpty(),
                birthday = keyValues["Birthday"].orEmpty(),
                wallet = keyValues["Wallet"].orEmpty(),
                flags = keyValues["Flags"]?.toInt() ?: -1,
                sid = keyValues["sid"]?.toInt() ?: -1,
                mspAuth = keyValues["MSPAuth"].orEmpty(),
                clientIp = keyValues["ClientIP"].orEmpty(),
                clientPort = keyValues["ClientPort"]?.toInt() ?: -1,
                abchMigrated = keyValues["ABCHMigrated"] == "1",
                mpopEnabled = keyValues["MPOPEnabled"] == "1"
            )
            return profile.mspAuth
        }
        return null
    }

}

data class ProfileInformation(
    val MIMEVersion: String,
    val loginTime: Long,
    val emailEnabled: Boolean,
    val memberHighId: Long,
    val memberLowId: Long,
    val langPreference: Int,
    val preferredEmail: String?,
    val country: String?,
    val postalCode: String?,
    val gender: String?,
    val isKid: Boolean,
    val age: String,
    val birthDayPresent: String,
    val birthday: String,
    val wallet: String,
    val flags: Int,
    val sid: Int,
    val mspAuth: String,
    val clientIp: String,
    val clientPort: Int,
    val abchMigrated: Boolean,
    val mpopEnabled: Boolean
)

data class ProfileData(
    val passport: String,
    val nickname: String?,
    val personalMessage: String?,
    val status: String?
)

data class ContactRequest(
    val passport: String,
    val nickname: String?
)

data class SwitchboardInvite(
    val sessionId: String,
    val address: String,
    val port: Int,
    val passport: String,
    val auth: String
)

enum class ListType {
    AddList,
    ForwardList,
    BlockList
}

enum class ContactType {
    Passport,
    Phone
}