package org.cyanotic.butterfly.core.contact_list_fetcher

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.cyanotic.butterfly.protocol.soap.*
import org.simpleframework.xml.Serializer
import org.simpleframework.xml.core.Persister
import java.io.StringWriter

class ContactListFetcher(
    private val httpClient: OkHttpClient
) {

    suspend fun getContacts(mspAuth: String): List<AbFindAllContact> {
        val persister: Serializer = Persister()
        val envelope = ABFindAllEnvelope(
            ABFindAllHeader(
                AbApplicationHeader(
                    applicationId = "CFE80F9D-180F-4399-82AB-413F33A1FA11",
                    isMigration = false,
                    partnerScenario = "Initial"
                ),
                AbAuthHeader(
                    managedGroupRequests = false
                )
            ),
            AbFindAllBody(
                AbFindAll(
                    abId = "00000000-0000-0000-0000-000000000000",
                    abView = "Full",
                    deltasOnly = false,
                    lastChange = "0001-01-01T00:00:00.0000000-08:00"
                )
            )
        )
        val writer = StringWriter()
        writer.write("<?xml version=\"1.0\"?>\n")
        persister.write(envelope, writer)
        val soapRequestBody = writer.buffer.toString()
        val request = Request.Builder()
            .url("https://m1.escargot.log1p.xyz/abservice/abservice.asmx")
            .post(body = soapRequestBody.toRequestBody("text/xml".toMediaType()))
            .addHeader("SOAPAction", "http://www.msn.com/webservices/AddressBook/ABFindAll")
            .addHeader("Cookie", "MSPAuth=$mspAuth")
            .build()
        val response = httpClient.newCall(request).execute()

        val serializer: Serializer = Persister()
        val soapResponseBody = response.body!!.string()
        val contacts = serializer.read(AbFindAllResponseEnvelope::class.java, soapResponseBody)
        return contacts.body.findAllResponse.findAllResponse.findAllResponse
    }

    suspend fun addContact(passport: String, token: String, ownNickname: String) {
        val persister: Serializer = Persister()
        val envelope = AddContactRequestEnvelope(
            AddContactRequestHeader(
                AddContactRequestABApplicationHeader(
                    applicationId = "CFE80F9D-180F-4399-82AB-413F33A1FA11",
                    isMigration = false,
                    partnerScenario = "ContactSave"
                ),
                AddContactRequestAbAuthHeader(
                    managedGroupRequests = false,
                    ticketToken = "t=$token&p="
                )
            ),
            AddContactRequestBody(
                AddContactRequestContent(
                    abId = "00000000-0000-0000-0000-000000000000",
                    contacts = arrayListOf(
                        AddContactRequestContact(
                            contactInfo = AddContactRequestContactInfo(
                                type = "LivePending",
                                passportName = passport,
                                isMessengerUser = true,
                                messengerMemberInfo = AddContactRequestMessengerMemberInfo(
                                    ownNickname
                                )
                            )
                        )
                    )
                )
            )
        )
        val writer = StringWriter()
        writer.write("<?xml version=\"1.0\"?>\n")
        persister.write(envelope, writer)
        val soapRequestBody = writer.buffer.toString()
        val request = Request.Builder()
            .url("https://m1.escargot.log1p.xyz/abservice/abservice.asmx")
            .post(body = soapRequestBody.toRequestBody("text/xml".toMediaType()))
            .addHeader("SOAPAction", "http://www.msn.com/webservices/AddressBook/ABContactAdd")
            .addHeader("Cookie", "MSPAuth=$token")
            .build()
        val response = httpClient.newCall(request).execute()
        val soapResponseBody = response.body!!.string()
        println(soapResponseBody)
    }
}