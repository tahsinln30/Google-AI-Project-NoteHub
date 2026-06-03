package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY lastUpdated DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Int): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Int)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress_entries ORDER BY date DESC")
    fun getAllProgress(): Flow<List<ProgressEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(entry: ProgressEntry): Long

    @Delete
    suspend fun deleteProgress(entry: ProgressEntry)

    @Query("SELECT * FROM progress_entries WHERE date = :date LIMIT 1")
    suspend fun getProgressByDate(date: String): ProgressEntry?
}
