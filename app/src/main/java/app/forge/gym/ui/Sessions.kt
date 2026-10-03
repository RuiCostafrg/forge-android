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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.forge.gym.GymViewModel
import app.forge.gym.model.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun SessionsScreen(state: AppState, vm: GymViewModel, onOpen: (String) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var create by remember { mutableStateOf(false) }
    val sessions = remember(state.sessions, selectedDay, search, filter) {
        state.sessions.filter { w ->
            (selectedDay == null || (!w.needsReview && w.date == selectedDay)) && when (filter) {
                "History" -> w.included
                "Review" -> w.needsReview
                "Drafts" -> !w.complete && !w.needsReview
                else -> true
            } && (search.isBlank() || listOf(w.name, w.note, w.source, w.type.label).any { it.contains(search, true) } || w.exercises.any { it.name.contains(search, true) || it.note.contains(search, true) || it.sets.any { s -> s.note.contains(search, true) } })
        }.sortedWith(compareBy<Workout> { if (it.needsReview) 1 else 0 }.thenByDescending { if (it.needsReview) -(it.importOrder ?: 0).toLong() else it.date ?: Long.MIN_VALUE }.thenByDescending { it.startedAt })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Your training journal", "Sessions", "Every set has a story.", action = { IconButton(onClick = { create = true }) { Icon(Icons.Rounded.Add, "New session", tint = Green) } }) }
        item {
            ForgeCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthText = month.minusMonths(1).toString(); selectedDay = null }) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { monthText = month.plusMonths(1).toString(); selectedDay = null }) { Icon(Icons.Rounded.ChevronRight, "Next month") }
                }
                Row(Modifier.fillMaxWidth()) { weekDays.forEach { Text(it.take(3), Modifier.weight(1f), color = Muted, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
                val offset = sundayIndex(month.atDay(1))
                val rows = (offset + month.lengthOfMonth() + 6) / 7
                repeat(rows) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { col ->
                            val day = row * 7 + col - offset + 1
                            val epoch = if (day in 1..month.lengthOfMonth()) month.atDay(day).toEpochDay() else null
                            val trained = epoch != null && state.sessions.any { it.included && it.date == epoch }
                            val selected = epoch != null && epoch == selectedDay
                            val isToday = epoch == today()
                            Box(Modifier.weight(1f).height(44.dp).then(if (epoch != null) Modifier.clickable { selectedDay = if (selected) null else epoch } else Modifier), contentAlignment = Alignment.Center) {
                                if (epoch != null) Surface(shape = RoundedCornerShape(9.dp), color = if (selected) Green else if (trained) Green.copy(alpha = .13f) else CardColor,
                                    border = if (isToday) BorderStroke(1.dp, Green) else null) {
                                    Column(Modifier.size(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(day.toString(), fontSize = 12.sp, color = if (selected) Navy else if (trained) Green else MaterialTheme.colorScheme.onSurface)
                                        if (trained) Box(Modifier.size(3.dp).background(if (selected) Navy else Green, RoundedCornerShape(2.dp)))
                                    }
                                }
                            }
                        }
                    }
                }
                Text("Sunday first · confirmed finished sessions only", color = Muted, fontSize = 10.sp)
                if (selectedDay != null) TextButton(onClick = { selectedDay = null }) { Text("Clear ${dateLabel(selectedDay)} filter") }
            }
        }
        item { OutlinedTextField(search, { search = it }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Search sessions, exercises, notes") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp)) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "History", "Review", "Drafts").forEach { label -> FilterChip(filter == label, { filter = label; if (label == "Review") selectedDay = null }, label = { Text(label) }) } } }
        item { Text("${sessions.size} ${if (filter == "Review") "reviewable entries" else "sessions"}", color = Muted, fontSize = 12.sp) }
        if (sessions.isEmpty()) item { EmptyState(Icons.Rounded.CalendarMonth, "Nothing here yet.", "Try another filter or start a new session.") }
        items(sessions, key = { it.id }) { SessionCard(it, onOpen) }
    }
    if (create) {
        var type by remember { mutableStateOf(WorkoutType.CUSTOM) }
        AlertDialog(onDismissRequest = { create = false }, title = { Text("New session") }, text = { TypePicker(type, { type = it }, false) },
            confirmButton = { TextButton(onClick = { create = false; vm.start(type, onOpen) }) { Text("Start") } }, dismissButton = { TextButton(onClick = { create = false }) { Text("Cancel") } })
    }
}
