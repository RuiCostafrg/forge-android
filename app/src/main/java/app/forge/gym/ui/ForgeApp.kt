package app.forge.gym.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.forge.gym.GymViewModel
import kotlinx.coroutines.delay

@Composable fun ForgeApp(vm: GymViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val startupProblem by vm.startupProblem.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openSession by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { for (message in vm.messages) snackbar.showSnackbar(message) }
    val workout = state.sessions.firstOrNull { it.id == openSession }
    BackHandler(openSession != null) { openSession = null }
    MaterialTheme(colorScheme = ForgeColors) {
        Scaffold(containerColor = Navy, snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (openSession == null) NavigationBar(containerColor = Navy, tonalElevation = 0.dp) {
                    val names = listOf("Overview", "Sessions", "Program", "Settings")
                    val icons = listOf(Icons.Rounded.Dashboard, Icons.Rounded.CalendarMonth, Icons.Rounded.FitnessCenter, Icons.Rounded.Tune)
                    names.forEachIndexed { i, name -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], name) }, label = { Text(name, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Green.copy(alpha = .14f), selectedIconColor = Green, selectedTextColor = Green, unselectedIconColor = Muted, unselectedTextColor = Muted)) }
                }
            }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                if (startupProblem != null) Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(16.dp)) { Text(startupProblem!!); TextButton(onClick = { openSession = null; tab = 3 }) { Text("Open recovery settings") } }
                }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Green)
                if (state.restEndsAt != null) RestBanner(state.restEndsAt!!, onStop = { vm.change { it.copy(restEndsAt = null) } })
                if (openSession != null && workout != null) SessionEditor(workout, state, vm, onClose = { openSession = null })
                else when (tab) {
                    0 -> Overview(state, vm, onOpen = { openSession = it })
                    1 -> SessionsScreen(state, vm, onOpen = { openSession = it })
                    2 -> ProgramScreen(state, vm)
                    else -> SettingsScreen(state, vm, snackbar)
                }
            }
        }
    }
}
@Composable private fun RestBanner(end: Long, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(end) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val remaining = ((end - now + 999) / 1000).coerceAtLeast(0).toInt()
    Surface(color = Green.copy(alpha = .13f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Timer, null, tint = Green)
            Text(if (remaining > 0) "Rest · ${clock(remaining)}" else "Rest complete · ready for your next set", color = Green, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            TextButton(onClick = onStop) { Text(if (remaining > 0) "Skip" else "Done") }
        }
    }
}
