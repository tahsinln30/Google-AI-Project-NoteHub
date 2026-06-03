package com.example.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val noteDao: NoteDao,
    private val progressDao: ProgressDao
) {
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val allProgress: Flow<List<ProgressEntry>> = progressDao.getAllProgress()

    suspend fun getNoteById(id: Int): Note? = noteDao.getNoteById(id)

    suspend fun insertNote(note: Note): Long = noteDao.insertNote(note)

    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)

    suspend fun deleteNoteById(id: Int) = noteDao.deleteNoteById(id)

    suspend fun insertProgress(entry: ProgressEntry): Long = progressDao.insertProgress(entry)

    suspend fun deleteProgress(entry: ProgressEntry) = progressDao.deleteProgress(entry)

    suspend fun getProgressByDate(date: String): ProgressEntry? = progressDao.getProgressByDate(date)

    // Simulates an API-driven cloud synchronization for student notes
    suspend fun simulateSync(syncCode: String, currentNotes: List<Note>): List<Note> {
        // Mark all current notes as synced locally
        val updatedLocalNotes = currentNotes.map { note ->
            if (!note.isSynced) {
                val updatedNote = note.copy(isSynced = true, synchedAt = System.currentTimeMillis())
                noteDao.insertNote(updatedNote)
                updatedNote
            } else {
                note
            }
        }

        // If the student inputs a specific external sharing code, we can "download" custom pre-defined study notes
        // simulating importing notes from their laptop or classmate!
        val downloadedNotes = when (syncCode.uppercase().trim()) {
            "STUDY101" -> listOf(
                Note(
                    title = "📚 Algorithms & Data Structures Cheatsheet",
                    content = """# Algorithms & Data Structures Cheatsheet
This note was synced from your Laptop!

## 1. Time Complexities
- **Binary Search**: `O(log n)`
- **Quick Sort**: `O(n log n)` average, `O(n²)` worst case
- **Merge Sort**: `O(n log n)` guaranteed

## 2. Essential Code Template (Kotlin Dijkstra)
```kotlin
fun dijkstra(start: Node) {
    val queue = PriorityQueue<Node>()
    start.distance = 0
    queue.add(start)
    
    while(queue.isNotEmpty()) {
        val curr = queue.poll()
        // explore neighbors...
    }
}
```

---
*Last synced: Just now via CloudSync*""",
                    category = "Computer Science",
                    isSynced = true,
                    synchedAt = System.currentTimeMillis()
                ),
                Note(
                    title = "🧪 Chemistry Lab: Acid-Base Titration",
                    content = """# Lab Experiment 4: Titration
Synced from Classmate's Shared Note!

### Objective
Determine the concentration of hydrochloric acid (HCl) by titrating against a standard sodium hydroxide (NaOH) solution.

### Reaction
$$\text{HCl} + \text{NaOH} \rightarrow \text{NaCl} + \text{H}_2\text{O}$$

### Key Findings
1. Phenolphthalein turns **faint pink** at endpoint.
2. Average volume of NaOH used: `18.4 mL`.
3. Calculated HCl Molarity: `0.092 M`.

> Ensure dropwise addition near endpoint to avoid overshooting!""",
                    category = "Chemistry",
                    isSynced = true,
                    synchedAt = System.currentTimeMillis()
                )
            )
            "EXAM2026" -> listOf(
                Note(
                    title = "✍️ Physics 2 Midterm Prep Guide",
                    content = """# Physics II: Electromagnetism Midterm
Synced from Tablet Companion!

## Key Formulas to Memorize
1. **Gauss's Law**:
   ${'$'}${'$'}\Phi_E = \oint E \cdot dA = \frac{Q_{encl}}{\varepsilon_0}${'$'}${'$'}
2. **Coulomb's Law**:
   ${'$'}${'$'}F = k_e \frac{q_1 q_2}{r^2}${'$'}${'$'}
3. **Capacitance**:
   ${'$'}${'$'}C = \frac{Q}{V}${'$'}${'$'}

### Exam Strategy
- Spend 15 mins on multiple choice
- Verify units (Farads, Coulombs, Teslas)
- Draw free-body diagrams for field forces.""",
                    category = "Physics",
                    isSynced = true,
                    synchedAt = System.currentTimeMillis()
                )
            )
            else -> emptyList() // No new external notes for generic code, just complete local sync
        }

        // Insert downloaded notes into local database
        for (item in downloadedNotes) {
            noteDao.insertNote(item)
        }

        return updatedLocalNotes + downloadedNotes
    }
}
