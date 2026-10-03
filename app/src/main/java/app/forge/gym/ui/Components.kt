package app.forge.gym.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.forge.gym.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val Navy = Color(0xFF0C1220)
val CardColor = Color(0xFF161F30)
val Green = Color(0xFFBCED78)
val Muted = Color(0xFF9AA8BC)
val Outline = Color(0xFF2B374B)
val ForgeColors = darkColorScheme(primary = Green, onPrimary = Navy, background = Navy, onBackground = Color(0xFFF2F5FA),
    surface = CardColor, onSurface = Color(0xFFF2F5FA), surfaceVariant = Color(0xFF1B273B), onSurfaceVariant = Muted,
    outline = Outline, secondary = Green, error = Color(0xFFFFACAD))
fun number(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
fun dateLabel(day: Long?): String = day?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) } ?: "Date needs review"
fun clock(seconds: Int): String = "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)

@Composable fun PageTitle(eyebrow: String, title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp)) {
        Text(eyebrow.uppercase(Locale.ENGLISH), color = Green, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        if (subtitle != null) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun SectionTitle(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); action?.invoke()
    }
}
@Composable fun ForgeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = CardColor, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Outline)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable fun Tag(text: String, accent: Boolean = false) {
    Surface(color = if (accent) Green.copy(alpha = .12f) else Color(0xFF233047), shape = RoundedCornerShape(7.dp)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), fontSize = 11.sp, color = if (accent) Green else Muted, fontWeight = FontWeight.Medium)
    }
}
@Composable fun EmptyState(icon: ImageVector, title: String, message: String, action: (@Composable () -> Unit)? = null) {
    ForgeCard {
        Icon(icon, null, tint = Green, modifier = Modifier.size(30.dp))
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Text(message, color = Muted)
        action?.invoke()
    }
}
@Composable fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}
@Composable fun ConfirmDialog(title: String, message: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
@Composable fun TypePicker(type: WorkoutType, onType: (WorkoutType) -> Unit, includeRest: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(type.label, modifier = Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, "Choose workout type")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            WorkoutType.entries.filter { includeRest || it != WorkoutType.REST }.forEach { t ->
                DropdownMenuItem(text = { Text(t.label) }, onClick = { open = false; onType(t) })
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ExercisePicker(library: List<Exercise>, excluded: Set<String>, onDismiss: () -> Unit, onPick: (Exercise) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var custom by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf("Custom") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Navy) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (custom) "Custom exercise" else "Add exercise", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(search, { if (it.length <= 80) search = it }, label = { Text(if (custom) "Exercise name" else "Search exercises") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (custom) {
                OutlinedTextField(group, { if (it.length <= 40) group = it }, label = { Text("Muscle group") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onPick(Exercise(newId(), search.trim(), group.trim().ifBlank { "Custom" })); onDismiss() }, enabled = search.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create & add") }
                TextButton(onClick = { custom = false }) { Text("Back to library") }
            } else {
                TextButton(onClick = { custom = true }) { Icon(Icons.Rounded.Add, null); Text("Create a custom exercise") }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(library.filter { it.id !in excluded && (it.name.contains(search, true) || it.group.contains(search, true)) }, key = { it.id }) { e ->
                        ListItem(headlineContent = { Text(e.name) }, supportingContent = { Text(e.group) }, trailingContent = { Icon(Icons.Rounded.Add, null, tint = Green) },
                            modifier = Modifier.clickable { onPick(e); onDismiss() }, colors = ListItemDefaults.colors(containerColor = Navy))
                    }
                }
            }
        }
    }
}
@Composable fun SessionCard(w: Workout, onOpen: (String) -> Unit) {
    ForgeCard(Modifier.clickable { onOpen(w.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Green.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.FitnessCenter, null, tint = Green, modifier = Modifier.padding(12.dp).size(20.dp)) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(w.name, fontWeight = FontWeight.SemiBold)
                Text(if (w.needsReview) w.originalDate.ifBlank { "Undated note" } else dateLabel(w.date), color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ChevronRight, "Open session", tint = Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag(if (w.needsReview) "Needs review" else if (w.complete) "Finished" else "Draft", accent = !w.needsReview)
            Tag("${w.workingSets} working sets")
            if (w.volume > 0) Tag("${number(w.volume)} kg")
        }
    }
}
