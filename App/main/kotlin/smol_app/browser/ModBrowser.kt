/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package smol_app.browser

import AppScope
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mod_repo.ScrapedMod
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.tinylog.kotlin.Logger
import smol_access.Constants
import smol_access.SL
import smol_app.ModBrowserState
import smol_app.UI
import smol_app.browser.chromium.CefBrowserPanel
import smol_app.browser.chromium.ChromiumBrowser
import smol_app.composables.*
import smol_app.navigation.Screen
import smol_app.themes.SmolTheme
import smol_app.toolbar.toolbar
import smol_app.util.*
import timber.ktx.Timber
import utilities.Platform
import utilities.currentPlatform
import java.awt.Cursor
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.*

private val modListMinWidthDp = 600.dp

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalSplitPaneApi::class
)
@Composable
@Preview
fun AppScope.ModBrowserView(
    modifier: Modifier = Modifier,
    defaultUrl: String? = null
) {
    val scrapedMods = SL.modRepo.items.collectAsState().value
    val shownMods = remember { mutableStateListOf<ScrapedMod?>(elements = scrapedMods.toTypedArray()) }

    val browser = remember { mutableStateOf<ChromiumBrowser?>(null) }
    val linkLoader = remember { mutableStateOf<((String) -> Unit)?>(null) }
    var alertDialogMessage: String? by remember { mutableStateOf(null) }
    val showLogPanel = remember { mutableStateOf(false) }
    var isBrowserFullscreen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(modifier = Modifier.height(SmolTheme.topBarHeight)) {
                toolbar(router.state.value.activeChild.instance as Screen)
                Spacer(Modifier.weight(1f))

                // Doesn't seem to work, just restart the app to reload, it only happens once a day anyway.
//                SmolTooltipArea(
//                    modifier = Modifier
//                        .padding(end = 8.dp),
//                    tooltip = { SmolTooltipText(text = "Fetch latest mod cache.") }) {
//                    IconButton(
//                        modifier = Modifier,
//                        onClick = {
//                            coroutineScope.launch {
//                                kotlin.runCatching { SL.modRepo.refreshFromInternet() }
//                                    .onFailure { Timber.w(it) }
//                            }
//                        }
//                    ) {
//                        Icon(
//                            imageVector = Icons.Default.Refresh,
//                            contentDescription = null,
//                            modifier = Modifier
//                                .width(24.dp)
//                                .height(24.dp),
//                            tint = SmolTheme.dimmedIconColor()
//                        )
//                    }
//                }
                SmolTooltipArea(
                    modifier = Modifier
                        .padding(end = 8.dp),
                    tooltip = { SmolTooltipText(text = "Open in an external browser") }) {
                    IconButton(
                        onClick = {
                            runCatching {
                                browser.value?.currentUrl?.value?.first?.openAsUriInBrowser()
                            }
                                .onFailure { Logger.warn(it) }
                        }
                    ) {
                        Icon(
                            painter = painterResource("icon-web.svg"),
                            contentDescription = null,
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp)
                                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                        )
                    }
                }
                SmolTooltipArea(
                    modifier = Modifier,
                    tooltip = { SmolTooltipText(text = "Toggle full-width browser") }) {
                    IconButton(
                        onClick = {
                            isBrowserFullscreen = !isBrowserFullscreen
                        }
                    ) {
                        Icon(
                            painter = painterResource("icon-maximize.svg"),
                            contentDescription = null,
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
        }, content = {
            Column(Modifier.padding(bottom = SmolTheme.bottomBarHeight - 8.dp)) {
                Row(modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
                    val splitterState = rememberSplitPaneState(
                        initialPositionPercentage = SL.UI.uiConfig.modBrowserState?.modListWidthPercent ?: 0f
                    )
                    HorizontalSplitPane(splitPaneState = splitterState) {
                        if (!isBrowserFullscreen) {
                            first(modListMinWidthDp) {
                                LaunchedEffect(splitterState.positionPercentage) {
                                    // Update config file on recompose
                                    SL.UI.uiConfig.modBrowserState =
                                        SL.UI.uiConfig.modBrowserState?.copy(modListWidthPercent = splitterState.positionPercentage)
                                            ?: ModBrowserState(modListWidthPercent = splitterState.positionPercentage)
                                }
                                Column {
                                    Row(Modifier.padding(bottom = 16.dp)) {
                                        Row {
                                            smolSearchField(
                                                modifier = Modifier
                                                    .focusRequester(searchFocusRequester())
                                                    .widthIn(max = 320.dp)
                                                    .padding(end = 16.dp),
                                                tooltipText = "Hotkey: Ctrl-F",
                                                label = "Filter"
                                            ) { query ->
                                                if (query.isBlank()) {
                                                    shownMods.replaceAllUsingDifference(
                                                        scrapedMods,
                                                        doesOrderMatter = false
                                                    )
                                                } else {
                                                    shownMods.replaceAllUsingDifference(
                                                        filterModPosts(query, scrapedMods).ifEmpty { listOf(null) },
                                                        doesOrderMatter = true
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.weight(1f))

                                        Column(modifier = Modifier.align(Alignment.Bottom)) {
                                            SmolTooltipArea(
                                                modifier = Modifier
                                                    .padding(vertical = 4.dp, horizontal = 16.dp)
                                                    .align(Alignment.End),
                                                tooltip = {
                                                    SmolTooltipText(text = buildString {
                                                        appendLine("The Mod Browser lists mods scraped from the official forum and from the Unofficial Discord Chat, with permission.")
                                                        appendLine("The list is <b>not</b> live; it is fetched from an online cache, which is updated periodically so as to avoid excessive load on the forum.")
                                                        append("If a mod has been added to the forum but doesn't yet show up in the list, simply navigate to it using the browser and download it.")
                                                    }.parseHtml())
                                                }) {
                                                Icon(
                                                    painter = painterResource("icon-help-circled.svg"),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .width(24.dp)
                                                        .height(24.dp),
                                                    tint = SmolTheme.dimmedIconColor()
                                                )
                                            }
                                            val lastUpdated = SL.modRepo.lastUpdated.collectAsState().value
                                            Text(
                                                modifier = Modifier.padding(top = 8.dp, end = 16.dp),
                                                text = "Mod list generated on: ${
                                                    lastUpdated?.format(
                                                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                                                            .withZone(ZoneId.systemDefault())
                                                    )?.plus(
                                                        " " + ZoneId.systemDefault()
                                                            .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                                    ) ?: "unknown"
                                                }",
                                                style = MaterialTheme.typography.overline
                                            )
                                        }
                                    }

                                    val scrollState = rememberLazyListState()
                                    Row {
                                        LazyVerticalGrid(
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            cells = GridCells.Adaptive(200.dp),
                                            state = scrollState,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            this.items(
                                                items = shownMods
                                                    .filterNotNull()
                                                    .sortedWith(compareBy { it.name })
                                            ) { mod ->
                                                scrapedModCard(mod, linkLoader)
                                            }
                                        }
                                        VerticalScrollbar(
                                            adapter = ScrollbarAdapter(scrollState),
                                            modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        second {
                            Column {
                                Row(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                                    var canGoBack by remember { mutableStateOf(browser.value?.canGoBack ?: false) }
                                    var canGoForward by remember {
                                        mutableStateOf(
                                            browser.value?.canGoForward ?: false
                                        )
                                    }
                                    SmolSecondaryButton(
                                        modifier = Modifier.padding(start = 8.dp)
                                            .align(Alignment.CenterVertically),
                                        onClick = { linkLoader.value?.invoke(Constants.FORUM_MOD_INDEX_URL) }
                                    ) { Text("Index") }
                                    SmolSecondaryButton(
                                        modifier = Modifier.padding(start = 8.dp)
                                            .align(Alignment.CenterVertically),
                                        onClick = { linkLoader.value?.invoke(Constants.FORUM_MODDING_SUBFORUM_URL) }
                                    ) { Text("Modding") }
                                    IconButton(
                                        modifier = Modifier.padding(start = 8.dp)
                                            .align(Alignment.CenterVertically),
                                        enabled = canGoBack,
                                        onClick = { browser.value?.goBack() }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = null
                                        )
                                    }
                                    IconButton(
                                        modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                                            .align(Alignment.CenterVertically),
                                        enabled = canGoForward,
                                        onClick = { browser.value?.goForward() }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = null
                                        )
                                    }
                                    var enteredUrl by remember { mutableStateOf("") }
                                    LaunchedEffect(Unit) {
                                        browser.value?.currentUrl?.collect {
                                            enteredUrl = it.first
                                            canGoBack = browser.value?.canGoBack ?: false
                                            canGoForward = browser.value?.canGoForward ?: false
                                        }
                                    }
                                    SmolOutlinedTextField(
                                        modifier = Modifier.weight(1f)
                                            .align(Alignment.CenterVertically)
                                            .onEnterKeyPressed {
                                                browser.value?.loadUrl(enteredUrl)
                                                true
                                            },
                                        value = enteredUrl,
                                        onValueChange = { enteredUrl = it },
                                        label = { Text("Address") },
                                        maxLines = 1,
                                        singleLine = true
                                    )
                                }
                                embeddedBrowser(browser, linkLoader, defaultUrl ?: Constants.FORUM_MOD_INDEX_URL)
                            }
                        }
                        horizontalSplitter()
                    }
                }
            }

            if (showLogPanel.value) {
                logPanel { showLogPanel.value = false }
            }
        },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.fillMaxWidth()
            ) {
                logButtonAndErrorDisplay(showLogPanel = showLogPanel)
            }
        }
    )

    if (alertDialogMessage != null) {
        SmolAlertDialog(
            onDismissRequest = { alertDialogMessage = null },
            text = { Text(text = alertDialogMessage ?: "", style = SmolTheme.alertDialogBody()) }
        )
    }

    LaunchedEffect(defaultUrl) {
        if (defaultUrl != null) {
            Timber.i { "Loading Mod Browser with default url $defaultUrl." }
            browser.value?.loadUrl(defaultUrl)
        }
    }
}

class ModBrowserLinkLoader(private val linkLoader: MutableState<((String) -> Unit)?>) : UriHandler {
    override fun openUri(uri: String) {
        linkLoader.value?.invoke(uri)
    }
}

@Composable
private fun AppScope.embeddedBrowser(
    browser: MutableState<ChromiumBrowser?>,
    linkLoader: MutableState<((String) -> Unit)?>,
    startUrl: String
) {
    val background = MaterialTheme.colors.background
    val useCEF = true

    if (useCEF) {
        SwingPanel(
            background = MaterialTheme.colors.background,
            modifier = Modifier
                .padding(start = 16.dp)
                .fillMaxHeight()
                .fillMaxWidth()
                .sizeIn(minWidth = 200.dp),
            factory = {
                CefBrowserPanel(
                    startURL = startUrl,
                    useOSR = Platform.Linux == currentPlatform,
                    isTransparent = false,
                    downloadHandler = object : DownloadHander {

                        override fun onStart(
                            itemId: String,
                            suggestedFileName: String?,
                            totalBytes: Long
                        ) {
                            val item = DownloadItem(
                                id = itemId
                            )
                                .apply {
                                    this.path.value = getDownloadPathFor(suggestedFileName)
                                    this.totalBytes.value = totalBytes
                                }
                            SL.UI.downloadManager.addDownload(item)
                        }

                        override fun onProgressUpdate(
                            itemId: String,
                            progressBytes: Long?,
                            totalBytes: Long?,
                            speedBps: Long?,
                            endTime: Date
                        ) {
                            SL.UI.downloadManager.downloads.value.firstOrNull { it.id == itemId }
                                ?.let { download ->
                                    runBlocking {
                                        if (progressBytes != null)
                                            download.progressBytes.emit(progressBytes)
                                        if (speedBps != null)
                                            download.bitsPerSecond.emit(speedBps)
                                    }
                                    if (download.status.value is DownloadItem.Status.NotStarted)
                                        runBlocking {
                                            download.status.emit(DownloadItem.Status.Downloading)
                                        }
                                }
                        }

                        override fun onCanceled(itemId: String) {
                            SL.UI.downloadManager.downloads.value.firstOrNull { it.id == itemId }
                                ?.let { download ->
                                    runBlocking {
                                        download.status.emit(
                                            DownloadItem.Status.Failed(
                                                RuntimeException(
                                                    "Download canceled."
                                                )
                                            )
                                        )
                                    }
                                }
                        }

                        override fun onCompleted(itemId: String) {
                            SL.UI.downloadManager.downloads.value.firstOrNull { it.id == itemId }
                                ?.let { download ->
                                    runBlocking {
                                        if (download.totalBytes.value != null)
                                            download.progressBytes.emit(download.totalBytes.value ?: 0)
                                        download.status.emit(DownloadItem.Status.Completed)
                                    }

                                    if (download.path.value != null) {
                                        GlobalScope.launch(Dispatchers.IO) {
                                            val destinationFolder = SL.gamePathManager.getModsPath()
                                            if (destinationFolder != null) {
                                                SL.access.installFromUnknownSource(
                                                    inputFile = download.path.value!!,
                                                    destinationFolder = destinationFolder
                                                )
                                                SL.access.reload()
                                            }
                                        }
                                    }
                                }
                        }

                        override fun getDownloadPathFor(filename: String?): Path =
                            Constants.TEMP_DIR.resolve(
                                filename?.ifEmpty { null } ?: UUID.randomUUID().toString())
                    }
                )
                    .also { browserPanel ->
                        browser.value = browserPanel
                        linkLoader.value = { url ->
                            browserPanel.loadUrl(url)
                        }
                    }
            }
        )
    } else {
//        javaFxBrowser(jfxpanel, background, linkLoader)
    }
}

//@OptIn(ExperimentalFoundationApi::class)
//@Composable
//fun downloadBar(
//    modifier: Modifier = Modifier,
//) {
//    val downloads = SL.UI.downloadManager.downloads.collectAsState().value
//
//    LazyRow(modifier) {
//        items(downloads) { download ->
//            downloadCard(
//                download = download,
//                requestToastDismissal = {}
//            )
//        }
//    }
//}