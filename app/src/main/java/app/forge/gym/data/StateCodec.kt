package app.forge.gym.data

import app.forge.gym.model.*
import org.json.JSONArray
import org.json.JSONObject

object StateCodec {
    const val MAX_BYTES = 20 * 1024 * 1024
    fun encode(state: AppState): String = JSONObject().apply {
        put("format", "forge-android-backup"); put("version", 1)
        put("exercises", array(state.exercises) { JSONObject().put("id", it.id).put("name", it.name).put("group", it.group) })
        put("program", array(state.program) { p -> JSONObject().put("day", p.day).put("type", p.type.name).put("restSeconds", p.restSeconds).put("note", p.note)
            .put("exercises", array(p.exercises) { JSONObject().put("exerciseId", it.exerciseId).put("sets", it.sets).put("reps", it.reps) }) })
        put("sessions", array(state.sessions, ::workoutJson))
        put("restSeconds", state.restSeconds); put("restEndsAt", state.restEndsAt ?: JSONObject.NULL)
        put("originalNotes", state.originalNotes)
    }.toString()
    private fun workoutJson(w: Workout) = JSONObject().apply {
        put("id", w.id); put("name", w.name); put("type", w.type.name); put("date", w.date ?: JSONObject.NULL)
        put("startedAt", w.startedAt); put("finishedAt", w.finishedAt ?: JSONObject.NULL); put("note", w.note)
        put("needsReview", w.needsReview); put("originalDate", w.originalDate); put("source", w.source); put("importOrder", w.importOrder ?: JSONObject.NULL)
        put("exercises", array(w.exercises) { e -> JSONObject().put("exerciseId", e.exerciseId).put("name", e.name).put("group", e.group)
            .put("note", e.note).put("trainingMax", e.trainingMax)
            .put("sets", array(e.sets) { s -> JSONObject().put("id", s.id).put("weight", s.weight).put("reps", s.reps).put("done", s.done).put("warmup", s.warmup).put("note", s.note) }) })
    }
    fun decode(text: String): AppState {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup must be under 20 MB." }
        val root = JSONObject(text)
        require(root.getString("format") == "forge-android-backup" && root.getInt("version") == 1) { "Unsupported backup. Choose a Forge Android JSON export." }
        val exercises = readArray(root.getJSONArray("exercises"), 2000) { Exercise(string(it, "id", 100), string(it, "name", 80), string(it, "group", 40)) }
        unique(exercises.map { it.id })
        val ids = exercises.map { it.id }.toSet()
        val program = readArray(root.getJSONArray("program"), 7) { p ->
            DayPlan(p.getInt("day").also { require(it in 0..6) }, WorkoutType.valueOf(p.getString("type")),
                readArray(p.getJSONArray("exercises"), 100) {
                    val id = string(it, "exerciseId", 100)
                    require(id in ids) { "Program references a missing exercise." }
                    PlanExercise(id, it.getInt("sets").also { require(it in 1..30) }, it.getInt("reps").also { require(it in 1..1000) })
                }.also { unique(it.map { e -> e.exerciseId }) }, p.getInt("restSeconds").also { require(it in 15..600) }, string(p, "note", 1000, true))
        }
        require(program.map { it.day }.sorted() == (0..6).toList()) { "Backup must contain seven distinct program days." }
        val sessions = readArray(root.getJSONArray("sessions"), 10000, ::readWorkout)
        unique(sessions.map { it.id })
        val restSeconds = root.getInt("restSeconds").also { require(it in 15..600) }
        val restEndsAt = if (root.isNull("restEndsAt")) null else root.getLong("restEndsAt").also { require(it > 0) }
        return AppState(exercises, program.sortedBy { it.day }, sessions, restSeconds, restEndsAt, string(root, "originalNotes", 5_000_000, true))
    }
    private fun readWorkout(j: JSONObject): Workout {
        val started = j.getLong("startedAt").also { require(it > 0) }
        val ended = if (j.isNull("finishedAt")) null else j.getLong("finishedAt").also { require(it >= started) }
        val date = if (j.isNull("date")) null else j.getLong("date").also { require(it in -100000..100000) }
        val exercises = readArray(j.getJSONArray("exercises"), 100) { e ->
            WorkoutExercise(string(e, "exerciseId", 100), string(e, "name", 80), string(e, "group", 40),
                readArray(e.getJSONArray("sets"), 100) { s ->
                    SetEntry(string(s, "id", 100), string(s, "weight", 12, true), string(s, "reps", 6, true), s.getBoolean("done"), s.getBoolean("warmup"), string(s, "note", 1000, true)).also {
                        require(!it.done || it.isValid) { "A completed set has invalid weight or reps." }
                    }
                }.also { unique(it.map { s -> s.id }) }, string(e, "note", 1000, true), string(e, "trainingMax", 12, true))
        }
        unique(exercises.map { it.exerciseId })
        val review = j.getBoolean("needsReview")
        require(review || date != null) { "A confirmed session must have a date." }
        val w = Workout(string(j, "id", 100), string(j, "name", 80), WorkoutType.valueOf(j.getString("type")), date, started, ended, exercises,
            string(j, "note", 1000, true), review, string(j, "originalDate", 100, true), string(j, "source", 200_000, true),
            if (j.isNull("importOrder")) null else j.getInt("importOrder").also { require(it >= 0) })
        require(!w.complete || review || w.completedSets > 0) { "A finished session has no completed sets." }
        return w
    }
    private fun string(j: JSONObject, key: String, max: Int, empty: Boolean = false) = j.getString(key).also {
        require(it.length <= max && (empty || it.isNotBlank())) { "Invalid field: $key." }
    }
    private fun unique(ids: List<String>) { require(ids.size == ids.toSet().size) { "Duplicate identifiers in backup." } }
    private fun <T> array(items: List<T>, transform: (T) -> JSONObject): JSONArray = JSONArray().apply { items.forEach { put(transform(it)) } }
    private fun <T> readArray(a: JSONArray, max: Int, transform: (JSONObject) -> T): List<T> {
        require(a.length() <= max) { "Too many records in backup." }
        return List(a.length()) { transform(a.getJSONObject(it)) }
    }
}
