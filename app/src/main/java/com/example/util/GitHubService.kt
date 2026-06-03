package com.example.util

import android.content.Context
import android.util.Base64
import com.example.data.Note
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class GitHubConfig(
    val token: String,
    val repo: String,
    val branch: String,
    val folderPath: String
) {
    val isValid: Boolean get() = token.isNotBlank() && repo.isNotBlank()
}

object GitHubService {
    private const val PREFS_NAME = "github_sync_prefs"
    private const val KEY_TOKEN = "github_token"
    private const val KEY_REPO = "github_repo"
    private const val KEY_BRANCH = "github_branch"
    private const val KEY_FOLDER_PATH = "github_folder_path"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Save configuration details to encrypted/local shared preferences
     */
    fun saveConfig(context: Context, token: String, repo: String, branch: String, folderPath: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_REPO, repo.trim().replace("https://github.com/", ""))
            .putString(KEY_BRANCH, if (branch.isBlank()) "main" else branch.trim())
            .putString(KEY_FOLDER_PATH, folderPath.trim().removePrefix("/").removeSuffix("/"))
            .apply()
    }

    /**
     * Read configuration details from shared preferences
     */
    fun getConfig(context: Context): GitHubConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return GitHubConfig(
            token = prefs.getString(KEY_TOKEN, "") ?: "",
            repo = prefs.getString(KEY_REPO, "") ?: "",
            branch = prefs.getString(KEY_BRANCH, "main") ?: "main",
            folderPath = prefs.getString(KEY_FOLDER_PATH, "NoteHub") ?: "NoteHub"
        )
    }

    /**
     * Clears local saved GitHub credentials
     */
    fun clearConfig(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /**
     * Call github API to verify the token and get user name
     */
    fun verifyCredentials(token: String): String? {
        if (token.isBlank()) return null
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .addHeader("Authorization", "token $token")
            .addHeader("Accept", "application/vnd.github.v3+json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    json.optString("login", "Authenticated User")
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get file SHA if it exists in the repository
     */
    private fun getFileSha(config: GitHubConfig, pathInRepo: String): String? {
        val url = "https://api.github.com/repos/${config.repo}/contents/$pathInRepo?ref=${config.branch}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "token ${config.token}")
            .addHeader("Accept", "application/vnd.github.v3+json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    json.optString("sha", null)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Helper to make clean file-names safe for URL paths
     */
    private fun getRepoSafePath(config: GitHubConfig, note: Note): String {
        val categorySafe = note.category.trim().replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val titleSafe = note.title.trim().replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val fileName = "${titleSafe}.md"
        val folder = config.folderPath
        
        return when {
            folder.isBlank() && categorySafe.isBlank() -> fileName
            folder.isBlank() -> "$categorySafe/$fileName"
            categorySafe.isBlank() -> "$folder/$fileName"
            else -> "$folder/$categorySafe/$fileName"
        }
    }

    /**
     * Push a single note as markdown file to the Repository
     */
    fun pushNoteToRepo(context: Context, note: Note, onLog: (String) -> Unit = {}): Boolean {
        val config = getConfig(context)
        if (!config.isValid) {
            onLog("❌ Error: GitHub connection is unconfigured or invalid.")
            return false
        }

        val pathInRepo = getRepoSafePath(config, note)
        onLog("> Resolving state of '$pathInRepo'...")
        
        val existingSha = getFileSha(config, pathInRepo)
        if (existingSha != null) {
            onLog("> Match found details on branch [${config.branch}]. Preparing update payload...")
        } else {
            onLog("> No previous record. Preparing creation payload...")
        }

        val base64Content = Base64.encodeToString(
            note.content.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )

        val requestBodyJson = JSONObject().apply {
            put("message", "Sync note from NoteHub: ${note.title}")
            put("content", base64Content)
            put("branch", config.branch)
            if (existingSha != null) {
                put("sha", existingSha)
            }
        }

        val url = "https://api.github.com/repos/${config.repo}/contents/$pathInRepo"
        val request = Request.Builder()
            .url(url)
            .put(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .addHeader("Authorization", "token ${config.token}")
            .addHeader("Accept", "application/vnd.github.v3+json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    onLog("[OK] Pushed \"${note.title}\" successfully.")
                    true
                } else {
                    val errBody = response.body?.string() ?: ""
                    onLog("❌ Code [${response.code}]: Failed to push \"${note.title}\". Response: $errBody")
                    false
                }
            }
        } catch (e: IOException) {
            onLog("❌ Network Exception: ${e.message}")
            false
        }
    }

    /**
     * Download notes list and file details recursively or level path-based
     */
    fun pullNotesFromRepo(context: Context, onLog: (String) -> Unit = {}): List<Note> {
        val config = getConfig(context)
        val extractedNotes = mutableListOf<Note>()
        if (!config.isValid) {
            onLog("❌ Error: GitHub connection is unconfigured or invalid.")
            return emptyList()
        }

        onLog("> Connecting to repository '${config.repo}'...")
        val url = if (config.folderPath.isBlank()) {
            "https://api.github.com/repos/${config.repo}/contents?ref=${config.branch}"
        } else {
            "https://api.github.com/repos/${config.repo}/contents/${config.folderPath}?ref=${config.branch}"
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "token ${config.token}")
            .addHeader("Accept", "application/vnd.github.v3+json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onLog("❌ Pull directory command failed. Code: ${response.code}")
                    return emptyList()
                }

                val body = response.body?.string() ?: ""
                val rootItems = JSONArray(body)
                val filesToFetch = mutableListOf<Triple<String, String, String>>() // Path, Name, Category

                onLog("> Listing contents under folder '${config.folderPath.ifBlank { "root" }}'...")
                
                // Add first level md files
                for (i in 0 until rootItems.length()) {
                    val item = rootItems.getJSONObject(i)
                    val type = item.optString("type")
                    val pathItem = item.optString("path")
                    val nameItem = item.optString("name")
                    
                    if (type == "file" && nameItem.endsWith(".md", ignoreCase = true)) {
                        filesToFetch.add(Triple(pathItem, nameItem, ""))
                    } else if (type == "dir") {
                        // Let's check subfolders one level deep to extract category mappings!
                        val categoryName = nameItem
                        onLog("> Parsing category subdirectory '$categoryName'...")
                        val subUrl = "https://api.github.com/repos/${config.repo}/contents/$pathItem?ref=${config.branch}"
                        val subRequest = Request.Builder()
                            .url(subUrl)
                            .addHeader("Authorization", "token ${config.token}")
                            .addHeader("Accept", "application/vnd.github.v3+json")
                            .build()
                        
                        try {
                            client.newCall(subRequest).execute().use { subResp ->
                                if (subResp.isSuccessful) {
                                    val subItems = JSONArray(subResp.body?.string() ?: "")
                                    for (j in 0 until subItems.length()) {
                                        val sEl = subItems.getJSONObject(j)
                                        val sType = sEl.optString("type")
                                        val sPath = sEl.optString("path")
                                        val sName = sEl.optString("name")
                                        if (sType == "file" && sName.endsWith(".md", ignoreCase = true)) {
                                            filesToFetch.add(Triple(sPath, sName, categoryName))
                                        }
                                    }
                                }
                            }
                        } catch (subE: Exception) {
                            onLog("> Log: Skipped subfolder '$categoryName' parsing: ${subE.message}")
                        }
                    }
                }

                onLog("> Downloading [${filesToFetch.size}] target Markdown records...")
                
                for ((fPath, fName, category) in filesToFetch) {
                    val detailUrl = "https://api.github.com/repos/${config.repo}/contents/$fPath?ref=${config.branch}"
                    val detailRequest = Request.Builder()
                        .url(detailUrl)
                        .addHeader("Authorization", "token ${config.token}")
                        .addHeader("Accept", "application/vnd.github.v3+json")
                        .build()

                    try {
                        client.newCall(detailRequest).execute().use { detailResp ->
                            if (detailResp.isSuccessful) {
                                val detailObj = JSONObject(detailResp.body?.string() ?: "")
                                val rawB64 = detailObj.optString("content", "").replace("\n", "").replace("\r", "")
                                if (rawB64.isNotBlank()) {
                                    val decodedBytes = Base64.decode(rawB64, Base64.DEFAULT)
                                    val contentText = String(decodedBytes, StandardCharsets.UTF_8)
                                    
                                    // Parse neat human-readable titles, from first heading if present, or filename minus .md
                                    val title = parseMarkdownTitle(contentText).ifBlank {
                                        fName.removeSuffix(".md").replace("_", " ")
                                    }
                                    
                                    val fetchedNote = Note(
                                        title = title,
                                        content = contentText,
                                        category = category.replace("_", " ").ifBlank { "General" },
                                        isSynced = true,
                                        synchedAt = System.currentTimeMillis()
                                    )
                                    extractedNotes.add(fetchedNote)
                                    onLog("[OK] Downloaded content for \"$title\"")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        onLog("⚠️ Error downloading file '$fName': ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            onLog("❌ Directory Pull Failed: ${e.message}")
        }

        return extractedNotes
    }

    /**
     * Extracts title from first markdown header line `# Title`
     */
    private fun parseMarkdownTitle(content: String): String {
        try {
            val lines = content.lines()
            for (line in lines) {
                if (line.startsWith("# ")) {
                    return line.removePrefix("# ").trim()
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return ""
    }
}
