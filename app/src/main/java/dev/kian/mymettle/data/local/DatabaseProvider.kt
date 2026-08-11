package dev.kian.mymettle.data.local

import android.content.Context
import androidx.room.Room
import dev.kian.mymettle.data.reference.ReferenceSeedCallback

object DatabaseProvider {
    @Volatile
    private var instance: MyMettleDatabase? = null

    fun get(context: Context): MyMettleDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            MyMettleDatabase::class.java,
            "my-mettle.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration(true)
            .addCallback(ReferenceSeedCallback(context.applicationContext))
            .build()
            .also { instance = it }
    }
}
