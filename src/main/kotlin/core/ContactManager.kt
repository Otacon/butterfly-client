package core

import core.contactListFetcher.ContactListFetcher
import core.utils.httpClient
import database.ContactsTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.orfeo.Contact
import protocol.notification.NotificationTransportManager
import kotlin.coroutines.CoroutineContext

object ContactManager : CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.IO

    private val localContacts = ContactsTable()
    private val contactListFetcher = ContactListFetcher(httpClient)
    private val accountManager = AccountManager
    private val notificationServiceManager = NotificationTransportManager

    init {
        listenForNotificationContactChanges()
    }

    suspend fun refreshContactList() {
        val currentAccount = accountManager.getCurrentAccount()
        val newContacts = contactListFetcher.getContacts(currentAccount.mspauth!!).map {
            Contact(
                passport = it.contactInfo.passportName,
                account = currentAccount.passport,
                nickname = it.contactInfo.displayName,
                personalMessage = null,
                status = null
            )
        }
        localContacts.update(currentAccount.passport, newContacts)
    }

    suspend fun ownContactUpdates(): Flow<Contact> {
        return localContacts.ownContactUpdates(accountManager.getCurrentAccount().passport)
    }

    suspend fun otherContactsUpdates(): Flow<List<Contact>> {
        return localContacts.otherContactsUpdates(accountManager.getCurrentAccount().passport)
    }

    private fun listenForNotificationContactChanges() {
        launch {
            notificationServiceManager.transport.contactChanged().collect { profileData ->
                val account = accountManager.getCurrentAccount().passport
                val updatedContact = Contact(
                    passport = profileData.passport,
                    account = account,
                    nickname = profileData.nickname,
                    personalMessage = profileData.personalMessage,
                    status = profileData.status
                )
                localContacts.update(account, listOf(updatedContact))
            }
        }
    }
}