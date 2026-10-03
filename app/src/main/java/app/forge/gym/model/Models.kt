package app.forge.gym.model

import java.time.*
import java.util.UUID
import kotlin.math.max

fun newId(): String = UUID.randomUUID().toString()
val weekDays = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
fun sundayIndex(date: LocalDate): Int = date.dayOfWeek.value % 7
fun epochDay(date: LocalDate): Long = date.toEpochDay()
fun today(): Long = LocalDate.now().toEpochDay()
enum class WorkoutType(val label: String) { PUSH("Push"), PULL("Pull"), LEGS("Legs"), UPPER("Upper"), LOWER("Lower"), REST("Rest"), CUSTOM("Custom") }

data class Exercise(val id: String, val name: String, val group: String)
data class PlanExercise(val exerciseId: String, val sets: Int = 3, val reps: Int = 10)
data class DayPlan(val day: Int, val type: WorkoutType, val exercises: List<PlanExercise>, val restSeconds: Int = 90, val note: String = "")
data class SetEntry(val id: String = newId(), val weight: String = "", val reps: String = "10", val done: Boolean = false, val warmup: Boolean = false, val note: String = "") {
    val weightValue: Double? get() = weight.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..2000.0 }
    val repsValue: Int? get() = reps.toIntOrNull()?.takeIf { it in 1..1000 }
    val isValid: Boolean get() = weightValue != null && repsValue != null
    val working: Boolean get() = done && isValid && !warmup
    val volume: Double get() = if (working) weightValue!! * repsValue!! else 0.0
    val estimatedMax: Double? get() = if (!working || repsValue!! !in 1..12) null else
        if (repsValue == 1) weightValue else weightValue!! * (1 + repsValue!! / 30.0)
}
data class WorkoutExercise(
    val exerciseId: String, val name: String, val group: String, val sets: List<SetEntry>,
    val note: String = "", val trainingMax: String = ""
) {
    val trainingMaxValue: Double? get() = trainingMax.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..2000.0 }
}
data class Workout(
    val id: String = newId(), val name: String, val type: WorkoutType, val date: Long?, val startedAt: Long,
    val finishedAt: Long? = null, val exercises: List<WorkoutExercise>, val note: String = "",
    val needsReview: Boolean = false, val originalDate: String = "", val source: String = "", val importOrder: Int? = null
) {
    val completedSets: Int get() = exercises.sumOf { e -> e.sets.count { it.done && it.isValid } }
    val workingSets: Int get() = exercises.sumOf { e -> e.sets.count { it.working } }
    val totalSets: Int get() = exercises.sumOf { it.sets.size }
    val volume: Double get() = exercises.sumOf { e -> e.sets.sumOf { it.volume } }
    val complete: Boolean get() = finishedAt != null
    val included: Boolean get() = complete && !needsReview && date != null
    val durationMinutes: Int get() = (((finishedAt ?: startedAt) - startedAt).coerceAtLeast(0) / 60000).toInt()
}
data class AppState(
    val exercises: List<Exercise> = exerciseLibrary,
    val program: List<DayPlan> = defaultProgram,
    val sessions: List<Workout> = emptyList(),
    val restSeconds: Int = 90,
    val restEndsAt: Long? = null,
    val originalNotes: String = ""
)
val exerciseLibrary = listOf(
    Exercise("bench", "Bench press", "Chest"), Exercise("incline", "Incline bench press", "Chest"),
    Exercise("idp", "Incline dumbbell press", "Chest"), Exercise("fly", "Chest fly", "Chest"),
    Exercise("row", "Cable row", "Back"), Exercise("barbell-row", "Barbell row", "Back"),
    Exercise("pulldown", "Lat pulldown", "Back"), Exercise("pullup", "Pull-up", "Back"),
    Exercise("squat", "Squat", "Legs"), Exercise("legpress", "Leg press", "Legs"),
    Exercise("legcurl", "Leg curl", "Legs"), Exercise("deadlift", "Deadlift", "Legs"),
    Exercise("rdl", "Romanian deadlift", "Legs"), Exercise("lunge", "Lunge", "Legs"),
    Exercise("calf", "Calf raise", "Legs"), Exercise("press", "Overhead press", "Shoulders"),
    Exercise("lateral", "Lateral raise", "Shoulders"), Exercise("facepull", "Face pull", "Shoulders"),
    Exercise("curl", "Barbell curl", "Arms"), Exercise("hammer", "Hammer curl", "Arms"),
    Exercise("triceps", "Triceps pushdown", "Arms"), Exercise("dip", "Dip", "Arms"),
    Exercise("crunch", "Crunch", "Core"), Exercise("plank", "Plank", "Core")
)
fun defaultExercises(type: WorkoutType): List<PlanExercise> = when (type) {
    WorkoutType.PUSH -> listOf(PlanExercise("bench", 4, 8), PlanExercise("incline", 3, 10), PlanExercise("press", 3, 8), PlanExercise("lateral", 3, 12), PlanExercise("triceps", 3, 12))
    WorkoutType.PULL -> listOf(PlanExercise("deadlift", 3, 5), PlanExercise("pulldown", 3, 10), PlanExercise("row", 3, 10), PlanExercise("facepull", 3, 15), PlanExercise("curl", 3, 12))
    WorkoutType.LEGS -> listOf(PlanExercise("squat", 4, 8), PlanExercise("legpress", 3, 10), PlanExercise("legcurl", 3, 12), PlanExercise("calf", 4, 15))
    WorkoutType.UPPER -> listOf(PlanExercise("bench", 3, 8), PlanExercise("row", 3, 10), PlanExercise("press", 3, 8), PlanExercise("pulldown", 3, 10), PlanExercise("curl", 3, 12))
    WorkoutType.LOWER -> listOf(PlanExercise("squat", 3, 5), PlanExercise("rdl", 3, 10), PlanExercise("lunge", 3, 12), PlanExercise("legcurl", 3, 12), PlanExercise("calf", 3, 15))
    else -> emptyList()
}
val defaultProgram = listOf(WorkoutType.PUSH, WorkoutType.PULL, WorkoutType.LEGS, WorkoutType.REST, WorkoutType.UPPER, WorkoutType.LOWER, WorkoutType.REST)
    .mapIndexed { i, type -> DayPlan(i, type, defaultExercises(type)) }

enum class Metric(val label: String) { TOP_WEIGHT("Top weight"), ESTIMATED_MAX("Estimated 1RM"), TRAINING_MAX("Training max") }
data class ProgressPoint(val sessionId: String, val label: String, val value: Double)
object Training {
    fun start(state: AppState, type: WorkoutType, now: Long, date: Long = today()): AppState {
        val plan = state.program.first { it.day == sundayIndex(LocalDate.ofEpochDay(date)) }
        val targets = if (plan.type == type) plan.exercises else defaultExercises(type)
        val exerciseLogs = targets.map { target ->
            val exercise = state.exercises.first { it.id == target.exerciseId }
            val previous = previousSet(state, exercise.id)
            WorkoutExercise(exercise.id, exercise.name, exercise.group, List(target.sets) { SetEntry(weight = previous?.weight ?: "0", reps = target.reps.toString()) })
        }
        return state.copy(sessions = state.sessions + Workout(name = "${type.label} session", type = type, date = date, startedAt = now, exercises = exerciseLogs), restSeconds = plan.restSeconds, restEndsAt = null)
    }
    fun previousSet(state: AppState, exerciseId: String): SetEntry? = state.sessions.filter { it.included }.sortedWith(compareByDescending<Workout> { it.date }.thenByDescending { it.startedAt })
        .asSequence().flatMap { it.exercises.asSequence() }.filter { it.exerciseId == exerciseId }
        .flatMap { it.sets.asSequence().filter { s -> s.working } }.firstOrNull()
    fun update(state: AppState, id: String, transform: (Workout) -> Workout): AppState {
        require(state.sessions.any { it.id == id }) { "Session no longer exists." }
        return state.copy(sessions = state.sessions.map { if (it.id == id) transform(it) else it })
    }
    fun editSet(state: AppState, id: String, exerciseId: String, set: SetEntry): AppState = update(state, id) { w ->
        w.copy(exercises = w.exercises.map { e -> if (e.exerciseId != exerciseId) e else e.copy(sets = e.sets.map { if (it.id == set.id) set else it }) })
    }
    fun toggleSet(state: AppState, id: String, exerciseId: String, set: SetEntry, now: Long): AppState {
        require(set.isValid) { "Enter a weight from 0–2000 kg and 1–1000 reps." }
        return editSet(state, id, exerciseId, set.copy(done = !set.done))
            .copy(restEndsAt = if (!set.done) now + state.restSeconds * 1000L else null)
    }
    fun finish(state: AppState, id: String, now: Long): AppState = update(state, id) { w ->
        require(w.completedSets > 0) { "Complete at least one valid set before finishing." }
        require(w.date != null) { "Choose a session date first." }
        w.copy(finishedAt = max(now, w.startedAt))
    }.copy(restEndsAt = null)
    fun confirm(state: AppState, id: String): AppState = update(state, id) { w ->
        require(w.date != null) { "Choose a complete date, including the year." }
        require(w.completedSets > 0) { "Review and complete at least one valid set." }
        w.copy(needsReview = false, finishedAt = w.finishedAt ?: w.startedAt)
    }
    fun progress(state: AppState, exerciseId: String, metric: Metric, imported: Boolean): List<ProgressPoint> {
        val sessions = if (imported) state.sessions.filter { it.importOrder != null }.sortedBy { it.importOrder } +
            state.sessions.filter { it.importOrder == null && it.included }.sortedBy { it.date }
        else state.sessions.filter { it.included }.sortedWith(compareBy<Workout> { it.date }.thenBy { it.startedAt })
        return sessions.mapNotNull { w ->
            val e = w.exercises.firstOrNull { it.exerciseId == exerciseId } ?: return@mapNotNull null
            val value = when (metric) {
                Metric.TOP_WEIGHT -> e.sets.filter { it.working }.maxOfOrNull { it.weightValue!! }
                Metric.ESTIMATED_MAX -> e.sets.mapNotNull { it.estimatedMax }.maxOrNull()
                Metric.TRAINING_MAX -> e.trainingMaxValue
            } ?: return@mapNotNull null
            ProgressPoint(w.id, if (imported && w.importOrder != null) "Note ${w.importOrder + 1}" else w.date?.let { LocalDate.ofEpochDay(it).toString() } ?: w.originalDate, value)
        }
    }
}
