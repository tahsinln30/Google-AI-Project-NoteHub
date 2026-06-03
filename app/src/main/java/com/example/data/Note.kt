package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val category: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val synchedAt: Long = 0L
) : Serializable
