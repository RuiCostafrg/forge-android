package app.forge.gym

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.forge.gym.data.*
import app.forge.gym.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class GymViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val problem = MutableStateFlow<String?>(null)
    val startupProblem = problem.asStateFlow()
    private val initial = runCatching { store.read() }.getOrElse {
        problem.value = "Local storage could not be read. Changes are blocked. Restore a backup or recover the previous revision in Settings."
        AppState()
    }
    private val mutableState = MutableStateFlow(initial)
    val state = mutableState.asStateFlow()
    val messages = Channel<String>(Channel.UNLIMITED)
    val saving = MutableStateFlow(false)
    private data class Change(val recover: Boolean, val onSaved: ((AppState) -> Unit)?, val transform: (AppState) -> AppState)
    private val changes = Channel<Change>(Channel.UNLIMITED)
    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (change in changes) {
                saving.value = true
                try {
                    require(problem.value == null || change.recover) { "Restore or recover local storage before making changes." }
                    val updated = change.transform(mutableState.value)
                    store.write(updated)
                    mutableState.value = updated
                    change.onSaved?.let { callback -> withContext(Dispatchers.Main) { callback(updated) } }
                    if (change.recover) problem.value = null
                } catch (e: Exception) { messages.send(e.message ?: "Could not save changes.") }
                finally { saving.value = false }
            }
        }
    }
    fun change(recover: Boolean = false, onSaved: ((AppState) -> Unit)? = null, transform: (AppState) -> AppState) { changes.trySend(Change(recover, onSaved, transform)) }
    fun start(type: WorkoutType, onStarted: (String) -> Unit) = change(onSaved = { onStarted(it.sessions.last().id) }) { state ->
        Training.start(state, type, System.currentTimeMillis())
    }
    fun update(id: String, transform: (Workout) -> Workout) = change { Training.update(it, id, transform) }
    fun selectType(id: String, type: WorkoutType) = change { Training.selectType(it, id, type) }
    fun finish(id: String) = change { Training.finish(it, id, System.currentTimeMillis()) }
    fun reopen(id: String) = update(id) { it.copy(finishedAt = null) }
    fun confirm(id: String) = change { Training.confirm(it, id) }
    fun delete(id: String) = change { it.copy(sessions = it.sessions.filter { w -> w.id != id }, restEndsAt = null) }
    fun editSet(id: String, exerciseId: String, set: SetEntry) = change { Training.editSet(it, id, exerciseId, set) }
    fun toggleSet(id: String, exerciseId: String, set: SetEntry) = change { state ->
        val current = state.sessions.first { it.id == id }.exercises.first { it.exerciseId == exerciseId }.sets.first { it.id == set.id }
        Training.toggleSet(state, id, exerciseId, current, System.currentTimeMillis())
    }
    fun addExercise(id: String, exercise: Exercise) = change { state ->
        val library = if (state.exercises.any { it.id == exercise.id }) state.exercises else state.exercises + exercise
        Training.update(state.copy(exercises = library), id) { w ->
            require(w.exercises.none { it.exerciseId == exercise.id }) { "This exercise is already in the session." }
            val previous = Training.previousSet(state, exercise.id)
            w.copy(exercises = w.exercises + WorkoutExercise(exercise.id, exercise.name, exercise.group, List(3) { SetEntry(weight = previous?.weight ?: "0") }))
        }
    }
    fun saveDay(plan: DayPlan, custom: List<Exercise>) = change { state ->
        state.copy(program = state.program.map { if (it.day == plan.day) plan else it }, exercises = (state.exercises + custom).distinctBy { it.id })
    }
    suspend fun exportBackup(): String = withContext(Dispatchers.IO) { StateCodec.encode(state.value) }
    fun importBackup(text: String) = change(recover = true) { StateCodec.decode(text) }
    fun recover() = change(recover = true) { store.previous() }
    fun importNotes(text: String) = change { HistoryImporter.import(it, text) }
}
