package org.cyanotic.butterfly_launcher.mainWindow

import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.math.roundToInt
import kotlin.system.exitProcess

class MainWindowPresenter(
    private val view: MainWindowContract.View,
    private val interactor: MainWindowInteractor
) : MainWindowContract.Presenter {

    private val executor = Executors.newSingleThreadExecutor()
    private var model = MainWindowModel(
        status = "",
        progress = 0,
        error = null,
        isLaunchButtonEnabled = false,
        isUpdateButtonEnabled = false,
        isOpenDataFolderButtonEnabled = false,
        isCheckForUpdatesButtonEnabled = false,
        configuration = null
    )

    override fun onCreate() = executor.execute {
        downloadConfigurationAndCheckForUpdates()
    }

    override fun onLaunchClicked() = executor.execute {
        SwingUtilities.invokeLater { view.close() }
        model.configuration!!.launch()
        exitProcess(-1)
    }

    override fun onUpdateClicked() = executor.execute {
        model = model.copy(
            status = "Updating Escargot...",
            error = null,
            progress = -1,
            isUpdateButtonEnabled = false,
            isLaunchButtonEnabled = false,
            isOpenDataFolderButtonEnabled = false,
            isCheckForUpdatesButtonEnabled = false
        )
        updateUI()
        val success = interactor.performUpdate(model.configuration!!) { progress ->
            model = model.copy(progress = (progress * 100).roundToInt())
            updateUI()
        }
        model = if (success) {
            model.copy(
                status = "Escargot is now up to date!",
                progress = 0,
                isUpdateButtonEnabled = false,
                isLaunchButtonEnabled = true,
                isOpenDataFolderButtonEnabled = true,
                isCheckForUpdatesButtonEnabled = true
            )
        } else {
            model.copy(
                status = "",
                progress = 0,
                error = "Unable to update Escargot.\nCheck your connection or remove all files in the data folder.",
                isUpdateButtonEnabled = true,
                isOpenDataFolderButtonEnabled = true,
                isCheckForUpdatesButtonEnabled = true
            )
        }
        updateUI()
    }

    override fun onOpenFilesClicked() {
        view.openFileManager(interactor.getAppHome())
    }

    override fun onWindowFocussed() = executor.execute {
        checkForUpdates()
    }

    override fun onCheckForUpdatesClicked() = executor.execute {
        downloadConfigurationAndCheckForUpdates()
    }

    private fun downloadConfigurationAndCheckForUpdates() {
        model = model.copy(
            status = "Checking for new versions...",
            progress = -1,
            isLaunchButtonEnabled = false,
            isUpdateButtonEnabled = false,
            isOpenDataFolderButtonEnabled = false,
            isCheckForUpdatesButtonEnabled = false,
        )
        updateUI()
        val configuration = interactor.getConfiguration()

        if (configuration == null) {
            model = model.copy(
                progress = 0,
                error = "Unable to check for updates. Please try again.",
                isOpenDataFolderButtonEnabled = true,
                isCheckForUpdatesButtonEnabled = true
            )
            updateUI()
        } else {
            model = model.copy(
                progress = 0,
                configuration = configuration
            )
            checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        val configuration = model.configuration!!
        val status = if (configuration.requiresUpdate()) {
            "A new version is available."
        } else {
            "Escargot is up to date."
        }
        model = model.copy(
            status = status,
            isLaunchButtonEnabled = !configuration.requiresUpdate(),
            isUpdateButtonEnabled = configuration.requiresUpdate(),
            isOpenDataFolderButtonEnabled = true,
            isCheckForUpdatesButtonEnabled = true,
            progress = 0,
            configuration = configuration
        )
        updateUI()
    }

    private fun updateUI() = SwingUtilities.invokeLater {
        view.apply {
            setStatus(model.status)
            setProgress(model.progress)
            setError(model.error)
            showError(model.error != null)
            setLaunchButtonEnabled(model.isLaunchButtonEnabled)
            setUpdateButtonEnabled(model.isUpdateButtonEnabled)
            setRemoveDataButtonEnabled(model.isOpenDataFolderButtonEnabled)
            setCheckForUpdatesButtonEnabled(model.isCheckForUpdatesButtonEnabled)
        }
    }

}