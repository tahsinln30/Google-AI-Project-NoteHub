package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.util.GitHubConfig
import com.example.util.GitHubService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = NoteRepository(db.noteDao(), db.progressDao())

    // Search and filter states
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("All")

    // Sync States
    val syncCode = MutableStateFlow("")
    val isSyncing = MutableStateFlow(false)
    val syncStatus = MutableStateFlow("Local storage ready (Cloud unsynced)")
    val lastSyncedTime = MutableStateFlow<Long?>(null)

    // GitHub Integration States
    val githubConfig = MutableStateFlow<GitHubConfig?>(null)
    val githubUser = MutableStateFlow<String?>(null)
    val githubLogs = MutableStateFlow<List<String>>(emptyList())
    val isGitHubSyncing = MutableStateFlow(false)
    val isGitHubVerifying = MutableStateFlow(false)

    fun addGithubLog(message: String) {
        val current = githubLogs.value
        githubLogs.value = current + listOf(message)
    }

    fun clearGithubLogs() {
        githubLogs.value = emptyList()
    }

    fun initializeGitHub(context: Context) {
        viewModelScope.launch {
            val config = GitHubService.getConfig(context)
            githubConfig.value = config
            if (config.isValid) {
                verifyGitHubCredentials(config.token)
            }
        }
    }

    fun saveGitHubConfig(context: Context, token: String, repo: String, branch: String, path: String) {
        viewModelScope.launch {
            GitHubService.saveConfig(context, token, repo, branch, path)
            val newConfig = GitHubService.getConfig(context)
            githubConfig.value = newConfig
            if (newConfig.isValid) {
                verifyGitHubCredentials(newConfig.token)
            } else {
                githubUser.value = null
            }
        }
    }

    fun unlinkGitHub(context: Context) {
        viewModelScope.launch {
            GitHubService.clearConfig(context)
            githubConfig.value = GitHubService.getConfig(context)
            githubUser.value = null
            githubLogs.value = emptyList()
        }
    }

    fun verifyGitHubCredentials(token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            isGitHubVerifying.value = true
            val username = GitHubService.verifyCredentials(token)
            githubUser.value = username
            isGitHubVerifying.value = false
        }
    }

    fun pushSingleNoteToGitHub(context: Context, note: Note, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            isGitHubSyncing.value = true
            val success = GitHubService.pushNoteToRepo(context, note) { log ->
                addGithubLog(log)
            }
            if (success) {
                val updatedNote = note.copy(isSynced = true, synchedAt = System.currentTimeMillis())
                repository.insertNote(updatedNote)
            }
            isGitHubSyncing.value = false
            onResult(success)
        }
    }

    fun pushAllNotesToGitHub(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            clearGithubLogs()
            isGitHubSyncing.value = true
            addGithubLog("> Initializing batch note push to GitHub...")
            val notesToPush = notes.value
            if (notesToPush.isEmpty()) {
                addGithubLog("⚠️ No local notes found to push.")
                isGitHubSyncing.value = false
                return@launch
            }
            addGithubLog("> Found [${notesToPush.size}] notes to push. Processing sequential push commits...")
            
            var successCount = 0
            for (note in notesToPush) {
                addGithubLog("> Pushing note \"${note.title}\"...")
                val success = GitHubService.pushNoteToRepo(context, note) { log ->
                    addGithubLog("  $log")
                }
                if (success) {
                    successCount++
                    val updatedNote = note.copy(isSynced = true, synchedAt = System.currentTimeMillis())
                    repository.insertNote(updatedNote)
                }
            }
            addGithubLog("🏁 Batch job complete! [ $successCount / ${notesToPush.size} ] successfully committed to GitHub.")
            isGitHubSyncing.value = false
        }
    }

    fun pullNotesFromGitHub(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            clearGithubLogs()
            isGitHubSyncing.value = true
            addGithubLog("> Initializing pull courseworks and notes from GitHub...")
            val pulledNotes = GitHubService.pullNotesFromRepo(context) { log ->
                addGithubLog(log)
            }
            if (pulledNotes.isEmpty()) {
                addGithubLog("⚠️ No eligible markdown records downloaded.")
                isGitHubSyncing.value = false
                return@launch
            }
            
            addGithubLog("> Syncing pulled records to local SQLite Database...")
            var merged = 0
            var newNotesCount = 0
            for (pulled in pulledNotes) {
                val existingList = repository.allNotes.first()
                val existing = existingList.find { it.title.trim().equals(pulled.title.trim(), ignoreCase = true) }
                if (existing != null) {
                    merged++
                    val updated = existing.copy(
                        content = pulled.content,
                        category = pulled.category.ifBlank { existing.category },
                        isSynced = true,
                        synchedAt = System.currentTimeMillis(),
                        lastUpdated = System.currentTimeMillis()
                    )
                    repository.insertNote(updated)
                    addGithubLog("  🔄 Updated local copy matching: \"${pulled.title}\"")
                } else {
                    newNotesCount++
                    repository.insertNote(pulled)
                    addGithubLog("  ➕ Imported new note: \"${pulled.title}\"")
                }
            }
            addGithubLog("🏁 Pull complete! Merged $merged updates, imported $newNotesCount new notes.")
            isGitHubSyncing.value = false
        }
    }

    // Notes list state, filtered by search query and category
    val notes: StateFlow<List<Note>> = combine(
        repository.allNotes,
        searchQuery,
        selectedCategory
    ) { baseNotes, query, cat ->
        var filteredList = baseNotes
        
        // Filter by category
        if (cat != "All") {
            filteredList = filteredList.filter { it.category.equals(cat, ignoreCase = true) }
        }
        
        // Filter by search query
        if (query.isNotEmpty()) {
            filteredList = filteredList.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.content.contains(query, ignoreCase = true)
            }
        }
        
        filteredList
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Progress logs state
    val progressLogs: StateFlow<List<ProgressEntry>> = repository.allProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Pre-populate if database is clean and it is the first run
    init {
        viewModelScope.launch {
            val prefs = application.getSharedPreferences("PrepPrefs", Context.MODE_PRIVATE)
            val alreadyPopulated = prefs.getBoolean("AlreadyPopulated", false)
            if (!alreadyPopulated) {
                repository.allNotes.first().let { list ->
                    if (list.isEmpty()) {
                        initializeStarterNotes()
                    }
                }
                repository.allProgress.first().let { logs ->
                    if (logs.isEmpty()) {
                        initializeStarterProgress()
                    }
                }
                prefs.edit().putBoolean("AlreadyPopulated", true).apply()
            }
        }
    }

    private suspend fun initializeStarterNotes() {
        val notesList = listOf(
            Note(
                title = "🚀 Welcome to NoteHub",
                content = """# NoteHub Student System
Welcome to your unified markdown study environment!

## ⚡ Key Features
- **Markdown Rendering**: Type structured markdown and see formatted titles, bullets, code blocks, and quote callouts!
- **Cross-device Sync Simulation**: Tap the Cloud icon on the toolbar, enter a sync code like `STUDY101` or `EXAM2026`, and pull in content instantly.
- **Progress Hub**: Set goals, log study hours, and monitor tasks.
- **PDF Exporter Engine**: Compile layout formatting automatically.

## 📝 Markdown Guide
- `# Header 1` creates a primary title heading.
- `## Header 2` creates subheadings.
- `> Quote block` renders beautiful highlighted notes.
- Use `**bold text**` and `*italic text*` inline to key out concepts.
- Prefix lines with `- ` to form dynamic bulleted checklists.
- Wrap snippets with ``` (triple backticks) to trigger isolated coding block visualizers.

---
*Create your first note by clicking the ➕ button below!*""",
                category = "Guide",
                isSynced = false
            ),
            Note(
                title = "📐 Calculus II: Integration Methods",
                content = """# Calculus II: Integration Methods
My Exam review summary. Calculus exam on Wednesday morning!

## 1. Integration by Parts
Formula:
${'$'}${'$'}\int u \, dv = u v - \int v \, du${'$'}${'$'}

Method to choose `u` - **LIATE** Rule:
  1. **L**ogarithmic functions
  2. **I**nverse trigonometric
  3. **A**lgebraic
  4. **T**rigonometric
  5. **E**xponential

## 2. Standard Trigonometric Sub
- For term ${'$'}\sqrt{a^2 - x^2}${'$'}, choose substitute ${'$'}x = a \sin(\theta)${'$'}
- For term ${'$'}\sqrt{a^2 + x^2}${'$'}, choose substitute ${'$'}x = a \tan(\theta)${'$'}

> Pro-tip: Always rewrite the bounds of integration immediately when performing variable substitution on definite integrals!""",
                category = "Mathematics",
                isSynced = false
            )
        )
        for (item in notesList) {
            repository.insertNote(item)
        }
    }

    private suspend fun initializeStarterProgress() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val now = System.currentTimeMillis()
        
        val yesterday = sdf.format(Date(now - 1 * 24 * 60 * 60 * 1000L))
        val dayBefore = sdf.format(Date(now - 2 * 24 * 60 * 60 * 1000L))
        
        repository.insertProgress(ProgressEntry(
            date = dayBefore,
            studyHours = 4.5f,
            completedTasks = 3,
            notesWrittenCount = 1,
            summary = "Reviewed Calculus Integration methods and created a comprehensive master formulary. Completed practice worksheet set 4."
        ))
        
        repository.insertProgress(ProgressEntry(
            date = yesterday,
            studyHours = 5.2f,
            completedTasks = 5,
            notesWrittenCount = 2,
            summary = "Wrote chemistry lab writeup for Experiment 4. Solved sample physics midterm problems under mock exam timers."
        ))
    }

    // Insert Note
    fun saveNote(title: String, content: String, category: String, id: Int = 0) {
        viewModelScope.launch {
            val validCategory = if (category.isBlank()) "General" else category.trim()
            val cleanTitle = if (title.isBlank()) "Untitled Note" else title.trim()
            
            val newNote = if (id > 0) {
                // Editing existing note
                val existing = repository.getNoteById(id)
                existing?.copy(
                    title = cleanTitle,
                    content = content,
                    category = validCategory,
                    lastUpdated = System.currentTimeMillis(),
                    isSynced = false // Reset sync when modified locally
                ) ?: Note(title = cleanTitle, content = content, category = validCategory)
            } else {
                Note(title = cleanTitle, content = content, category = validCategory)
            }
            repository.insertNote(newNote)
        }
    }

    // Delete Note
    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    // Log daily study progress
    fun logProgress(hours: Float, completedTasksCount: Int, notesWritten: Int, summaryText: String) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateStr = sdf.format(Date())
            
            // Query if there's already progress logged for today so we update it
            val existing = repository.getProgressByDate(dateStr)
            val updatedProgress = existing?.copy(
                studyHours = hours,
                completedTasks = completedTasksCount,
                notesWrittenCount = notesWritten,
                summary = summaryText,
                timestamp = System.currentTimeMillis()
            ) ?: ProgressEntry(
                date = dateStr,
                studyHours = hours,
                completedTasks = completedTasksCount,
                notesWrittenCount = notesWritten,
                summary = summaryText
            )
            repository.insertProgress(updatedProgress)
        }
    }

    // Delete daily study progress
    fun deleteProgress(entry: ProgressEntry) {
        viewModelScope.launch {
            repository.deleteProgress(entry)
        }
    }

    // Handles the device syncing request
    fun syncDevices(targetCode: String) {
        if (targetCode.isBlank()) return
        
        viewModelScope.launch {
            isSyncing.value = true
            syncStatus.value = "Connecting to sync server with key..."
            
            // Simulate networking delay
            kotlinx.coroutines.delay(1800)
            
            try {
                // Get current notes
                val currentLocalNotes = repository.allNotes.first()
                val responseNotes = repository.simulateSync(targetCode, currentLocalNotes)
                
                isSyncing.value = false
                lastSyncedTime.value = System.currentTimeMillis()
                syncStatus.value = "Sync success! Merged and imported notes matching '$targetCode'"
            } catch (e: Exception) {
                isSyncing.value = false
                syncStatus.value = "Error syncing: ${e.localizedMessage}"
            }
        }
    }

    // Get list of unique categories to fill filter-tag selector
    val categories: StateFlow<List<String>> = repository.allNotes
        .map { notesList ->
            val uniq = notesList.map { it.category }.distinct().filter { it.isNotBlank() }
            listOf("All") + uniq
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf("All")
        )
}

class NotesViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotesViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
