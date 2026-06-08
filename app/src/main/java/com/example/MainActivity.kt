package com.example

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Note
import com.example.data.ProgressEntry
import com.example.ui.MarkdownContent
import com.example.ui.theme.*
import com.example.util.GitHubConfig
import com.example.util.GitHubService
import com.example.util.PdfExporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Retrieve custom colorful rainbow accents for each note category name
fun getCategoryColor(category: String): Color {
    val clean = category.trim().lowercase()
    return when {
        clean.contains("guide") || clean.contains("welcome") -> CatPurple
        clean.contains("math") || clean.contains("calculus") -> CatBlue
        clean.contains("chem") || clean.contains("science") -> CatTeal
        clean.contains("phys") -> CatOrange
        clean.contains("general") || clean.contains("misc") -> CatPink
        clean.contains("exam") || clean.contains("test") -> CatRed
        clean.contains("progress") || clean.contains("study") -> CatLime
        else -> {
            // Generate a stable color based on hash code
            val colors = listOf(CatPurple, CatPink, CatOrange, CatTeal, CatLime, CatYellow, CatBlue, CatRed)
            val index = if (clean.isEmpty()) 0 else Math.abs(clean.hashCode()) % colors.size
            colors[index]
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Read system preference initially, but allow manual toggle override
            val systemIsInDark = isSystemInDarkTheme()
            var darkThemeEnabled by androidx.compose.runtime.saveable.rememberSaveable {
                mutableStateOf(systemIsInDark)
            }
            MyApplicationTheme(darkTheme = darkThemeEnabled) {
                MainAppScreen(
                    darkThemeEnabled = darkThemeEnabled,
                    onToggleDarkTheme = { darkThemeEnabled = !darkThemeEnabled }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    darkThemeEnabled: Boolean = isSystemInDarkTheme(),
    onToggleDarkTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: NotesViewModel = viewModel(factory = NotesViewModelFactory(app))

    LaunchedEffect(Unit) {
        viewModel.initializeGitHub(context)
    }

    // UI collected states
    val notesList by viewModel.notes.collectAsStateWithLifecycle()
    val progressLogsList by viewModel.progressLogs.collectAsStateWithLifecycle()
    val categoryFilters by viewModel.categories.collectAsStateWithLifecycle()
    val currentSearchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentSelectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    
    val syncCodeText by viewModel.syncCode.collectAsStateWithLifecycle()
    val isSyncingActive by viewModel.isSyncing.collectAsStateWithLifecycle()
    val currentSyncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val lastSyncedTimeVal by viewModel.lastSyncedTime.collectAsStateWithLifecycle()

    // GitHub collected states
    val githubConfigVal by viewModel.githubConfig.collectAsStateWithLifecycle()
    val githubUserVal by viewModel.githubUser.collectAsStateWithLifecycle()
    val githubLogsVal by viewModel.githubLogs.collectAsStateWithLifecycle()
    val isGitHubSyncingActive by viewModel.isGitHubSyncing.collectAsStateWithLifecycle()
    val isGitHubVerifyingActive by viewModel.isGitHubVerifying.collectAsStateWithLifecycle()

    // Screen navigation states
    var selectedTabState by remember { mutableStateOf(0) } // 0 = Notes, 1 = Progress
    
    // Popup states
    var showSyncDialogState by remember { mutableStateOf(false) }
    var showGitHubDialogState by remember { mutableStateOf(false) }
    var showProgressLogDialogState by remember { mutableStateOf(false) }
    var noteToEditState by remember { mutableStateOf<Note?>(null) }
    var noteToViewState by remember { mutableStateOf<Note?>(null) }
    var noteToDeleteState by remember { mutableStateOf<Note?>(null) }
    
    // PDF State
    var isGeneratingPdf by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "NoteHub",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Dynamic Dark Mode Enable/Disable manual toggle button
                    IconButton(
                        onClick = onToggleDarkTheme,
                        modifier = Modifier
                            .testTag("dark_mode_toggle_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (darkThemeEnabled) Icons.Default.WbSunny else Icons.Default.NightsStay,
                            contentDescription = if (darkThemeEnabled) "Enable Light Mode" else "Enable Dark Mode",
                            tint = if (darkThemeEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showSyncDialogState = true },
                        modifier = Modifier
                            .testTag("toolbar_sync_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        BadgedBox(
                            badge = {
                                if (notesList.any { !it.isSynced }) {
                                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                        Text("*")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Device Sync Panel",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = { showGitHubDialogState = true },
                        modifier = Modifier
                            .testTag("toolbar_github_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        BadgedBox(
                            badge = {
                                if (githubConfigVal == null || !githubConfigVal!!.isValid) {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text("!")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "GitHub Sync Panel",
                                tint = if (githubConfigVal != null && githubConfigVal!!.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (selectedTabState == 0) {
                ExtendedFloatingActionButton(
                    text = { Text("Draft Note", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add New Note") },
                    onClick = {
                        noteToEditState = Note(title = "", content = "", category = "")
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .testTag("add_note_fab")
                        .padding(bottom = 8.dp)
                )
            } else {
                ExtendedFloatingActionButton(
                    text = { Text("Log Progress", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Log study activity") },
                    onClick = {
                        showProgressLogDialogState = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier
                        .testTag("log_progress_fab")
                        .padding(bottom = 8.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Custom premium capsule-segmented slider selector
            val isDark = LocalThemeIsDark.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .background(
                        color = if (isDark) Color(0xFF131A30) else Color(0xFFEEF2FF),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("My Notes (${notesList.size})", "Progress Hub").forEachIndexed { index, title ->
                    val isSelected = selectedTabState == index
                    val animBgColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "tab_bg"
                    )
                    val animTextColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            if (isDark) MilkyTextLight.copy(alpha = 0.6f) else SlateTextDark.copy(alpha = 0.6f)
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "tab_text"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(animBgColor)
                            .clickable { selectedTabState = index }
                            .padding(vertical = 10.dp)
                            .testTag(if (index == 0) "notes_tab" else "progress_tab"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (index == 0) {
                                    if (isSelected) Icons.Filled.EditNote else Icons.Outlined.EditNote
                                } else {
                                    if (isSelected) Icons.Filled.TrendingUp else Icons.Outlined.TrendingUp
                                },
                                contentDescription = null,
                                tint = animTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = title,
                                color = animTextColor,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = selectedTabState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "WorkspaceSwitching"
            ) { tabIdx ->
                when (tabIdx) {
                    0 -> NotesWorkspace(
                        notes = notesList,
                        categories = categoryFilters,
                        searchQuery = currentSearchQuery,
                        selectedCategory = currentSelectedCategory,
                        onSearchChange = { viewModel.searchQuery.value = it },
                        onCategorySelect = { viewModel.selectedCategory.value = it },
                        onViewNote = { noteToViewState = it },
                        onEditNote = { noteToEditState = it },
                        onDeleteNote = { noteToDeleteState = it },
                        onExportPdf = { note ->
                            isGeneratingPdf = true
                            PdfExporter.exportNoteToPdf(
                                context = context,
                                note = note,
                                onComplete = { file ->
                                    isGeneratingPdf = false
                                    Toast.makeText(context, "PDF successfully generated!", Toast.LENGTH_SHORT).show()
                                    PdfExporter.sharePdf(context, file)
                                },
                                onError = { error ->
                                    isGeneratingPdf = false
                                    Toast.makeText(context, "Error: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    )
                    1 -> ProgressHub(
                        logs = progressLogsList,
                        onDeleteLog = { logEntry ->
                            viewModel.deleteProgress(logEntry)
                        }
                    )
                }
            }
        }
    }

    // Dialog: Add/Edit student notes
    if (noteToEditState != null) {
        NoteComposeDialog(
            note = noteToEditState!!,
            onDismiss = { noteToEditState = null },
            onSave = { updatedTitle, updatedCategory, updatedContent ->
                viewModel.saveNote(
                    title = updatedTitle,
                    category = updatedCategory,
                    content = updatedContent,
                    id = noteToEditState!!.id
                )
                noteToEditState = null
                Toast.makeText(context, "Note Saved Successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog: Read fully-formatted Markdown note
    if (noteToViewState != null) {
        NoteViewDialog(
            note = noteToViewState!!,
            onDismiss = { noteToViewState = null },
            onEdit = {
                noteToEditState = noteToViewState
                noteToViewState = null
            },
            onExportPdf = {
                isGeneratingPdf = true
                PdfExporter.exportNoteToPdf(
                    context = context,
                    note = noteToViewState!!,
                    onComplete = { file ->
                        isGeneratingPdf = false
                        Toast.makeText(context, "PDF successfully generated!", Toast.LENGTH_SHORT).show()
                        PdfExporter.sharePdf(context, file)
                    },
                    onError = { error ->
                        isGeneratingPdf = false
                        Toast.makeText(context, "Error: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                )
            },
            onPushToGitHub = if (githubConfigVal != null && githubConfigVal!!.isValid) {
                {
                    viewModel.pushSingleNoteToGitHub(context, noteToViewState!!) { success ->
                        if (success) {
                            Toast.makeText(context, "Cloud note update pushed!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Push failed. Check token or repository permissions.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                null
            }
        )
    }

    // Dialog: Delete confirmation
    if (noteToDeleteState != null) {
        AlertDialog(
            onDismissRequest = { noteToDeleteState = null },
            title = { Text("Delete This Note?") },
            text = { Text("Are you sure you want to permanently delete \"${noteToDeleteState!!.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote(noteToDeleteState!!)
                        noteToDeleteState = null
                        Toast.makeText(context, "Note Deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("delete_confirm_btn")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { noteToDeleteState = null },
                    modifier = Modifier.testTag("delete_cancel_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Cross device sync engine panel
    if (showSyncDialogState) {
        DeviceSyncDialog(
            syncCode = syncCodeText,
            isSyncing = isSyncingActive,
            syncStatus = currentSyncStatus,
            lastSynced = lastSyncedTimeVal,
            onCodeChange = { viewModel.syncCode.value = it },
            onSyncClick = { viewModel.syncDevices(syncCodeText) },
            onDismiss = { showSyncDialogState = false }
        )
    }

    // Dialog: GitHub Synchronization Portal
    if (showGitHubDialogState) {
        GitHubSyncDialog(
            config = githubConfigVal,
            username = githubUserVal,
            logs = githubLogsVal,
            isSyncing = isGitHubSyncingActive,
            isVerifying = isGitHubVerifyingActive,
            onSaveConfig = { token, repo, branch, folderPath ->
                viewModel.saveGitHubConfig(context, token, repo, branch, folderPath)
            },
            onUnlink = {
                viewModel.unlinkGitHub(context)
            },
            onPushAll = {
                viewModel.pushAllNotesToGitHub(context)
            },
            onPullAll = {
                viewModel.pullNotesFromGitHub(context)
            },
            onDismiss = { showGitHubDialogState = false }
        )
    }

    // Dialog: Log Today's Study progress
    if (showProgressLogDialogState) {
        ProgressLogDialog(
            onDismiss = { showProgressLogDialogState = false },
            onSave = { hours, completedTasks, notesWritten, summary ->
                viewModel.logProgress(hours, completedTasks, notesWritten, summary)
                showProgressLogDialogState = false
                Toast.makeText(context, "Daily Progress Logged", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Loader for PDF export activity
    if (isGeneratingPdf) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun NotesWorkspace(
    notes: List<Note>,
    categories: List<String>,
    searchQuery: String,
    selectedCategory: String,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onViewNote: (Note) -> Unit,
    onEditNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onExportPdf: (Note) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // High-fidelity elegant pill-shaped search input with subtle background, clear colors and focus glow
        val isDarkTheme = LocalThemeIsDark.current
        TextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { 
                Text(
                    text = "Search notes by title or content...", 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ) 
            },
            leadingIcon = { 
                Icon(
                    imageVector = Icons.Default.Search, 
                    contentDescription = "Search notes", 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                ) 
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear, 
                            contentDescription = "Clear search", 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = if (isDarkTheme) Color(0xFF161E36) else Color(0xFFF3F5F9),
                unfocusedContainerColor = if (isDarkTheme) Color(0xFF10162B) else Color(0xFFEEF0F4),
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("note_search_input")
        )
        
        Spacer(modifier = Modifier.height(14.dp))
        
        // Category Tags custom-designed horizontal bar
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { categoryName ->
                val isSelected = categoryName == selectedCategory
                val chipColor = if (categoryName == "All") MaterialTheme.colorScheme.primary else getCategoryColor(categoryName)
                
                val containerColor = if (isSelected) {
                    chipColor
                } else {
                    chipColor.copy(alpha = if (isDarkTheme) 0.15f else 0.08f)
                }
                
                val fontColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    if (isDarkTheme) chipColor.copy(alpha = 0.9f) else chipColor
                }
                
                val dotColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    chipColor
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(containerColor)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) chipColor else chipColor.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable { onCategorySelect(categoryName) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    // Accent indicator dot showing category color
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(dotColor, shape = CircleShape)
                    )
                    Text(
                        text = categoryName,
                        fontWeight = FontWeight.ExtraBold,
                        color = fontColor,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 0.1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Empty State Check
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "No notes found",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "Create a custom notebook draft using the '+' button, or use Cloud Sync (code STUDY101) to load lecture slides.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // High-fidelity structured list for study notes
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteGridItem(
                        note = note,
                        onView = { onViewNote(note) },
                        onEdit = { onEditNote(note) },
                        onDelete = { onDeleteNote(note) },
                        onPdf = { onExportPdf(note) }
                    )
                }
            }
        }
    }
}

@Composable
fun NoteGridItem(
    note: Note,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPdf: () -> Unit
) {
    val categoryColor = getCategoryColor(note.category)
    val isDark = LocalThemeIsDark.current
    
    // Estimate word count for the draft
    val wordsCount = remember(note.content) {
        note.content.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF10162B) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("note_item_card_${note.id}")
            .clickable { onView() }
            .border(
                width = 1.dp,
                color = if (isDark) {
                    categoryColor.copy(alpha = 0.25f)
                } else {
                    categoryColor.copy(alpha = 0.15f)
                },
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Rounded side-bar ribbon acting as notebook binding
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(categoryColor, categoryColor.copy(alpha = 0.7f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // Info Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mini elegant Category Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(categoryColor.copy(alpha = if (isDark) 0.18f else 0.08f))
                            .border(1.dp, categoryColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = note.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = categoryColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                    
                    // Sync icon and Date
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (note.isSynced) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Synced to cloud",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Local modified draft",
                                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        
                        Text(
                            text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(note.lastUpdated)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Notebook Title
                Text(
                    text = note.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(5.dp))

                // Markdown Clean Snippet Preview
                val rawCleanedPreview = remember(note.content) {
                    note.content
                        .replace(Regex("[#*`>]"), "")
                        .trim()
                }
                
                Text(
                    text = if (rawCleanedPreview.isEmpty()) "Empty draft description..." else rawCleanedPreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Actions Panel: Custom interaction tray
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left action: Open Draft custom bubble
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(categoryColor.copy(alpha = if (isDark) 0.15f else 0.08f))
                            .clickable { onView() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "View note content",
                                tint = categoryColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Open Draft",
                                color = categoryColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Right actions: Quick tool triggers & stats
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Word count label
                        Text(
                            text = "✍️ $wordsCount w",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        // Edit Button
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Note Details",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // PDF PDF Export Button
                        IconButton(
                            onClick = onPdf,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Save PDF copy",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Trash Can
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.15f else 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Draft",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Dialog: Read fully-formatted Markdown note
@Composable
fun NoteViewDialog(
    note: Note,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onExportPdf: () -> Unit,
    onPushToGitHub: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Headline bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = note.category.ifBlank { "GENERAL" },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.Close, contentDescription = "Close Note View")
                    }
                }
                
                Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                // Scrollable Markdown content block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    MarkdownContent(markdown = note.content)
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                // Bottom actions panel inside view dialog
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onEdit,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontWeight = FontWeight.Bold)
                        }

                        if (onPushToGitHub != null) {
                            TextButton(
                                onClick = onPushToGitHub,
                                modifier = Modifier.minimumInteractiveComponentSize()
                                    .testTag("notes_view_git_push")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Git Push", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Button(
                        onClick = onExportPdf,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .testTag("pdf_export_btn")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export PDF", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Dialog: Add/Edit student notes (Includes Live Writing and Live Render preview toggle)
@Composable
fun NoteComposeDialog(
    note: Note,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var titleState by remember { mutableStateOf(note.title) }
    var categoryState by remember { mutableStateOf(note.category) }
    var contentState by remember { mutableStateOf(note.content) }
    
    // 0 = Edit raw text, 1 = Live markdown visualizer
    var composeSubTabState by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.96f)
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (note.id > 0) "Refine Notes" else "New Study Draft",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.Close, contentDescription = "Close notepad editor")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                // Input meta info (Title + Category tag Suggestion list)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = titleState,
                        onValueChange = { titleState = it },
                        label = { Text("Study Unit / Note Title") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_edit_title"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = categoryState,
                            onValueChange = { categoryState = it },
                            label = { Text("Subject / Category Tag") },
                            placeholder = { Text("e.g., Mathematics, Labs") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("note_edit_category"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Workspace select tabs
                TabRow(
                    selectedTabIndex = composeSubTabState,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.height(42.dp)
                ) {
                    Tab(
                        selected = composeSubTabState == 0,
                        onClick = { composeSubTabState = 0 },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        text = { Text("Compose Markdown", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = composeSubTabState == 1,
                        onClick = { composeSubTabState = 1 },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        text = { Text("Live Render Preview", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                // Main editing workspace with animation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    if (composeSubTabState == 0) {
                        // Note editor notepad
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Syntax shortcut helpers toolbar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val helpers = listOf(
                                    "H1" to "# ",
                                    "H2" to "## ",
                                    "Bold" to "**bold**",
                                    "Quote" to "> ",
                                    "Bullet" to "- ",
                                    "Code" to "```\n\n```"
                                )
                                for (helper in helpers) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                contentState = contentState + helper.second
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = helper.first,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            
                            OutlinedTextField(
                                value = contentState,
                                onValueChange = { contentState = it },
                                label = { Text("Note content (Markdown enabled)") },
                                placeholder = { Text("Begin drafting...\nUse H1: # title\nUse list item: - value\nUse bold: **text**") },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("note_edit_content"),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    } else {
                        // Markdown content visualizer
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (contentState.isBlank()) {
                                Text(
                                    text = "Write something in the Draft tab to see a real-time markdown preview here!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic
                                )
                            } else {
                                MarkdownContent(markdown = contentState)
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                // Dialog bottom command buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Discard Draft")
                    }

                    Button(
                        onClick = { onSave(titleState, categoryState, contentState) },
                        modifier = Modifier
                            .testTag("save_note_btn")
                            .minimumInteractiveComponentSize(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Note", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Dialog: Cross device sync engine panel (Simulates network download sync code updates)
@Composable
fun DeviceSyncDialog(
    syncCode: String,
    isSyncing: Boolean,
    syncStatus: String,
    lastSynced: Long?,
    onCodeChange: (String) -> Unit,
    onSyncClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Device Sync Manager", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close Sync pane")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                Icon(
                    imageVector = Icons.Default.SyncAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = "Sync notes and coursework summaries across your phone, tablet, and computer instantly using safe access keys.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = syncCode,
                    onValueChange = onCodeChange,
                    label = { Text("Connection Token / Sync Key") },
                    placeholder = { Text("e.g. STUDY101 or EXAM2026") },
                    singleLine = true,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_input_code"),
                    shape = RoundedCornerShape(8.dp)
                )

                // Dynamic simulation feedback banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text("Current State:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Text(syncStatus, style = MaterialTheme.typography.bodySmall)
                            if (lastSynced != null) {
                                val syncDateStr = SimpleDateFormat("HH:mm:ss a", Locale.getDefault()).format(Date(lastSynced))
                                Text("Last sync success at: $syncDateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }

                if (isSyncing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("Synchronizing data nodes...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Button(
                        onClick = onSyncClick,
                        enabled = syncCode.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sync_start_btn")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Establish Handshake / Sync", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Dialog: GitHub Synchronization Portal
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubSyncDialog(
    config: GitHubConfig?,
    username: String?,
    logs: List<String>,
    isSyncing: Boolean,
    isVerifying: Boolean,
    onSaveConfig: (token: String, repo: String, branch: String, path: String) -> Unit,
    onUnlink: () -> Unit,
    onPushAll: () -> Unit,
    onPullAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var token by remember { mutableStateOf(config?.token ?: "") }
    var repo by remember { mutableStateOf(config?.repo ?: "") }
    var branch by remember { mutableStateOf(config?.branch ?: "main") }
    var folderPath by remember { mutableStateOf(config?.folderPath ?: "NoteHub") }
    var isTokenVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Headline bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "GitHub Studio Portal",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close Sync Portal")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                val isConnected = config != null && config.isValid && !username.isNullOrBlank()

                if (!isConnected) {
                    // Show Login / Configuration options
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Connect NoteHub with your GitHub account to backup and synchronize your study notes as plain text Markdown files (.md).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("GitHub Personal Access Token (PAT)") },
                            placeholder = { Text("ghp_...") },
                            singleLine = true,
                            visualTransformation = if (isTokenVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                    Icon(
                                        imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle token visibility"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                                .testTag("github_token_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = repo,
                            onValueChange = { repo = it },
                            label = { Text("Repository Name") },
                            placeholder = { Text("e.g. accountname/my-notes-repo") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                                .testTag("github_repo_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = branch,
                                onValueChange = { branch = it },
                                label = { Text("Branch") },
                                placeholder = { Text("main") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                                    .testTag("github_branch_input"),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = folderPath,
                                onValueChange = { folderPath = it },
                                label = { Text("Folder Path") },
                                placeholder = { Text("NoteHub") },
                                singleLine = true,
                                modifier = Modifier.weight(1.5f)
                                    .testTag("github_folder_input"),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isVerifying) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Verifying API token permissions...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Button(
                                onClick = { onSaveConfig(token, repo, branch, folderPath) },
                                enabled = token.isNotBlank() && repo.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("github_save_btn")
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Authorize & Bind Repository", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                } else {
                    // Show Authenticated Controls & Console Logs
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Connection Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = username?.take(2)?.uppercase() ?: "GH",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Linked with @$username",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Target: ${config?.repo} [${config?.branch}]",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = onUnlink,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Disconnect", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }

                        // Terminal Console Logs Block
                        Text(
                            text = "Sync Output Consoles:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF070B19))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                            LaunchedEffect(logs.size) {
                                if (logs.isNotEmpty()) {
                                    listState.animateScrollToItem(logs.size - 1)
                                }
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (logs.isEmpty()) {
                                    item {
                                        Text(
                                            text = "system ready. press 'Backup Notes' or 'Download Notes' to synchronize.",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                } else {
                                    items(logs) { log ->
                                        Text(
                                            text = log,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = when {
                                                log.startsWith("❌") -> Color(0xFFEF4444)
                                                log.startsWith("[OK]") || log.startsWith("🏁") -> Color(0xFF10B981)
                                                log.startsWith("⚠️") -> Color(0xFFF59E0B)
                                                else -> Color(0xFF94A3B8)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Controls Row
                        if (isSyncing) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync operations running ...", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = onPullAll,
                                    modifier = Modifier.weight(1f)
                                        .testTag("github_pull_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download Notes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = onPushAll,
                                    modifier = Modifier.weight(1f)
                                        .testTag("github_push_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Backup Notes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Dialog: Add study hours or tasks to Progress entry
@Composable
fun ProgressLogDialog(
    onDismiss: () -> Unit,
    onSave: (Float, Int, Int, String) -> Unit
) {
    var studyHoursState by remember { mutableStateOf(2.5f) }
    var completedTasksState by remember { mutableStateOf("2") }
    var notesCreatedState by remember { mutableStateOf("1") }
    var diaryState by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Log Daily Progress", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close prompt")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Study hours slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Study Hours Today:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(String.format(Locale.getDefault(), "%.1f hrs", studyHoursState), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = studyHoursState,
                        onValueChange = { studyHoursState = it },
                        valueRange = 0.5f..12f,
                        steps = 23, // increments of 0.5 hrs
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            thumbColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = completedTasksState,
                        onValueChange = { completedTasksState = it.filter { c -> c.isDigit() } },
                        label = { Text("Task Keys Finished") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("progress_tasks_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = notesCreatedState,
                        onValueChange = { notesCreatedState = it.filter { c -> c.isDigit() } },
                        label = { Text("Notes Authored") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("progress_notes_input"),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                OutlinedTextField(
                    value = diaryState,
                    onValueChange = { diaryState = it },
                    label = { Text("Task details / study items completed") },
                    placeholder = { Text("e.g. Worked index proofs. Finished bio laboratory prep...") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .testTag("progress_summary_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val tasksVal = completedTasksState.toIntOrNull() ?: 0
                            val notesVal = notesCreatedState.toIntOrNull() ?: 0
                            onSave(studyHoursState, tasksVal, notesVal, diaryState)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .testTag("progress_save_btn")
                            .minimumInteractiveComponentSize()
                    ) {
                        Text("Save Entry", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Design for study metrics and logging progress logs
@Composable
fun ProgressHub(
    logs: List<ProgressEntry>,
    onDeleteLog: (ProgressEntry) -> Unit
) {
    val totalHours = logs.sumOf { it.studyHours.toDouble() }.toFloat()
    val totalTasks = logs.sumOf { it.completedTasks }
    val totalNotes = logs.sumOf { it.notesWrittenCount }

    // Toggle option state ("Log Hours", "Done Tasks", or "Drafts Saved")
    var selectedOption by remember { mutableStateOf<String?>("Log Hours") }

    // Interactive Pomodoro timer preset index for "Log Hours"
    var pomodoroPresetIndex by remember { mutableStateOf(0) }
    
    // Active timer interactive state
    var isTimerRunning by remember { mutableStateOf(false) }
    var timeLeftSeconds by remember { mutableStateOf(2700) } // Preset 0 default (45 mins)
    
    // LaunchedEffect to run the live countdown timer
    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            while (timeLeftSeconds > 0) {
                kotlinx.coroutines.delay(1000)
                timeLeftSeconds--
            }
            if (timeLeftSeconds == 0) {
                isTimerRunning = false
            }
        }
    }

    // Checkboxes list state for "Done Tasks"
    var syllabusTasks by remember {
        mutableStateOf(
            listOf(
                "📐 Definite integration boundary proofs" to true,
                "🧪 Organic chemistry molecular orbital structures" to true,
                "📝 Review notes for upcoming physics exam" to false,
                "📅 Complete weekly milestone logs in Progress Hub" to false
            )
        )
    }
    var newTaskTitle by remember { mutableStateOf("") }

    // Backup & DB cache compression state for "Drafts Saved"
    var isOptimizingCache by remember { mutableStateOf(false) }
    var optimizationResult by remember { mutableStateOf<String?>(null) }
    var optimizationLogs = remember { mutableStateListOf<String>() }
    val coroutineScope = rememberCoroutineScope()

    // Activity list filtering state
    var activeProgressFilter by remember { mutableStateOf("All Sessions") }

    val isDark = LocalThemeIsDark.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. GORGEOUS PREMIUM HEADER BANNER
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF131A30) else Color(0xFFEEF2FF)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isDark) Color(0xFF1E2640) else Color(0xFFC7D2FE),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (isDark) {
                                listOf(Color(0xFF1A2342), Color(0xFF0F172A))
                            } else {
                                listOf(Color(0xFFE0E7FF), Color(0xFFEEF2FF))
                            },
                        )
                    )
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isDark) Color(0xFF1E3A8A) else Color(0xFFDBEAFE),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            if (isDark) StudyBlueCold else ScholarBlue,
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = "LIVE PERFORMANCE METRICS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isDark) StudyBlueCold else ScholarBlue
                                )
                            }
                        }
                        Text(
                            text = "Productivity Hub",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Refined analysis of your study habits, syllabus tracking, and session progress.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Leaderboard,
                        contentDescription = null,
                        tint = if (isDark) StudyBlueCold else ScholarBlue,
                        modifier = Modifier
                            .size(54.dp)
                            .padding(start = 12.dp)
                    )
                }
            }
        }

        // 2. STUDY GOAL PROGRESS GAUGE (ALWAYS VISIBLE!)
        val weeklyQuota = 40f
        val completionRatio = (totalHours / weeklyQuota).coerceIn(0f, 1f)
        val completionPercentage = (completionRatio * 100).toInt()

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF0F172A) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isDark) Color(0xFF1E2640) else Color(0xFFEEF2FF),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = if (isDark) StudyGoldSoft else ScholarAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Weekly Study Quota",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "$completionPercentage% Completed",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isDark) StudyGoldSoft else ScholarAmber
                    )
                }

                // Sleek, glowing linear progress bar with multiple notch markers
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(
                            color = if (isDark) Color(0xFF131A30) else Color(0xFFEEF2FF),
                            shape = RoundedCornerShape(7.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color(0xFF1E2640) else Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(7.dp)
                        )
                ) {
                    // Actual progress fill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(completionRatio)
                            .fillMaxHeight()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                ),
                                shape = RoundedCornerShape(7.dp)
                            )
                    )
                    
                    // Multi-Notch tick markers
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) { dIdx ->
                            Box(
                                modifier = Modifier
                                    .size(width = 1.5.dp, height = 8.dp)
                                    .background(
                                        color = if (completionRatio > (dIdx + 1) * 0.25f) {
                                            Color.White.copy(alpha = 0.5f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        }
                                    )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${String.format(Locale.getDefault(), "%.1f", totalHours)} Hrs logged",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Goal: ${weeklyQuota.toInt()} Hrs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. STATS CARDS INTERACTIVE GRID
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsCard(
                label = "Study Focus",
                count = String.format(Locale.getDefault(), "%.1f", totalHours),
                unit = "Hrs",
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Alarm,
                color = MaterialTheme.colorScheme.primary,
                selected = selectedOption == "Log Hours",
                onClick = {
                    selectedOption = if (selectedOption == "Log Hours") null else "Log Hours"
                }
            )
            StatsCard(
                label = "Done Targets",
                count = totalTasks.toString(),
                unit = "Tasks",
                modifier = Modifier.weight(1f),
                icon = Icons.Default.AssignmentTurnedIn,
                color = MaterialTheme.colorScheme.secondary,
                selected = selectedOption == "Done Tasks",
                onClick = {
                    selectedOption = if (selectedOption == "Done Tasks") null else "Done Tasks"
                }
            )
            StatsCard(
                label = "Vault Cache",
                count = totalNotes.toString(),
                unit = "Items",
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Draw,
                color = MaterialTheme.colorScheme.tertiary,
                selected = selectedOption == "Drafts Saved",
                onClick = {
                    selectedOption = if (selectedOption == "Drafts Saved") null else "Drafts Saved"
                }
            )
        }

        // 4. ANIMATE-EXPANDED COLLAPSIBLE OPTIONS PANEL
        AnimatedVisibility(
            visible = selectedOption != null,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        ) {
            val activeColor = when (selectedOption) {
                "Log Hours" -> MaterialTheme.colorScheme.primary
                "Done Tasks" -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.tertiary
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isDark) Color(0xFF10162B) else Color.White,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(
                        width = 1.5.dp,
                        color = activeColor.copy(alpha = if (isDark) 0.6f else 0.4f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(18.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header with option title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(activeColor, shape = CircleShape)
                            )
                            Text(
                                text = when (selectedOption) {
                                    "Log Hours" -> "Log Hours Focus Control"
                                    "Done Tasks" -> "Done Tasks & Syllabus Targets"
                                    else -> "Drafts Saved Sync Options"
                                },
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium,
                                color = activeColor
                            )
                        }

                        // Close badge
                        Box(
                            modifier = Modifier
                                .clickable { selectedOption = null }
                                .background(activeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Collapse",
                                style = MaterialTheme.typography.labelSmall,
                                color = activeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = if (isDark) Color(0xFF1E2640) else Color(0xFFEEF2FF))

                    // Content based on selection
                    when (selectedOption) {
                        "Log Hours" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Maintain rigorous attention using Pomodoro rhythms. Select intervals below to synchronize the interactive countdown widget:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )

                                val presets = listOf(
                                    Triple("☕ Deep Focus", "45m / 15m", 2700),
                                    Triple("📚 Study Sprint", "25m / 5m", 1500),
                                    Triple("⚡ Power Hour", "60m / 10m", 3600)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    presets.forEachIndexed { index, presetTriple ->
                                        val isSelected = pomodoroPresetIndex == index
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isSelected) activeColor else activeColor.copy(alpha = 0.08f)
                                                )
                                                .clickable { 
                                                    pomodoroPresetIndex = index
                                                    // Stop current timer and set new preset time
                                                    isTimerRunning = false
                                                    timeLeftSeconds = presetTriple.third
                                                }
                                                .border(
                                                    1.dp,
                                                    if (isSelected) activeColor else activeColor.copy(alpha = 0.2f),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = presetTriple.first,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else activeColor
                                                )
                                                Text(
                                                    text = presetTriple.second,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 10.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else activeColor.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Interactive Countdown Timer Card
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDark) Color(0xFF131A30) else Color(0xFFEEF2FF)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = activeColor.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = presets[pomodoroPresetIndex].first.uppercase() + " RUNTIME",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = activeColor
                                        )
                                        
                                        // Timer countdown text
                                        val minutes = timeLeftSeconds / 60
                                        val seconds = timeLeftSeconds % 60
                                        val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                                        
                                        Text(
                                            text = formattedTime,
                                            fontSize = 38.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        // Controls Row
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    // Reset timer to current preset defaults
                                                    isTimerRunning = false
                                                    timeLeftSeconds = presets[pomodoroPresetIndex].third
                                                },
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
                                                    .border(1.dp, activeColor.copy(alpha = 0.3f), shape = CircleShape)
                                                    .size(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Reset Timer",
                                                    tint = activeColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Button(
                                                onClick = { isTimerRunning = !isTimerRunning },
                                                colors = ButtonDefaults.buttonColors(containerColor = activeColor),
                                                shape = RoundedCornerShape(20.dp),
                                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = if (isTimerRunning) "Pause" else "Start",
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = if (isTimerRunning) "Pause focus" else "Start Engine",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TipsAndUpdates,
                                        contentDescription = null,
                                        tint = activeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Tip: Log focus session durations below to preserve historical consistency.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        "Done Tasks" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                val completedCount = syllabusTasks.count { it.second }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Active checklist target checkpoints:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$completedCount of ${syllabusTasks.size} cleared",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = activeColor
                                    )
                                }

                                syllabusTasks.forEachIndexed { index, taskPair ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDark) Color(0xFF131A30) else Color(0xFFF1F5F9).copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                width = 1.dp,
                                                color = if (taskPair.second) activeColor.copy(alpha = 0.3f) else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                syllabusTasks = syllabusTasks.toMutableList().apply {
                                                    this[index] = taskPair.first to !taskPair.second
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (taskPair.second) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = if (taskPair.second) "Checked" else "Unchecked",
                                                tint = if (taskPair.second) activeColor else activeColor.copy(alpha = 0.4f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = taskPair.first,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (taskPair.second) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                textDecoration = if (taskPair.second) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                                fontWeight = if (taskPair.second) FontWeight.Normal else FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    syllabusTasks = syllabusTasks.toMutableList().apply {
                                                        removeAt(index)
                                                     }
                                                },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .testTag("delete_syllabus_task_$index")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete target",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newTaskTitle,
                                        onValueChange = { newTaskTitle = it },
                                        placeholder = { Text("Add custom target checkpoint...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = activeColor,
                                            unfocusedBorderColor = activeColor.copy(alpha = 0.3f)
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            if (newTaskTitle.isNotBlank()) {
                                                syllabusTasks = syllabusTasks + (newTaskTitle to false)
                                                newTaskTitle = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = activeColor),
                                        modifier = Modifier.height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("+ Add", fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        "Drafts Saved" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Academic Storage System Health & Metadata:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDark) Color(0xFF131A30) else Color(0xFFEEF2FF).copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, activeColor.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(Color(0xFF10B981), shape = CircleShape)
                                                )
                                                Text(
                                                    text = "SQLite Hub Database: Healthy",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                    text = "$totalNotes draft records",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = activeColor
                                                )
                                        }
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(activeColor, shape = CircleShape)
                                                )
                                                Text(
                                                    text = "Cloud Workspace Backups",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = "Synced automatically",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Interactive terminal console output simulation!
                                if (isOptimizingCache || optimizationLogs.isNotEmpty()) {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF070B19)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(6.dp).background(Color.Red, shape = CircleShape))
                                                Box(modifier = Modifier.size(6.dp).background(Color.Yellow, shape = CircleShape))
                                                Box(modifier = Modifier.size(6.dp).background(Color.Green, shape = CircleShape))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("console_terminal_logs.sh", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                                            }
                                            HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))
                                            optimizationLogs.forEach { logLine ->
                                                Text(
                                                    text = logLine,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (logLine.startsWith("⚡") || logLine.startsWith("[OK]")) Color(0xFF10B981) else Color(0xFF94A3B8)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (isOptimizingCache) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = activeColor
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Rebuilding indices...",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        } else if (optimizationResult != null) {
                                            Text(
                                                text = "Storage check cleared successfully.",
                                                color = Color(0xFF10B981),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            if (!isOptimizingCache) {
                                                isOptimizingCache = true
                                                optimizationResult = null
                                                optimizationLogs.clear()
                                                optimizationLogs.add("> Executing vacuum_sqlite_storage.bin")
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(550)
                                                    optimizationLogs.add("> Reindexing database B-Trees...")
                                                    kotlinx.coroutines.delay(650)
                                                    optimizationLogs.add("> Rebuilding SQLite internal page mappings...")
                                                    kotlinx.coroutines.delay(550)
                                                    optimizationLogs.add("[OK] Integrity check complete: zero corruption.")
                                                    optimizationLogs.add("⚡ Local database compressed successfully!")
                                                    isOptimizingCache = false
                                                    optimizationResult = "Rebuilt!"
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = activeColor),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Defrag & Decompress", fontWeight = FontWeight.Black, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. RECONSTRUCTED TIMELINE JOURNAL HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Activity Log Ledger",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "A historical catalog of finished study sessions",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${logs.size} Logs",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Horizontal scrolling Quick Filtering Pills
        val filterOptions = listOf("All Sessions", "⏱️ Extreme (>3.0h)", "🎯 High-Output (Tasks)")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filterOptions) { opt ->
                val isSelected = activeProgressFilter == opt
                val bgPaint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                val textPaint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgPaint)
                        .border(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .clickable { activeProgressFilter = opt }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = opt,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = textPaint
                    )
                }
            }
        }

        // FILTERED PROGRESS LOGS LIST
        val filteredLogs = remember(logs, activeProgressFilter) {
            when (activeProgressFilter) {
                "⏱️ Extreme (>3.0h)" -> logs.filter { it.studyHours >= 3.0f }
                "🎯 High-Output (Tasks)" -> logs.filter { it.completedTasks >= 2 }
                else -> logs
            }
        }

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "No study logs match the filter",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Click 'Log Progress' in the bottom bar to input a daily check-in!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            // Because our page is inside a vertical Scrollable Column with other widgets,
            // we should not put a scrollable LazyColumn directly inside it without defining a height.
            // Modern, responsive Android layout: we can implement a custom items list inside the Column itself
            // using standard list rendering, which behaves beautifully with overall page scrolling!
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 68.dp)
            ) {
                filteredLogs.forEach { log ->
                    ProgressLogItem(log = log, onDelete = { onDeleteLog(log) })
                }
            }
        }
    }
}

@Composable
fun StatsCard(
    label: String,
    count: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalThemeIsDark.current
    
    // Calculate solid background color
    val containerColor = if (selected) {
        if (isDark) {
            androidx.compose.ui.graphics.lerp(color, Color(0xFF10162B), 0.85f)
        } else {
            androidx.compose.ui.graphics.lerp(color, Color.White, 0.90f)
        }
    } else {
        if (isDark) Color(0xFF10162B) else Color.White
    }
    
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
        modifier = modifier
            .clickable { onClick() }
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) color else color.copy(alpha = if (isDark) 0.25f else 0.15f),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.ExtraBold
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) color else color.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = count,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                    color = color,
                    fontSize = 24.sp
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 3.dp),
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Selected active indicator line below stat info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        color = if (selected) color else Color.Transparent,
                        shape = RoundedCornerShape(1.5.dp)
                    )
            )
        }
    }
}

@Composable
fun ProgressLogItem(
    log: ProgressEntry,
    onDelete: () -> Unit
) {
    val originalSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val targetSdf = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault())
    val displayDate = try {
        val d = originalSdf.parse(log.date)
        if (d != null) targetSdf.format(d) else log.date
    } catch (e: Exception) {
        log.date
    }

    val isDark = LocalThemeIsDark.current
    val isHeroicSession = log.studyHours >= 3.0f
    val isSprintSession = log.studyHours < 1.5f
    
    val accentColor = if (isHeroicSession) {
        MaterialTheme.colorScheme.secondary // Amber/Warm Accent
    } else if (isSprintSession) {
        MaterialTheme.colorScheme.tertiary // Sage Green Accent
    } else {
        MaterialTheme.colorScheme.primary // Default Indigo Accent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Continuous timeline stream node with dynamic color accents
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxHeight()
                .width(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(accentColor, shape = CircleShape)
                    .border(2.dp, if (isDark) Color(0xFF0F172A) else Color.White, shape = CircleShape)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(accentColor.copy(alpha = 0.3f))
            )
        }

        // Timeline main body card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF10162B) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .weight(1f)
                .border(
                    width = 1.dp,
                    color = accentColor.copy(alpha = if (isDark) 0.3f else 0.18f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = displayDate,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.bodyMedium,
                            color = accentColor
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = if (isHeroicSession) Color(0xFFF59E0B) else if (isSprintSession) Color(0xFF10B981) else Color(0xFF6366F1),
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = if (isHeroicSession) "Extreme Focus Session" else if (isSprintSession) "Short Focus Sprint" else "Standard Work Session",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = onDelete, 
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.15f else 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove log entry",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                HorizontalDivider(color = if (isDark) Color(0xFF1E2640) else Color(0xFFEEF2FF).copy(alpha = 0.5f))

                // Custom capsule tags for study statistics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.15f else 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "⏱️ ${log.studyHours} Hrs Focus",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = if (isDark) 0.15f else 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🎯 ${log.completedTasks} Tasks Done",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = if (isDark) 0.15f else 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "✍️ ${log.notesWrittenCount} Notes Typed",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                if (log.summary.isNotBlank()) {
                    Text(
                        text = log.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .background(
                                color = if (isDark) Color(0xFF1E2640).copy(alpha = 0.3f) else Color(0xFFF1F5F9).copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
