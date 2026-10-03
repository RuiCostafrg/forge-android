package app.forge.gym

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.forge.gym.data.*
import app.forge.gym.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeSmokeTests {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun startLogFinishAndReopenNativeSession() {
        compose.onNodeWithText("Start session").assertIsDisplayed()
        // The suggested split depends on today's weekday, so choose an explicit override.
        compose.onNodeWithText("TODAY'S PLAN").assertIsDisplayed()
        val current = LocalStore(InstrumentationRegistry.getInstrumentation().targetContext).use { it.read() }
        val planType = current.program.first { it.day == sundayIndex(java.time.LocalDate.now()) }.type
        val initialLabel = if (planType == WorkoutType.REST) "Custom" else planType.label
        compose.onNodeWithText(initialLabel, useUnmergedTree = true).performClick()
        compose.onNodeWithText("Push", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Start session").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Push session").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("Finish session").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Finish", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Reopen to edit sets").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reopen to edit sets").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("DRAFT").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Sessions").performClick()
        compose.onNodeWithText("Drafts").performScrollTo().performClick()
        compose.onNodeWithText("Push session").assertExists()
    }

    @Test fun localDatabaseRetainsPreviousRevisionAndRejectsCorruptBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        // Exercise the real SQLite implementation in a separate database directory.
        val isolated = object : android.content.ContextWrapper(testContext) {
            override fun getDatabasePath(name: String) = java.io.File(context.cacheDir, "test-$name")
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?) =
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?, errorHandler: android.database.DatabaseErrorHandler?) =
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
        }
        isolated.getDatabasePath("forge.db").delete()
        val first = AppState(originalNotes = "revision one")
        val second = first.copy(restSeconds = 120)
        LocalStore(isolated).use { store -> store.write(first); store.write(second); assertEquals(first, store.previous()); assertEquals(second, store.read()) }
        LocalStore(isolated).use { store ->
            assertEquals(second, store.read())
            assertTrue(runCatching { StateCodec.decode("broken JSON") }.isFailure)
            assertEquals(second, store.read())
            store.write(store.previous()); assertEquals(first, store.read())
        }
    }
}
