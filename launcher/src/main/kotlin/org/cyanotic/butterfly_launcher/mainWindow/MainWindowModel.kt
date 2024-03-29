package org.cyanotic.butterfly_launcher.mainWindow

import org.update4j.Configuration

data class MainWindowModel(
    val status: String,
    val progress: Int,
    val error: String?,
    val isLaunchButtonEnabled: Boolean,
    val isUpdateButtonEnabled: Boolean,
    val isOpenDataFolderButtonEnabled: Boolean,
    val isCheckForUpdatesButtonEnabled: Boolean,
    val configuration: Configuration?
)