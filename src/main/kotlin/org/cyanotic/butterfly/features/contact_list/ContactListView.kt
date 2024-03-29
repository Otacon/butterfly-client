package org.cyanotic.butterfly.features.contact_list

import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.geometry.Side
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.stage.Stage
import org.cyanotic.butterfly.core.*
import org.cyanotic.butterfly.features.add_contact.AddContactView
import org.cyanotic.butterfly.features.conversation.ConversationView
import org.cyanotic.butterfly.features.friend_request.FriendRequestView
import org.cyanotic.butterfly.features.login.LoginView
import org.cyanotic.butterfly.protocol.Status
import kotlin.system.exitProcess


class ContactListView(
    private val stage: Stage
) : ContactListContract.View {

    @FXML
    private lateinit var menuLogout: MenuItem

    @FXML
    private lateinit var menuExit: MenuItem

    @FXML
    private lateinit var profilePicture: ImageView

    @FXML
    private lateinit var nickname: TextField

    @FXML
    private lateinit var personalMessage: TextField

    @FXML
    private lateinit var contactsFilter: TextField

    @FXML
    private lateinit var contactList: TreeView<ContactModel>

    @FXML
    private lateinit var statusButton: Button

    @FXML
    private lateinit var addContactButton: Button

    private val statusImage = ImageView()

    private val statusOnline = Image("/images/status-online.png")
    private val statusAway = Image("/images/status-away.png")
    private val statusBusy = Image("/images/status-busy.png")
    private val statusOffline = Image("/images/status-offline.png")

    private val presenter = ContactListPresenter(
        this,
        ContactListInteractor(ButterflyClient)
    )
    private val contactsRoot = TreeItem<ContactModel>(ContactModel.Root)
    private val contactsOnline = TreeItem<ContactModel>(ContactModel.Category("Available"))
    private val contactsOffline = TreeItem<ContactModel>(ContactModel.Category("Offline"))
    private val openedConversations = mutableSetOf<ConversationView>()

    fun onCreate() {
        setupListeners()
        contactList.setCellFactory { ContactListCell() }
        contactList.isShowRoot = false
        contactsRoot.children.add(contactsOnline)
        contactsRoot.children.add(contactsOffline)
        contactsOnline.isExpanded = true
        contactsOffline.isExpanded = true
        contactList.root = contactsRoot
        setupStatusButton()
        val addContactIcon = ImageView(Image("/images/add-contact.png"))
        addContactIcon.fitWidth = 18.0
        addContactIcon.fitHeight = 18.0
        addContactButton.graphic = addContactIcon
        addContactButton.setOnMouseClicked {
            val result = AddContactView.launch(stage)
            presenter.onAddContactClosed(result)
        }
        statusImage.requestFocus()
        presenter.onCreate()
    }

    private fun setupStatusButton() {
        statusImage.fitHeight = 10.0
        statusImage.isPreserveRatio = true
        statusButton.graphic = statusImage
        val menu = ContextMenu()
        val online = MenuItem("Available").also { it.setOnAction { presenter.onStatusChanged(Status.ONLINE) } }
        val away = MenuItem("Busy").also { it.setOnAction { presenter.onStatusChanged(Status.BUSY) } }
        val idle = MenuItem("Away").also { it.setOnAction { presenter.onStatusChanged(Status.AWAY) } }
        val busy = MenuItem("Appear offline").also { it.setOnAction { presenter.onStatusChanged(Status.HIDDEN) } }
        menu.items.addAll(online, away, idle, busy)
        statusButton.setOnMouseClicked {
            menu.show(statusButton, Side.BOTTOM, 0.0, 0.0)
        }
    }

    private fun setupListeners() {
        personalMessage.setOnKeyPressed { event ->
            when (event.code) {
                KeyCode.ENTER -> presenter.onPersonalMessageChanged(personalMessage.text)
                KeyCode.ESCAPE -> {
                    presenter.onCancelPersonalMessage()
                    statusImage.requestFocus()
                }
                else -> {
                }
            }
        }
        contactList.setOnMouseClicked { event ->
            if (event.clickCount == 2) {
                val selectedItem = contactList.selectionModel.selectedItem
                when (val item = selectedItem.value) {
                    ContactModel.Root,
                    is ContactModel.Category -> {
                    }
                    is ContactModel.Contact -> presenter.onContactClick(item)
                }

            }
        }

        contactsFilter.textProperty().addListener { _, old, new ->
            if (old != new) {
                presenter.onContactFilterChanged(new)
            }
        }

        menuLogout.setOnAction {
            presenter.onLogoutClicked()
        }
        menuExit.setOnAction {
            presenter.onExitClicked()
        }
    }

    override fun setProfilePicture(picture: String?) {
        picture?.let {
            profilePicture.image = Image(picture)
        }
    }

    override fun setNickname(text: String) {
        nickname.text = text
    }

    override fun setPersonalMessage(text: String) {
        personalMessage.text = text
    }

    override fun setContacts(online: List<ContactModel.Contact>, offline: List<ContactModel.Contact>) {

        contactsOnline.children.clear()
        contactsOnline.children.addAll(online.map { TreeItem(it) })
        contactsOnline.value = ContactModel.Category("Available (${online.size})")

        contactsOffline.children.clear()
        contactsOffline.children.addAll(offline.map { TreeItem(it) })
        contactsOffline.value = ContactModel.Category("Offline (${offline.size})")

    }

    override fun openConversation(recipient: String) {
        val existingConversation = openedConversations.firstOrNull { it.recipient.equals(recipient,true) }
        if(existingConversation == null) {
            val conversationView = ConversationView.launch(recipient)
            openedConversations.add(conversationView)
            conversationView.onWindowClose = {
                openedConversations.remove(conversationView)
            }
        }
    }

    override fun setStatus(status: Status) {
        val image = when (status) {
            Status.ONLINE -> statusOnline
            Status.AWAY,
            Status.BE_RIGHT_BACK,
            Status.IDLE,
            Status.OUT_TO_LUNCH,
            Status.ON_THE_PHONE -> statusAway
            Status.BUSY -> statusBusy
            Status.OFFLINE,
            Status.HIDDEN -> statusOffline
        }
        statusImage.image = image
    }

    override fun openContactRequest(passport: String) {
        val result = FriendRequestView.launch(stage, passport)
        presenter.onContactRequestResult(result)
    }

    override fun openLogin() {
        LoginView.launch(stage, autoLogin = false)
    }

    override fun exit() {
        Platform.exit()
        exitProcess(0)
    }

    companion object {

        fun launch(stage: Stage) {
            val controller = ContactListView(stage)
            val root = FXMLLoader().apply {
                setController(controller)
                location = javaClass.getResource("/ContactList.fxml")
            }.load<Scene>()
            stage.scene = root
            stage.isResizable = true
            stage.show()
            controller.onCreate()
        }

    }
}