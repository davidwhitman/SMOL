package smol_app

import smol_access.SL
import smol_access.ServiceLocator
import smol_access.business.VmParamsManager
import smol_app.browser.DownloadManager
import smol_app.toasts.ToasterState
import smol_app.updater.UpdateApp
import smol_app.util.currentPlatform

var SL_UI = AppServiceLocator()

class AppServiceLocator internal constructor(
    val downloadManager: DownloadManager = DownloadManager(SL.access),
    val uiConfig: UIConfig = UIConfig(SL.jsanity),
    val toaster: ToasterState = ToasterState(),
    val vmParamsManager: VmParamsManager = VmParamsManager(gamePath = SL.gamePath, platform = currentPlatform),
    val updater: UpdateApp = UpdateApp(appConfig = SL.appConfig)
)

val ServiceLocator.UI
    get() = SL_UI