package app.forge.gym

import app.forge.gym.data.*
import app.forge.gym.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.io.File

class CoreTests {
    private fun workout(set: SetEntry = SetEntry(weight = "100", reps = "5", done = true), review: Boolean = false) = Workout(id = "session", name = "Push", type = WorkoutType.PUSH, date = today(), startedAt = 1000, finishedAt = 2000,
        exercises = listOf(WorkoutExercise("bench", "Bench press", "Chest", listOf(set))), needsReview = review)
    @Test fun sundayFirstCalendar() { assertEquals(0, sundayIndex(LocalDate.of(2026, 10, 4))); assertEquals(6, sundayIndex(LocalDate.of(2026, 10, 3))) }
    @Test fun programHasAllSevenDays() { assertEquals((0..6).toList(), defaultProgram.map { it.day }); assertEquals(WorkoutType.PUSH, defaultProgram[0].type); assertEquals(WorkoutType.REST, defaultProgram[6].type) }
    @Test fun volumeExcludesWarmupsAndIncomplete() {
        val e = WorkoutExercise("bench", "Bench", "Chest", listOf(SetEntry(weight = "100", reps = "5", done = true), SetEntry(weight = "50", reps = "10", done = true, warmup = true), SetEntry(weight = "200", reps = "5")))
        assertEquals(500.0, workout().copy(exercises = listOf(e)).volume, .001)
        assertEquals(1, workout().copy(exercises = listOf(e)).workingSets)
    }
    @Test fun decimalComma() { assertEquals(62.5, SetEntry(weight = "62,5", reps = "8").weightValue!!, .001) }
    @Test fun bodyweightHasZeroExternalVolume() { assertTrue(SetEntry(weight = "0", reps = "10", done = true).isValid); assertEquals(0.0, SetEntry(weight = "0", reps = "10", done = true).volume, .001) }
    @Test fun negativeAndNonfiniteWeightsRejected() { listOf("-1", "NaN", "Infinity", "2001", "").forEach { assertFalse(SetEntry(weight = it, reps = "10").isValid) } }
    @Test fun zeroAndFractionalRepsRejected() { listOf("0", "-1", "1.5", "1001", "").forEach { assertFalse(SetEntry(weight = "100", reps = it).isValid) } }
    @Test fun epleySingleUsesActualWeight() { assertEquals(100.0, SetEntry(weight = "100", reps = "1", done = true).estimatedMax!!, .001) }
    @Test fun epleyLimitedToTwelveReps() { assertEquals(120.0, SetEntry(weight = "100", reps = "6", done = true).estimatedMax!!, .001); assertNull(SetEntry(weight = "100", reps = "13", done = true).estimatedMax) }
    @Test fun unreviewedEntriesExcludedFromDatedCharts() { val s = AppState(sessions = listOf(workout(review = true))); assertTrue(Training.progress(s, "bench", Metric.TOP_WEIGHT, false).isEmpty()); assertFalse(s.sessions.first().included) }
    @Test fun importedChartUsesNoteOrderAndSeparatesTrainingMax() {
        val a = workout().copy(id = "a", needsReview = true, importOrder = 1, exercises = listOf(workout().exercises.first().copy(trainingMax = "140")))
        val b = a.copy(id = "b", importOrder = 0)
        val p = Training.progress(AppState(sessions = listOf(a, b)), "bench", Metric.TRAINING_MAX, true)
        assertEquals(listOf("b", "a"), p.map { it.sessionId }); assertEquals(140.0, p.first().value, .001)
        assertEquals(100.0, Training.progress(AppState(sessions = listOf(a)), "bench", Metric.TOP_WEIGHT, true).first().value, .001)
    }
    @Test fun previousWeightOnlyFromConfirmedSessions() { val s = AppState(sessions = listOf(workout(review = true))); assertNull(Training.previousSet(s, "bench")) }
    @Test fun startingCopiesTargetsAndPreviousWeights() { val s = Training.start(AppState(sessions = listOf(workout())), WorkoutType.PUSH, 4000, LocalDate.of(2026, 10, 4).toEpochDay()); assertEquals("100", s.sessions.last().exercises.first().sets.first().weight); assertEquals(4, s.sessions.last().exercises.first().sets.size); assertFalse(s.sessions.last().complete) }
    @Test fun restDayCustomSessionStartsEmpty() { val s = Training.start(AppState(), WorkoutType.CUSTOM, 4000, LocalDate.of(2026, 10, 3).toEpochDay()); assertTrue(s.sessions.last().exercises.isEmpty()) }
    @Test fun changingTypePreservesExercises() { val s = AppState(sessions = listOf(workout())); val updated = Training.update(s, "session") { it.copy(type = WorkoutType.LEGS) }; assertEquals(s.sessions.first().exercises, updated.sessions.first().exercises) }
    @Test fun startingOffScheduleUsesSavedTemplateAndRest() {
        val custom = Exercise("custom-press", "My press", "Shoulders")
        val state = AppState(exercises = exerciseLibrary + custom, program = defaultProgram.map {
            if (it.day == 0) it.copy(exercises = listOf(PlanExercise(custom.id, 2, 6)), restSeconds = 180) else it
        })
        val started = Training.start(state, WorkoutType.PUSH, 4000, LocalDate.of(2026, 10, 3).toEpochDay())
        val exercise = started.sessions.last().exercises.single()
        assertEquals(custom.id, exercise.exerciseId)
        assertEquals(custom.name, exercise.name)
        assertEquals(2, exercise.sets.size)
        assertTrue(exercise.sets.all { it.reps == "6" && it.weight == "0" && !it.done })
        assertEquals(180, started.restSeconds)
    }
    @Test fun sessionDateChoosesMatchingTemplateWhenTypeRepeats() {
        val state = AppState(program = defaultProgram.map {
            if (it.day == 2) it.copy(type = WorkoutType.PUSH, exercises = listOf(PlanExercise("dip", 2, 7))) else it
        })
        val started = Training.start(state, WorkoutType.PUSH, 4000, LocalDate.of(2026, 10, 6).toEpochDay())
        assertEquals("dip", started.sessions.last().exercises.single().exerciseId)
    }
    @Test fun absentProgramTypeUsesBuiltInTargets() {
        val state = AppState(program = defaultProgram.map { it.copy(type = WorkoutType.REST, exercises = emptyList()) })
        val started = Training.start(state, WorkoutType.PULL, 4000)
        assertEquals(defaultExercises(WorkoutType.PULL).map { it.exerciseId }, started.sessions.last().exercises.map { it.exerciseId })
    }
    @Test fun selectingTemplatePopulatesEmptyDraftWithPreviousWeightsAndFreshSets() {
        val draft = workout().copy(id = "draft", type = WorkoutType.CUSTOM, date = null, finishedAt = null, exercises = emptyList())
        val state = AppState(sessions = listOf(workout(), draft))
        val result = Training.selectType(state, draft.id, WorkoutType.PUSH).sessions.last()
        assertEquals(WorkoutType.PUSH, result.type)
        assertEquals(defaultExercises(WorkoutType.PUSH).map { it.exerciseId }, result.exercises.map { it.exerciseId })
        val sets = result.exercises.first().sets
        assertEquals(4, sets.size)
        assertTrue(sets.all { it.weight == "100" && it.reps == "8" && !it.done })
        val ids = result.exercises.flatMap { it.sets }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertFalse(ids.contains(state.sessions.first().exercises.first().sets.first().id))
    }
    @Test fun selectingTemplateKeepsLoggedSetsAndDoesNotDuplicateExercises() {
        val draft = workout().copy(finishedAt = null, note = "Keep this", exercises = listOf(workout().exercises.first().copy(note = "Bench notes")))
        val state = AppState(sessions = listOf(draft), program = defaultProgram.map {
            if (it.type == WorkoutType.PUSH) it.copy(exercises = listOf(PlanExercise("bench", 8, 12), PlanExercise("dip", 2, 7))) else it
        })
        val once = Training.selectType(state, draft.id, WorkoutType.PUSH)
        val twice = Training.selectType(once, draft.id, WorkoutType.PUSH)
        val result = twice.sessions.single()
        assertEquals(once, twice)
        assertEquals(draft.exercises.single(), result.exercises.first())
        assertEquals("Keep this", result.note)
        assertEquals(listOf("bench", "dip"), result.exercises.map { it.exerciseId })
        assertTrue(result.exercises.last().sets.all { it.reps == "7" && !it.done })
    }
    @Test fun selectingTypeInFinishedSessionKeepsRecordedExercises() {
        val finished = workout()
        val result = Training.selectType(AppState(sessions = listOf(finished)), finished.id, WorkoutType.LEGS).sessions.single()
        assertEquals(finished.copy(type = WorkoutType.LEGS), result)
    }
    @Test fun emptySavedTemplateDoesNotFallBackToDefaults() {
        val state = AppState(program = defaultProgram.map { if (it.type == WorkoutType.PUSH) it.copy(exercises = emptyList()) else it })
        assertTrue(Training.start(state, WorkoutType.PUSH, 4000).sessions.single().exercises.isEmpty())
    }
    @Test fun selectingRestKeepsExistingSetsWithoutAddingExercises() {
        val draft = workout().copy(finishedAt = null)
        val state = AppState(sessions = listOf(draft), program = defaultProgram.map { if (it.type == WorkoutType.REST) it.copy(exercises = listOf(PlanExercise("squat"))) else it })
        assertEquals(draft.copy(type = WorkoutType.REST), Training.selectType(state, draft.id, WorkoutType.REST).sessions.single())
    }
    @Test(expected = IllegalArgumentException::class) fun finishRequiresCompletedSet() { Training.finish(AppState(sessions = listOf(workout(SetEntry()))), "session", 5000) }
    @Test(expected = IllegalArgumentException::class) fun confirmRequiresFullDate() { Training.confirm(AppState(sessions = listOf(workout(review = true).copy(date = null))), "session") }
    @Test fun confirmEnablesCalendarTotals() { val s = Training.confirm(AppState(sessions = listOf(workout(review = true))), "session"); assertTrue(s.sessions.first().included) }
    @Test fun timerUsesAbsoluteDeadline() { val w = workout(SetEntry(weight = "80", reps = "8" )).copy(finishedAt = null); val s = Training.toggleSet(AppState(sessions = listOf(w)), w.id, "bench", w.exercises.first().sets.first(), 10000); assertEquals(100000L, s.restEndsAt); assertTrue(s.sessions.first().exercises.first().sets.first().done) }
    @Test fun backupRoundTripPreservesNotesDraftsWarmupsAndMetadata() { val s = AppState(sessions = listOf(workout().copy(note = "Felt good", needsReview = true, date = null, source = "26/12\nBP", importOrder = 0)), originalNotes = "verbatim source\n"); assertEquals(s, StateCodec.decode(StateCodec.encode(s))) }
    @Test(expected = IllegalArgumentException::class) fun unknownBackupVersionRejected() { val j = JSONObject(StateCodec.encode(AppState())).put("version", 2); StateCodec.decode(j.toString()) }
    @Test(expected = IllegalArgumentException::class) fun backupMissingProgramDayRejected() { val j = JSONObject(StateCodec.encode(AppState())); j.getJSONArray("program").remove(0); StateCodec.decode(j.toString()) }
    @Test(expected = IllegalArgumentException::class) fun backupDuplicateSessionRejected() { val s = AppState(sessions = listOf(workout(), workout())); StateCodec.decode(StateCodec.encode(s)) }
    @Test(expected = IllegalArgumentException::class) fun corruptCompletedSetRejected() { val s = AppState(sessions = listOf(workout(SetEntry(weight = "-8", reps = "5", done = true)))); StateCodec.decode(StateCodec.encode(s)) }
    @Test fun explicitNotationParsedConservatively() {
        assertEquals(3, HistoryImporter.parseSets("3x5x120").size)
        assertEquals(44.0, HistoryImporter.parseSets("8x44").first().weightValue!!, .001)
        assertEquals(62.5, HistoryImporter.parseSets("8x62,5").first().weightValue!!, .001)
        listOf("3x5 120", "1.5x100", "12xmax", "120 125 130", "5/3/1 140").forEach { assertTrue(it, HistoryImporter.parseSets(it).isEmpty()) }
    }
    @Test fun schemeMetadataDoesNotBecomeLiftedSets() { val s = HistoryImporter.import(AppState(), "26/12\nBP 5/3/1 137.5\n"); val e = s.sessions.first().exercises.first(); assertEquals("137.5", e.trainingMax); assertTrue(e.sets.isEmpty()); assertNull(s.sessions.first().date); assertTrue(s.sessions.first().needsReview) }
    @Test fun completeWrittenYearIsPreserved() { val s = HistoryImporter.import(AppState(), "13.07.24\nBP\n8x44\n"); assertEquals(LocalDate.of(2024, 7, 13).toEpochDay(), s.sessions.first().date) }
    @Test fun sampleHistoryPreservedAndReviewable() {
        val file = File(System.getProperty("forge.root"), "app/src/main/assets/TrainingHistory.md")
        val text = file.readText()
        val s = HistoryImporter.import(AppState(), text)
        assertEquals(text, s.originalNotes)
        assertEquals(2, s.sessions.size)
        assertTrue(s.sessions.all { it.needsReview })
        assertEquals(1, s.sessions.count { it.date != null })
        assertEquals(1, s.sessions.sumOf { it.exercises.sumOf { e -> e.sets.size } })
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
    }
    @Test fun publicStarterSeedContainsNoPersonalHistory() {
        val file = File(System.getProperty("forge.root"), "app/src/main/assets/SeedHistory.json")
        val seed = StateCodec.decode(file.readText())
        assertTrue(seed.sessions.isEmpty())
        assertEquals("", seed.originalNotes)
        assertEquals(exerciseLibrary, seed.exercises)
    }

}
