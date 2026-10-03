package app.forge.gym.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.forge.gym.GymViewModel
import app.forge.gym.model.*

@Composable fun ProgramScreen(state: AppState, vm: GymViewModel) {
    var editingDay by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Plan with purpose", "Weekly program", "Your split. Your schedule. Sunday first.") }
        items(state.program.sortedBy { it.day }, key = { it.day }) { day ->
            ForgeCard(Modifier.clickable { editingDay = day.day }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(weekDays[day.day], color = Muted, fontSize = 11.sp); Text(day.type.label, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
                    if (day.day == sundayIndex(java.time.LocalDate.now())) Tag("TODAY", true)
                    IconButton(onClick = { editingDay = day.day }) { Icon(Icons.Rounded.Edit, "Edit ${weekDays[day.day]}", tint = Green) }
                }
                Text(if (day.type == WorkoutType.REST) "Recovery day" else "${day.exercises.size} exercises · ${day.exercises.sumOf { it.sets }} sets · ${day.restSeconds}s rest", color = Muted, fontSize = 12.sp)
                if (day.type != WorkoutType.REST) Text(day.exercises.take(3).joinToString(" · ") { p -> state.exercises.firstOrNull { it.id == p.exerciseId }?.name ?: "Exercise" }, fontSize = 12.sp)
            }
        }
        item { Text("Editing your program changes future sessions. Existing session logs keep their exercises and targets.", color = Muted, fontSize = 12.sp) }
    }
    editingDay?.let { day -> DayEditor(state.program.first { it.day == day }, state.exercises, onDismiss = { editingDay = null }, onSave = { plan, custom -> vm.saveDay(plan, custom); editingDay = null }) }
}
@Composable private fun DayEditor(initial: DayPlan, library: List<Exercise>, onDismiss: () -> Unit, onSave: (DayPlan, List<Exercise>) -> Unit) {
    var type by remember { mutableStateOf(initial.type) }
    var exercises by remember { mutableStateOf(initial.exercises) }
    var custom by remember { mutableStateOf(emptyList<Exercise>()) }
    var note by remember { mutableStateOf(initial.note) }
    var rest by remember { mutableIntStateOf(initial.restSeconds) }
    var picker by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Navy, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Cancel editing") }
                    Text(weekDays[initial.day], fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onSave(DayPlan(initial.day, type, exercises, rest, note), custom) }, enabled = type == WorkoutType.REST || exercises.isNotEmpty()) { Text("Save") }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { TypePicker(type, { type = it }) }
                    item { Text("Workout type changes keep the exercise list. Use defaults to replace it.", color = Muted, fontSize = 11.sp) }
                    item { OutlinedButton(onClick = { reset = true }, modifier = Modifier.fillMaxWidth()) { Text("Use ${type.label.lowercase()} defaults") } }
                    items(exercises, key = { it.exerciseId }) { target ->
                        val exercise = (library + custom).first { it.id == target.exerciseId }
                        ForgeCard {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text(exercise.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); IconButton(onClick = { exercises = exercises.filter { it.exerciseId != target.exerciseId } }) { Icon(Icons.Rounded.DeleteOutline, "Remove ${exercise.name}") } }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text("SETS", color = Muted, fontSize = 10.sp); Stepper(target.sets, 1..30) { count -> exercises = exercises.map { if (it.exerciseId == target.exerciseId) it.copy(sets = count) else it } } }
                                Column { Text("REP TARGET", color = Muted, fontSize = 10.sp); Stepper(target.reps, 1..1000) { count -> exercises = exercises.map { if (it.exerciseId == target.exerciseId) it.copy(reps = count) else it } } }
                            }
                            val index = exercises.indexOf(target)
                            Row {
                                IconButton(onClick = { exercises = exercises.toMutableList().apply { java.util.Collections.swap(this, index, index - 1) } }, enabled = index > 0) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
                                IconButton(onClick = { exercises = exercises.toMutableList().apply { java.util.Collections.swap(this, index, index + 1) } }, enabled = index < exercises.lastIndex) { Icon(Icons.Rounded.ArrowDownward, "Move down") }
                            }
                        }
                    }
                    item { OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Text("Add exercise") } }
                    item {
                        ForgeCard {
                            Text("Rest between sets · ${rest}s", fontWeight = FontWeight.SemiBold)
                            Slider(rest.toFloat(), { rest = (it / 15).toInt() * 15 }, valueRange = 15f..600f, steps = 38)
                            OutlinedTextField(note, { if (it.length <= 1000) note = it }, label = { Text("Program notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        }
                    }
                }
            }
        }
        if (picker) ExercisePicker(library + custom, exercises.map { it.exerciseId }.toSet(), { picker = false }, { e ->
            if (library.none { it.id == e.id }) custom = custom + e
            exercises = exercises + PlanExercise(e.id)
        })
        if (reset) ConfirmDialog("Replace exercises?", "The exercise list for this day will be replaced with ${type.label.lowercase()} defaults.", "Replace", { reset = false }) { exercises = defaultExercises(type) }
    }
}
@Composable private fun Stepper(value: Int, range: IntRange, onValue: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onValue(value - 1) }, enabled = value > range.first) { Icon(Icons.Rounded.Remove, "Decrease") }
        Text(value.toString(), fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onValue(value + 1) }, enabled = value < range.last) { Icon(Icons.Rounded.Add, "Increase") }
    }
}
