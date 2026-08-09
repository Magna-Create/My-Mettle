package dev.kian.mymettle.data.local

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var instance: MyMettleDatabase? = null

    fun get(context: Context): MyMettleDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            MyMettleDatabase::class.java,
            "my-mettle.db",
        ).build().also { instance = it }
    }
}
