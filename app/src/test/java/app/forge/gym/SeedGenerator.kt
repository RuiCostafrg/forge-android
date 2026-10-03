package app.forge.gym

import app.forge.gym.data.*
import app.forge.gym.model.*
import java.io.File
import org.json.JSONObject

object SeedGenerator {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0])
        val state = HistoryImporter.import(AppState(), File(root, "app/src/main/assets/TrainingHistory.md").readText())
        val text = StateCodec.encode(state)
        check(StateCodec.decode(text) == state)
        File(root, "app/src/main/assets/SeedHistory.json").writeText(text)
        val report = JSONObject().put("entries", state.sessions.size).put("exercises", state.sessions.sumOf { it.exercises.size })
            .put("explicitSets", state.sessions.sumOf { it.exercises.sumOf { e -> e.sets.size } }).put("completeDates", state.sessions.count { it.date != null })
            .put("needsReview", state.sessions.count { it.needsReview }).put("source", "TrainingHistory.md")
        File(root, "IMPORT_REPORT.json").writeText(report.toString(2))
        println(report.toString(2))
    }
}
