package dev.kian.mymettle.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kian.mymettle.data.local.MyMettleDatabase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NBio7DBackupCoverageTest {
    private lateinit var database: MyMettleDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java).build()
        database.openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun room15FullBackupEnumerates7DAnd7EDerivedTablesWithoutSpecialCases() = runBlocking {
        val backup = JSONObject(NativeFullBackupRepository(database).exportJson())
        assertEquals(15, backup.getInt("databaseSchemaVersion"))
        val tableNames = backup.getJSONArray("tables").let { tables ->
            buildSet {
                for (index in 0 until tables.length()) add(tables.getJSONObject(index).getString("name"))
            }
        }

        assertTrue("model_config_definition" in tableNames)
        assertTrue("inference_model_manifest" in tableNames)
        assertTrue("inference_model_manifest_entry" in tableNames)
        assertTrue("inference_run" in tableNames)
        assertTrue("capability_state" in tableNames)
        assertTrue("capability_parameter_state" in tableNames)
        assertTrue("set_demand_estimate" in tableNames)
        assertTrue("muscle_set_dose" in tableNames)
        assertTrue("muscle_session_dose" in tableNames)
        assertTrue("n_bio_7e_run" in tableNames)
        assertTrue("n_bio_7e_temporal_state" in tableNames)
        assertTrue("n_bio_7e_context_module_state" in tableNames)
        assertTrue("n_bio_7e_context_signal" in tableNames)
        assertTrue("n_bio_7e_context_module_status" in tableNames)
    }
}
