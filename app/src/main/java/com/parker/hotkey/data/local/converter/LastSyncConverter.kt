package com.parker.hotkey.data.local.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.parker.hotkey.domain.model.LastSync

class LastSyncConverter {
    private val gson = Gson()
    
    @TypeConverter
    fun fromLastSync(lastSync: LastSync): String {
        return gson.toJson(lastSync)
    }
    
    @TypeConverter
    fun toLastSync(value: String): LastSync {
        return gson.fromJson(value, LastSync::class.java)
    }
} 