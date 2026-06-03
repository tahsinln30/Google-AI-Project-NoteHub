package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "progress_entries")
data class ProgressEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // format YYYY-MM-DD
    val studyHours: Float,
    val completedTasks: Int,
    val notesWrittenCount: Int,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
