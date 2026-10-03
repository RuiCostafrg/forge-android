package app.forge.gym.data

import app.forge.gym.model.*
import java.time.LocalDate

/** Source notes remain intact. Missing dates and ambiguous notation always require review. */
object HistoryImporter {
    private val date = Regex("^\\s*(?:#+\\s*)?(\\d{1,2})[./-](\\d{1,2})(?:[./-](\\d{4}|\\d{2}))?\\s*$")
    private val separator = Regex("^[—–_=\\-\\s]{2,}$")
    private val scheme = Regex("(?:5/3/1|3/3/3|5/5/5)")
    private val explicit = Regex("(?<![\\d.,xX])(?:(\\d+)\\s*[xX]\\s*)?(\\d+)\\s*[xX]\\s*(\\d+(?:[.,]\\d+)?)(?![\\d.,xX])")
    private val aliases = mapOf("bp" to "bench", "bench press" to "bench", "dl" to "deadlift", "deadlift" to "deadlift", "ohp" to "press", "overhead press" to "press", "ibp" to "incline", "idp" to "idp", "squat" to "squat")
    private fun clean(line: String) = line.trim().removePrefix("#").trim().replace(Regex("^[AB]\\s*[-–]\\s*"), "")
    private fun primary(line: String): String? {
        val first = clean(line).substringBefore(' ').lowercase()
        return aliases[first]?.takeIf { it in listOf("bench", "deadlift", "squat") }
    }
    fun import(state: AppState, text: String): AppState {
        require(text.isNotBlank()) { "The notes file is empty." }
        require(text.length <= 5_000_000) { "Notes file is too large." }
        require(state.originalNotes.isEmpty()) { "Source notes have already been imported." }
        val blocks = mutableListOf<String>()
        var lines = mutableListOf<String>()
        val seenPrimary = mutableSetOf<String>()
        fun flush() {
            if (lines.any { it.isNotBlank() } && lines.any { primary(it) != null || explicit.containsMatchIn(it) || scheme.containsMatchIn(it) }) blocks += lines.joinToString("\n")
            lines = mutableListOf(); seenPrimary.clear()
        }
        text.lines().forEach { line ->
            val primary = primary(line)
            if (date.matches(line) || (line.isNotBlank() && separator.matches(line)) || (primary != null && primary in seenPrimary)) flush()
            lines += line
            if (primary != null) seenPrimary += primary
        }; flush()
        val library = state.exercises.toMutableList()
        val baseTime = System.currentTimeMillis()
        val sessions = blocks.mapIndexed { index, source ->
            val marker = source.lines().firstNotNullOfOrNull { date.matchEntire(it) }
            val resolved = marker?.takeIf { it.groupValues[3].isNotEmpty() }?.let {
                val year = it.groupValues[3].toInt().let { y -> if (y < 100) 2000 + y else y }
                runCatching { LocalDate.of(year, it.groupValues[2].toInt(), it.groupValues[1].toInt()).toEpochDay() }.getOrNull()
            }
            val logs = mutableListOf<WorkoutExercise>()
            var current: Int? = null
            source.lines().forEach lineLoop@ { line ->
                val trimmed = clean(line)
                if (trimmed.isBlank() || date.matches(line) || separator.matches(line)) return@lineLoop
                val firstNumber = Regex("\\d").find(trimmed)?.range?.first ?: trimmed.length
                val namePart = trimmed.take(firstNumber).trim().trimEnd(':', '-').trim()
                val firstWord = namePart.substringBefore(' ').lowercase()
                val alias = aliases[firstWord] ?: aliases[namePart.lowercase()]
                val isName = namePart.length in 2..80 && namePart.any { it.isLetter() } && !trimmed.startsWith("#") &&
                    (alias != null || !trimmed.first().isDigit()) && !Regex("^(?:note|comment|felt|failed|good|bad|tired)\\b", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
                if (isName) {
                    val exercise = library.firstOrNull { it.id == alias || it.name.equals(namePart, true) }
                        ?: Exercise(newId(), namePart, "Imported").also { library += it }
                    val existing = logs.indexOfFirst { it.exerciseId == exercise.id }
                    current = if (existing >= 0) existing else logs.size.also {
                        logs += WorkoutExercise(exercise.id, exercise.name, exercise.group, emptyList())
                    }
                }
                val target = current ?: return@lineLoop
                val sets = parseSets(trimmed)
                val schemeMatch = scheme.find(trimmed)
                val trainingMax = if (schemeMatch != null) {
                    val numbers = Regex("\\d+(?:[.,]\\d+)?").findAll(trimmed.replaceRange(schemeMatch.range, " ")).map { it.value }.toList()
                    numbers.lastOrNull { it.replace(',', '.').toDoubleOrNull()?.let { w -> w in 0.0..2000.0 } == true } ?: ""
                } else ""
                val e = logs[target]
                logs[target] = e.copy(sets = (e.sets + sets).take(100), note = (e.note + if (e.note.isBlank()) line else "\n$line").take(1000), trainingMax = trainingMax.ifBlank { e.trainingMax })
            }
            val type = source.lines().firstNotNullOfOrNull { l -> WorkoutType.entries.firstOrNull { it != WorkoutType.CUSTOM && it.label.equals(clean(l), true) } } ?: WorkoutType.CUSTOM
            Workout(name = "${type.label} · entry ${index + 1}", type = type, date = resolved, startedAt = baseTime + index,
                exercises = logs.take(100), needsReview = true, originalDate = marker?.value?.trim() ?: "Undated note", source = source, importOrder = index)
        }
        return state.copy(exercises = library.distinctBy { it.id }, sessions = state.sessions + sessions, originalNotes = text)
    }
    fun parseSets(line: String): List<SetEntry> {
        val bodyweight = Regex("(?<![\\d.,])(\\d+)\\s*[xX]\\s*(?:bw|bodyweight)\\b", RegexOption.IGNORE_CASE)
        val bw = bodyweight.findAll(line).mapNotNull { m -> m.groupValues[1].toIntOrNull()?.takeIf { it in 1..1000 }?.let {
            SetEntry(weight = "0", reps = it.toString(), done = true, note = "Bodyweight; zero external load.")
        } }.toList()
        return explicit.findAll(line).flatMap { m ->
            val after = line.substring(m.range.last + 1)
            // "3x5 120" is ambiguous: never reinterpret 5 as a lifted weight.
            val ambiguous = m.groupValues[1].isEmpty() && Regex("^\\s+\\d+(?:[.,]\\d+)?(?=\\s|$)").containsMatchIn(after)
            val count = m.groupValues[1].toIntOrNull() ?: 1
            val reps = m.groupValues[2].toIntOrNull() ?: 0
            val weight = m.groupValues[3].replace(',', '.').toDoubleOrNull() ?: -1.0
            if (ambiguous || count !in 1..30 || reps !in 1..1000 || weight !in 0.0..2000.0) emptySequence()
            else List(count) { SetEntry(weight = m.groupValues[3], reps = reps.toString(), done = true) }.asSequence()
        }.toList() + bw
    }
}
