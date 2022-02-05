package smol_app

import smol_access.SL
import smol_access.ServiceLocator
import smol_access.business.VmParamsManager
import smol_app.browser.DownloadManager
import smol_app.toasts.ToasterState
import updatestager.Updater
import utilities.currentPlatform

var SL_UI = AppServiceLocator()

class AppServiceLocator internal constructor(
    val downloadManager: DownloadManager = DownloadManager(access = SL.access, gamePathManager = SL.gamePathManager),
    val uiConfig: UIConfig = UIConfig(gson = SL.jsanity),
    val toaster: ToasterState = ToasterState(),
    val vmParamsManager: VmParamsManager = VmParamsManager(gamePathManager = SL.gamePathManager, platform = currentPlatform),
    val updater: Updater = Updater(appConfig = SL.appConfig)
)

val ServiceLocator.UI
    get() = SL_UI