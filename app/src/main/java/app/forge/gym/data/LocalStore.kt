package app.forge.gym.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.forge.gym.model.AppState

/** SQLite transaction retains the previous valid revision alongside the current snapshot. */
class LocalStore(private val context: Context) : SQLiteOpenHelper(context, "forge.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE app_state (id INTEGER PRIMARY KEY, data TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    private fun raw(id: Int): String? = readableDatabase.rawQuery("SELECT data FROM app_state WHERE id = ?", arrayOf(id.toString())).use {
        if (it.moveToFirst()) it.getString(0) else null
    }
    fun read(): AppState {
        raw(1)?.let { return StateCodec.decode(it) }
        val state = if ("SeedHistory.json" in context.assets.list("").orEmpty()) {
            StateCodec.decode(context.assets.open("SeedHistory.json").bufferedReader().use { it.readText() })
        } else {
            HistoryImporter.import(AppState(), context.assets.open("TrainingHistory.md").bufferedReader().use { it.readText() })
        }
        write(state)
        return state
    }
    fun previous(): AppState = StateCodec.decode(requireNotNull(raw(2)) { "No previous saved revision is available yet." })
    fun write(state: AppState) {
        val encoded = StateCodec.encode(state)
        StateCodec.decode(encoded)
        val old = raw(1)?.takeIf { runCatching { StateCodec.decode(it) }.isSuccess }
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (old != null) insert(db, 2, old)
            insert(db, 1, encoded)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun insert(db: SQLiteDatabase, id: Int, text: String) {
        val values = ContentValues().apply { put("id", id); put("data", text) }
        check(db.insertWithOnConflict("app_state", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Could not save data." }
    }
}
