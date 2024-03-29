package org.cyanotic.butterfly.features.contact_list

import org.cyanotic.butterfly.protocol.Status

data class ContactListModel(
    val nickname: String?,
    val passport: String,
    val personalMessage: String,
    val status: Status,
    val profilePicture: String?,
    val filter: String,
    val contacts: List<ContactModel.Contact>
)

sealed class ContactModel {
    object Root : ContactModel()

    data class Category(
        val name: String
    ) : ContactModel()

    data class Contact(
        val nickname: String?,
        val passport: String,
        val personalMessage: String,
        val status: Status
    ) : ContactModel()

}
