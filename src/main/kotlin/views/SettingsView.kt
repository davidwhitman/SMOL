package views

import AppState
import SL
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.pop
import util.rootCause
import java.io.File
import javax.swing.JFileChooser

@OptIn(
    ExperimentalMaterialApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
@Preview
fun AppState.settingsView(
    modifier: Modifier = Modifier
) {
    Scaffold(topBar = {
        TopAppBar {
            Button(onClick = router::pop, modifier = Modifier.padding(start = 16.dp)) {
                Text("Back")
            }
        }
    }) {
        Box(modifier) {
            Column(Modifier.padding(16.dp)) {
                var gamePath by remember { mutableStateOf(SL.appConfig.gamePath ?: "") }
                var archivesPath by remember { mutableStateOf(SL.appConfig.archivesPath ?: "") }
                var stagingPath by remember { mutableStateOf(SL.appConfig.stagingPath ?: "") }
                var alertDialogMessage: String? by remember { mutableStateOf(null) }

                fun save(): Boolean {
                    SL.appConfig.gamePath = gamePath
                    SL.appConfig.archivesPath = archivesPath

                    kotlin.runCatching {
                        SL.staging.changeStagingPath(stagingPath)
                    }
                        .onFailure { ex ->
                            alertDialogMessage =
                                "${ex.rootCause()::class.simpleName}\n${ex.rootCause().message}"
                            return false
                        }

                    return true
                }

                if (alertDialogMessage != null) {
                    AlertDialog(
                        modifier = Modifier.width(400.dp),
                        title = { Text("Error") },
                        text = { alertDialogMessage?.let { Text(alertDialogMessage!!) } },
                        onDismissRequest = { alertDialogMessage = null },
                        confirmButton = { Button(onClick = { alertDialogMessage = null }) { Text("Ok") } }
                    )
                }

                LazyColumn(Modifier.weight(1f)) {
                    item {
                        Column {
                            gamePath = gamePathSetting(gamePath)
                            archivesPath = archivesPathSetting(archivesPath)
                            stagingPath = stagingPathSetting(stagingPath)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(modifier = Modifier.padding(end = 16.dp), onClick = {
                        if (save()) {
                            router.pop()
                        }
                    }) { Text("Ok") }
                    OutlinedButton(
                        modifier = Modifier.padding(end = 16.dp),
                        onClick = { router.pop() }) { Text("Cancel") }
                    OutlinedButton(onClick = { save() }) { Text("Apply") }
                }
            }
        }
    }
}


@Composable
private fun AppState.gamePathSetting(gamePath: String): String {
    var newGamePath by remember { mutableStateOf(gamePath) }
    var isGamePathError by remember { mutableStateOf(!SL.gamePath.isValidGamePath(newGamePath)) }

    Row {
        TextField(
            value = newGamePath,
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            label = { Text("Game path") },
            isError = isGamePathError,
            onValueChange = {
                newGamePath = it
                isGamePathError = !SL.gamePath.isValidGamePath(it)
            })
        Button(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 16.dp),
            onClick = {
                newGamePath =
                    pickFolder(initialPath = newGamePath.ifBlank { null }
                        ?: System.getProperty("user.home"),
                        window = window)
                        ?: newGamePath
            }) {
            Text("Open")
        }
    }
    if (isGamePathError) {
        Text("Invalid game path", color = MaterialTheme.colors.error)
    }

    return newGamePath
}

@Composable
private fun AppState.archivesPathSetting(archivesPath: String): String {
    fun isValidArchivesPath(path: String) = !File(path).exists()
    var isArchivesPathError by remember { mutableStateOf(isValidArchivesPath(archivesPath)) }
    var archivesPathMutable by remember { mutableStateOf(archivesPath) }

    Row {
        TextField(
            value = archivesPathMutable,
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            label = { Text("Archive storage path") },
            isError = isArchivesPathError,
            onValueChange = {
                archivesPathMutable = it
                isArchivesPathError = isValidArchivesPath(it)
            })
        Button(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 16.dp),
            onClick = {
                archivesPathMutable =
                    pickFolder(initialPath = archivesPathMutable.ifBlank { null }
                        ?: System.getProperty("user.home"),
                        window = window)
                        ?: archivesPathMutable
            }) {
            Text("Open")
        }
    }
    if (isArchivesPathError) {
        Text("Invalid path", color = MaterialTheme.colors.error)
    }

    return archivesPathMutable
}

@Composable
private fun AppState.stagingPathSetting(stagingPath: String): String {
    fun isValidArchivesPath(path: String) = !File(path).exists()
    var stagingPathMutable by remember { mutableStateOf(stagingPath) }
    var isStagingPathError = false

    Row {
        TextField(
            value = stagingPathMutable,
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            label = { Text("Staging path") },
            isError = isStagingPathError,
            onValueChange = {
                stagingPathMutable = it
//                isStagingPathError = isValidArchivesPath(it)
            })
        Button(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 16.dp),
            onClick = {
                stagingPathMutable =
                    pickFolder(initialPath = stagingPathMutable.ifBlank { null }
                        ?: System.getProperty("user.home"),
                        window = window)
                        ?: stagingPathMutable
            }) {
            Text("Open")
        }
    }
    if (isStagingPathError) {
        Text("Invalid path", color = MaterialTheme.colors.error)
    }

    return stagingPathMutable
}

private fun pickFolder(initialPath: String, window: ComposeWindow): String? {
    JFileChooser().apply {
        currentDirectory =
            File(initialPath)
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY

        return when (showOpenDialog(window)) {
            JFileChooser.APPROVE_OPTION -> this.selectedFile.absolutePath
            else -> null
        }
    }
}