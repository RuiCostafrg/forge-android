package app.forge.gym.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.forge.gym.GymViewModel
import app.forge.gym.model.*
import java.time.LocalDate

@Composable fun SessionEditor(w: Workout, state: AppState, vm: GymViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var add by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }
    var confirmReview by remember { mutableStateOf(false) }
    var name by rememberSaveable(w.id) { mutableStateOf(w.name) }
    var note by rememberSaveable(w.id) { mutableStateOf(w.note) }
    var showSource by rememberSaveable(w.id) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text("SESSION", color = Green, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { delete = true }) { Icon(Icons.Rounded.DeleteOutline, "Delete session", tint = Muted) }
            }
        }
        item {
            ForgeCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tag(if (w.needsReview) "NEEDS REVIEW" else if (w.complete) "FINISHED" else "DRAFT", !w.needsReview); Tag("ON DEVICE", true) }
                OutlinedTextField(name, { value -> if (value.length <= 80) { name = value; if (value.isNotBlank()) vm.update(w.id) { it.copy(name = value) } } }, label = { Text("Session title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TypePicker(w.type, { type -> vm.selectType(w.id, type) })
                Text(if (w.complete) "Reopen to add exercises from your program." else "Selecting a type adds its program exercises and keeps your existing sets.", color = Muted, fontSize = 10.sp)
                OutlinedButton(onClick = {
                    val initial = w.date?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
                    DatePickerDialog(context, { _, year, month, day -> vm.update(w.id) { it.copy(date = LocalDate.of(year, month + 1, day).toEpochDay()) } }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(dateLabel(w.date)) }
                if (w.needsReview) Text("Original date: ${w.originalDate}. Choose the correct year; it is never inferred from a partial date.", color = Muted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth()) { Stat(w.completedSets.toString(), "DONE / ${w.totalSets}", Modifier.weight(1f)); Stat(number(w.volume), "WORKING VOLUME / KG", Modifier.weight(1f)) }
                if (w.complete) OutlinedButton(onClick = { vm.reopen(w.id) }, modifier = Modifier.fillMaxWidth()) { Text("Reopen to edit sets") }
            }
        }
        if (w.needsReview) item {
            ForgeCard {
                Text("Review against the original notes", color = Green, fontWeight = FontWeight.SemiBold)
                Text("Only explicit sets were parsed. Training maxima, ambiguous loads, comments, and unresolved dates are kept separately. Confirm only after checking the date and recorded sets.", color = Muted, fontSize = 12.sp)
                TextButton(onClick = { showSource = !showSource }) { Text(if (showSource) "Hide original entry" else "Show original entry") }
                if (showSource) androidx.compose.foundation.text.selection.SelectionContainer { Text(w.source, fontSize = 12.sp, color = Muted) }
            }
        }
        itemsIndexed(w.exercises, key = { _, e -> e.exerciseId }) { index, exercise ->
            WorkoutExerciseCard(w, exercise, index, state, vm)
        }
        if (!w.complete) item { OutlinedButton(onClick = { add = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Icon(Icons.Rounded.Add, null); Text("Add exercise") } }
        item {
            ForgeCard {
                Text("Session notes", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(note, { value -> if (value.length <= 1000) { note = value; vm.update(w.id) { it.copy(note = value) } } }, placeholder = { Text("How did it feel? Anything to remember?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("Rest timer", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 90, 120, 180).forEach { seconds -> FilterChip(state.restSeconds == seconds, onClick = { vm.change { it.copy(restSeconds = seconds) } }, label = { Text("${seconds}s") }) }
                }
                OutlinedButton(onClick = { vm.change { it.copy(restEndsAt = System.currentTimeMillis() + it.restSeconds * 1000L) } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Timer, null); Text("Start rest timer") }
            }
        }
        if (w.needsReview) item { Button(onClick = { confirmReview = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Confirm entry for calendar & totals") } }
        else if (!w.complete) item { Button(onClick = { finish = true }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = w.completedSets > 0) { Icon(Icons.Rounded.Check, null); Text("Finish session") } }
        item { Text("Working volume excludes warm-ups and incomplete sets. Zero kg means no external load.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 16.dp)) }
    }
    if (add) ExercisePicker(state.exercises, w.exercises.map { it.exerciseId }.toSet(), { add = false }, { vm.addExercise(w.id, it) })
    if (delete) ConfirmDialog("Delete session?", "This removes this session and its recorded sets from this device.", "Delete", { delete = false }) { vm.delete(w.id); onClose() }
    if (finish) ConfirmDialog("Finish session?", "${w.completedSets} completed sets will be saved. Incomplete sets stay in the log but do not count toward totals.", "Finish", { finish = false }) { vm.finish(w.id) }
    if (confirmReview) ConfirmDialog("Confirm imported entry?", "Confirm that the full date, exercises, weights, and reps match the original notes. This entry will count in the calendar and totals.", "Confirm", { confirmReview = false }) { vm.confirm(w.id) }
}
@Composable private fun WorkoutExerciseCard(w: Workout, e: WorkoutExercise, index: Int, state: AppState, vm: GymViewModel) {
    var expanded by rememberSaveable(w.id, e.exerciseId) { mutableStateOf(index == 0) }
    var options by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    var name by rememberSaveable(w.id, e.exerciseId) { mutableStateOf(e.name) }
    var note by rememberSaveable(w.id, e.exerciseId) { mutableStateOf(e.note) }
    var trainingMax by rememberSaveable(w.id, e.exerciseId) { mutableStateOf(e.trainingMax) }
    fun modify(transform: (WorkoutExercise) -> WorkoutExercise) = vm.update(w.id) { workout -> workout.copy(exercises = workout.exercises.map { if (it.exerciseId == e.exerciseId) transform(it) else it }) }
    ForgeCard {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Text("%02d".format(index + 1), color = Green, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) { Text(e.name, fontWeight = FontWeight.SemiBold); Text("${e.group} · ${e.sets.count { it.done }} / ${e.sets.size} sets", color = Muted, fontSize = 11.sp) }
            IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, "Expand exercise") }
        }
        if (expanded) {
            val previous = Training.previousSet(state.copy(sessions = state.sessions.filter { it.id != w.id }), e.exerciseId)
            if (previous != null) Text("Previous: ${previous.weight} kg × ${previous.reps} reps", color = Green, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("SET", color = Muted, fontSize = 10.sp, modifier = Modifier.width(34.dp))
                Text("KG", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("REPS", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("DONE", color = Muted, fontSize = 10.sp, modifier = Modifier.width(48.dp))
            }
            e.sets.forEachIndexed { i, set -> key(set.id) { SetRow(w, e, set, i, vm) } }
            if (!w.complete) TextButton(onClick = {
                modify { current -> require(current.sets.size < 100) { "Maximum 100 sets per exercise." }; current.copy(sets = current.sets + SetEntry(weight = current.sets.lastOrNull()?.weight ?: "0", reps = current.sets.lastOrNull()?.reps ?: "10")) }
            }) { Icon(Icons.Rounded.Add, null); Text("Add set") }
            TextButton(onClick = { options = !options }) { Text(if (options) "Hide exercise details" else "Notes, training max & exercise options") }
            if (options) {
                OutlinedTextField(name, { value -> if (value.length <= 80) { name = value; if (value.isNotBlank()) modify { it.copy(name = value) } } }, label = { Text("Exercise name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(trainingMax, { value -> if (value.length <= 12 && value.all { it.isDigit() || it == '.' || it == ',' }) { trainingMax = value; modify { it.copy(trainingMax = value) } } }, label = { Text("Training max / kg (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Planning metadata, not a weight you lifted.", color = Muted, fontSize = 10.sp)
                OutlinedTextField(note, { value -> if (value.length <= 1000) { note = value; modify { it.copy(note = value) } } }, label = { Text("Exercise notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                if (!w.complete) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { vm.update(w.id) { current -> val list = current.exercises.toMutableList(); java.util.Collections.swap(list, index, index - 1); current.copy(exercises = list) } }, enabled = index > 0) { Icon(Icons.Rounded.ArrowUpward, "Move exercise up") }
                    IconButton(onClick = { vm.update(w.id) { current -> val list = current.exercises.toMutableList(); java.util.Collections.swap(list, index, index + 1); current.copy(exercises = list) } }, enabled = index < w.exercises.lastIndex) { Icon(Icons.Rounded.ArrowDownward, "Move exercise down") }
                    TextButton(onClick = { remove = true }) { Text("Remove") }
                }
            }
        }
    }
    if (remove) ConfirmDialog("Remove ${e.name}?", "Its sets will be removed from this session.", "Remove", { remove = false }) { vm.update(w.id) { it.copy(exercises = it.exercises.filter { exercise -> exercise.exerciseId != e.exerciseId }) } }
}
@Composable private fun SetRow(w: Workout, e: WorkoutExercise, set: SetEntry, index: Int, vm: GymViewModel) {
    var weight by rememberSaveable(set.id) { mutableStateOf(set.weight) }
    var reps by rememberSaveable(set.id) { mutableStateOf(set.reps) }
    var note by rememberSaveable(set.id) { mutableStateOf(set.note) }
    var details by rememberSaveable(set.id) { mutableStateOf(false) }
    val editable = !w.complete
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (set.warmup) "W" else (index + 1).toString(), color = if (set.done) Green else Muted, modifier = Modifier.width(28.dp))
            OutlinedTextField(weight, { value -> if (value.length <= 12 && value.all { it.isDigit() || it == '.' || it == ',' }) { weight = value; vm.editSet(w.id, e.exerciseId, set.copy(weight = value, reps = reps, note = note, done = false)) } }, modifier = Modifier.weight(1f), singleLine = true, enabled = editable, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(reps, { value -> if (value.length <= 6 && value.all { it.isDigit() }) { reps = value; vm.editSet(w.id, e.exerciseId, set.copy(weight = weight, reps = value, note = note, done = false)) } }, modifier = Modifier.weight(1f), singleLine = true, enabled = editable, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Checkbox(set.done, onCheckedChange = if (editable) { _ -> vm.toggleSet(w.id, e.exerciseId, set) } else null, modifier = Modifier.width(42.dp))
        }
        TextButton(onClick = { details = !details }, contentPadding = PaddingValues(vertical = 0.dp), modifier = Modifier.align(Alignment.End)) { Text(if (details) "Hide details" else if (set.note.isNotBlank()) "Set note & options" else "Set options", fontSize = 10.sp) }
        if (details) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(set.warmup, onCheckedChange = if (editable) { value -> vm.editSet(w.id, e.exerciseId, set.copy(weight = weight, reps = reps, note = note, warmup = value)) } else null)
                Text("Warm-up · excluded from totals", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                if (editable) IconButton(onClick = { vm.update(w.id) { workout -> workout.copy(exercises = workout.exercises.map { if (it.exerciseId == e.exerciseId) it.copy(sets = it.sets.filter { s -> s.id != set.id }) else it }) } }) { Icon(Icons.Rounded.DeleteOutline, "Remove set", tint = Muted) }
            }
            OutlinedTextField(note, { value -> if (value.length <= 1000) { note = value; vm.editSet(w.id, e.exerciseId, set.copy(weight = weight, reps = reps, note = value)) } }, label = { Text("Set note") }, modifier = Modifier.fillMaxWidth(), minLines = 1)
        }
    }
}
