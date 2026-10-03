package app.forge.gym.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.forge.gym.GymViewModel
import app.forge.gym.data.StateCodec
import app.forge.gym.model.*
import kotlinx.coroutines.*

@Composable fun SettingsScreen(state: AppState, vm: GymViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    var pendingSummary by remember { mutableStateOf("") }
    var recover by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val text = vm.exportBackup()
                withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Could not open export destination." }.bufferedWriter(Charsets.UTF_8).use { it.write(text) } }
                snackbar.showSnackbar("Backup exported.")
            } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Export failed.") }
            finally { busy = false }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val text = withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= StateCodec.MAX_BYTES) { "Backup must be under 20 MB." }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                }
                val decoded = withContext(Dispatchers.IO) { StateCodec.decode(text) }
                pendingSummary = "${decoded.sessions.size} sessions and all seven program days. This replaces the current data on this device."
                pendingBackup = text
            } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Could not read backup.") }
            finally { busy = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Keep it yours", "Settings", "Private training. Stored on your device.") }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item {
            ForgeCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Shield, null, tint = Green); Text("100% local", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) }
                Text("No accounts, networking, analytics, or cloud sync. Your workouts live in this app's private storage.", color = Muted, fontSize = 13.sp)
                HorizontalDivider(color = Outline)
                SettingRow("Interface", "English")
                SettingRow("Weight unit", "Kilograms (kg)")
                SettingRow("First weekday", "Sunday")
            }
        }
        item {
            ForgeCard {
                Text("Backup & recovery", fontWeight = FontWeight.SemiBold)
                Text("Export before uninstalling. Android removes the app's local data when you uninstall. Keep your backup somewhere you control.", color = Muted, fontSize = 12.sp)
                Button(onClick = { export.launch("Forge-backup-${java.time.LocalDate.now()}.json") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FileDownload, null); Text("Export JSON backup") }
                OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FileUpload, null); Text("Restore JSON backup") }
                OutlinedButton(onClick = { recover = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Restore, null); Text("Recover previous saved revision") }
                Text("Restore accepts Forge Android schema v1. Swift/iOS archives use a different format.", color = Muted, fontSize = 10.sp)
            }
        }
        item {
            ForgeCard {
                Text("Your original training notes", fontWeight = FontWeight.SemiBold)
                val imported = state.sessions.filter { it.importOrder != null }
                Row(Modifier.fillMaxWidth()) { Stat(imported.size.toString(), "IMPORTED ENTRIES", Modifier.weight(1f)); Stat(imported.count { it.needsReview }.toString(), "NEED REVIEW", Modifier.weight(1f)) }
                Text("${imported.sumOf { it.exercises.size }} exercises · ${imported.sumOf { it.exercises.sumOf { e -> e.sets.size } }} explicit sets · ${imported.count { it.date != null }} complete dates", color = Muted, fontSize = 11.sp)
                Text("Entries are source note blocks, not guaranteed distinct workouts. Missing years are unresolved; duplicates and uncertain values remain reviewable. Original notes are kept verbatim.", color = Muted, fontSize = 12.sp)
                TextButton(onClick = { source = true }, enabled = state.originalNotes.isNotBlank()) { Text("Read original source notes") }
            }
        }
        item {
            ForgeCard {
                Text("What the numbers mean", fontWeight = FontWeight.SemiBold)
                Text("Working volume = completed external weight × reps. Warm-ups and incomplete sets are excluded. Bodyweight sets use 0 kg external load.", color = Muted, fontSize = 12.sp)
                Text("Estimated 1RM uses Epley for 1–12 reps. A single rep uses its actual weight. It is an estimate, not a tested maximum.", color = Muted, fontSize = 12.sp)
                Text("Training max is planning metadata from your program or notes. It is separate from lifted weights.", color = Muted, fontSize = 12.sp)
                Text("Unreviewed imports do not count toward calendar, weekly totals, or dated charts. The imported chart option follows note order.", color = Muted, fontSize = 12.sp)
            }
        }
        item { Text("FORGE 0.0.1 · Native Android / Kotlin", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 14.dp)) }
    }
    if (pendingBackup != null) ConfirmDialog("Replace local data?", pendingSummary, "Restore", { pendingBackup = null }) { vm.importBackup(pendingBackup!!) }
    if (recover) ConfirmDialog("Recover previous revision?", "The current data will be replaced by the last valid saved revision. Export a backup first if you want to keep the current version.", "Recover", { recover = false }) { vm.recover() }
    if (source) Dialog(onDismissRequest = { source = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Navy, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) { IconButton(onClick = { source = false }) { Icon(Icons.Rounded.Close, "Close source notes") }; Text("Original training notes", fontWeight = FontWeight.Bold) }
                val lines = remember(state.originalNotes) { state.originalNotes.lines() }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) { items(lines) { line -> SelectionContainer { Text(line.ifBlank { " " }, fontSize = 12.sp, color = Muted) } } }
            }
        }
    }
}
@Composable private fun SettingRow(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Muted, fontSize = 12.sp); Text(value, fontSize = 12.sp) } }
