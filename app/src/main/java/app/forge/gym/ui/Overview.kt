package app.forge.gym.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.forge.gym.GymViewModel
import app.forge.gym.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable fun Overview(state: AppState, vm: GymViewModel, onOpen: (String) -> Unit) {
    val today = LocalDate.now()
    val plan = state.program.first { it.day == sundayIndex(today) }
    var selectedType by rememberSaveable(today.toEpochDay(), plan.type.name) { mutableStateOf(if (plan.type == WorkoutType.REST) WorkoutType.CUSTOM else plan.type) }
    val weekStart = today.minusDays(sundayIndex(today).toLong()).toEpochDay()
    val week = state.sessions.filter { it.included && it.date!! in weekStart..weekStart + 6 }
    val drafts = state.sessions.filter { !it.complete && !it.needsReview }.sortedByDescending { it.startedAt }
    val recent = state.sessions.filter { it.included }.sortedByDescending { it.date }.take(3)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Forge / personal training", "Build your stronger.", today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)), action = { Tag("ON DEVICE", true) }) }
        item {
            Surface(color = Color(0xFF203B31), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Green.copy(alpha = .25f))) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("TODAY'S PLAN", color = Green, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.FitnessCenter, null, tint = Green)
                    }
                    Text(if (plan.type == WorkoutType.REST) "Recover. Come back stronger." else "${plan.type.label} day", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(if (plan.type == WorkoutType.REST) "A little rest goes a long way. You can still start a custom session." else "${plan.exercises.size} exercises · ${plan.exercises.sumOf { it.sets }} planned sets", color = Color(0xFFB2C8BB))
                    TypePicker(selectedType, { selectedType = it }, includeRest = false)
                    Button(onClick = { vm.start(selectedType, onOpen) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start session", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (drafts.isNotEmpty()) {
            item { SectionTitle("Continue your session") }
            items(drafts.take(3), key = { "draft-${it.id}" }) { SessionCard(it, onOpen) }
        }
        item {
            SectionTitle("This week")
            ForgeCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    weekDays.forEachIndexed { i, day ->
                        val trained = week.any { it.date == weekStart + i }
                        val isToday = today.toEpochDay() == weekStart + i
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(day.take(1), color = Muted, fontSize = 12.sp)
                            Surface(color = if (trained) Green else if (isToday) Outline else Navy, shape = RoundedCornerShape(10.dp), border = if (isToday) BorderStroke(1.dp, Green) else null) {
                                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                    if (trained) Icon(Icons.Rounded.Check, "$day trained", tint = Navy, modifier = Modifier.size(18.dp))
                                    else Text(LocalDate.ofEpochDay(weekStart + i).dayOfMonth.toString(), color = if (isToday) Green else Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = Outline, modifier = Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Stat(week.size.toString(), "SESSIONS", Modifier.weight(1f)); Stat(week.sumOf { it.workingSets }.toString(), "WORKING SETS", Modifier.weight(1f)); Stat(number(week.sumOf { it.volume }), "VOLUME / KG", Modifier.weight(1.3f))
                }
            }
        }
        item { SectionTitle("Your progression") }
        item { ProgressCard(state, onOpen) }
        item { SectionTitle("Recent sessions") }
        if (recent.isEmpty()) item { EmptyState(Icons.Rounded.History, "Your next session starts the story.", "Finish a workout to see it here. Imported notes stay in Review until you confirm them.") }
        items(recent, key = { "recent-${it.id}" }) { SessionCard(it, onOpen) }
        item { Text("Local by design. No account. No cloud sync.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp)) }
    }
}
@Composable fun ProgressCard(state: AppState, onOpen: (String) -> Unit) {
    var exerciseId by rememberSaveable { mutableStateOf("bench") }
    var metric by rememberSaveable { mutableStateOf(Metric.TOP_WEIGHT) }
    var includeImported by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val library = state.exercises.ifEmpty { exerciseLibrary }
    val exercise = library.firstOrNull { it.id == exerciseId } ?: library.first()
    val points = remember(state, exercise.id, metric, includeImported) { Training.progress(state, exercise.id, metric, includeImported).takeLast(80) }
    var selected by remember(points) { mutableIntStateOf((points.size - 1).coerceAtLeast(0)) }
    ForgeCard {
        Box {
            TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp)) { Text(exercise.name, fontWeight = FontWeight.SemiBold); Icon(Icons.Rounded.ExpandMore, null) }
            DropdownMenu(menu, { menu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                library.forEach { e -> DropdownMenuItem(text = { Text(e.name) }, onClick = { exerciseId = e.id; menu = false }) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Metric.entries.forEach { m -> FilterChip(selected = metric == m, onClick = { metric = m }, label = { Text(m.label, fontSize = 11.sp) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Include imported history", color = Muted, modifier = Modifier.weight(1f), fontSize = 12.sp)
            Switch(includeImported, { includeImported = it })
        }
        Text(if (includeImported) "Imported points use source note order, then new sessions. This is not a date timeline." else "Confirmed finished sessions in date order.", color = Muted, fontSize = 11.sp)
        if (points.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Text("No recorded data for this metric yet.", color = Muted) }
        } else {
            val point = points[selected.coerceIn(points.indices)]
            Row(verticalAlignment = Alignment.Bottom) {
                Text(number(point.value), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Green)
                Text(" kg", color = Muted, modifier = Modifier.padding(bottom = 7.dp))
                Spacer(Modifier.weight(1f)); Text(point.label, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 7.dp))
            }
            val max = (points.maxOf { it.value } * 1.12).coerceAtLeast(1.0)
            Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "${metric.label} chart, ${points.size} records. Selected ${point.label}, ${number(point.value)} kilograms." }
                .pointerInput(points) { detectTapGestures { selected = ((it.x / size.width) * (points.size - 1)).roundToInt().coerceIn(points.indices) } }) {
                val padding = 8.dp.toPx()
                val width = size.width - 2 * padding
                val height = size.height - 2 * padding
                repeat(4) { i -> val y = padding + height * i / 3; drawLine(Outline, Offset(padding, y), Offset(size.width - padding, y), 1.dp.toPx()) }
                fun pos(i: Int): Offset = Offset(padding + if (points.size == 1) width / 2 else width * i / (points.size - 1), padding + height * (1 - points[i].value / max).toFloat())
                val path = Path().apply { points.indices.forEach { i -> val p = pos(i); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                drawPath(path, Green, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                points.indices.forEach { drawCircle(Green, 3.dp.toPx(), pos(it)) }
                drawCircle(Navy, 6.dp.toPx(), pos(selected)); drawCircle(Green, 6.dp.toPx(), pos(selected), style = Stroke(2.dp.toPx()))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { selected = (selected - 1).coerceAtLeast(0) }, enabled = selected > 0) { Text("Previous") }
                TextButton(onClick = { onOpen(point.sessionId) }) { Text("Open session") }
                TextButton(onClick = { selected = (selected + 1).coerceAtMost(points.lastIndex) }, enabled = selected < points.lastIndex) { Text("Next") }
            }
        }
    }
}
