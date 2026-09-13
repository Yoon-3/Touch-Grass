package com.touchgrass.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File

/**
 * Shown on top of any blocked app while the daily mission is incomplete.
 * Back button is disabled (moves task to home instead) so the user can't
 * peek at the blocked app behind it.
 */
class BlockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "blocked_package_name"
    }

    private var photoUri: Uri? = null

    // singleTask means the activity instance (and its Compose composition) is
    // reused across repeated blocks instead of recreated, so a plain
    // LaunchedEffect(Unit) only ever runs once. Bump this on onNewIntent to
    // force a fresh mission fetch each time the overlay is shown again.
    private val refreshTrigger = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) {
            moveTaskToBack(true)
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF6750A4),
                    background = Color(0xFFFBF8FF)
                )
            ) {
                OverlayScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshTrigger.value++
    }

    @Composable
    private fun OverlayScreen() {
        val scope = rememberCoroutineScope()
        val trigger by refreshTrigger
        var mission by remember { mutableStateOf(AppStateManager.getMission(this)) }
        val seenMissions = remember { mutableStateListOf<String>().apply { if (mission.isNotBlank()) add(mission) } }
        var status by remember { mutableStateOf("") }
        var isChecking by remember { mutableStateOf(false) }
        var isRerolling by remember { mutableStateOf(false) }

        // Gemini's avoid-list instruction isn't always followed once the pool of
        // easy indoor objects narrows, so retry client-side on an exact repeat.
        suspend fun fetchNewMission() {
            var result: Result<String>? = null
            repeat(8) {
                val attempt = GeminiRepository.generateMission(seenMissions)
                result = attempt
                val newMission = attempt.getOrNull() ?: return@repeat
                if (seenMissions.none { it.equals(newMission, ignoreCase = true) }) {
                    mission = newMission
                    seenMissions.add(newMission)
                    AppStateManager.setMission(this@BlockOverlayActivity, newMission)
                    return
                }
            }
            result?.onSuccess { newMission ->
                mission = newMission
                seenMissions.add(newMission)
                AppStateManager.setMission(this@BlockOverlayActivity, newMission)
            }
        }

        // Fetch a fresh mission from Gemini every time the block screen shows,
        // instead of reusing the same one until the next midnight reset.
        LaunchedEffect(trigger) {
            status = ""
            fetchNewMission()
        }

        val takePictureLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            val uri = photoUri
            if (success && uri != null) {
                isChecking = true
                status = "Checking your photo..."
                scope.launch {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) {
                        status = "Couldn't read the photo. Try again."
                        isChecking = false
                        return@launch
                    }
                    val result = GeminiRepository.verifyPhoto(mission, bytes)
                    isChecking = false
                    result.onSuccess { approved ->
                        if (approved) {
                            val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                            if (packageName != null) {
                                AppStateManager.resetUsage(this@BlockOverlayActivity, packageName)
                            }
                            status = "Approved! Unlocking..."
                            finish()
                        } else {
                            status = "Gemini says that doesn't match the mission. Try again!"
                        }
                    }.onFailure {
                        status = "Couldn't reach Gemini. Check your connection and try again."
                    }
                }
            } else {
                status = "No photo taken."
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Touch Grass",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = Color(0xFF1C1B1F)
                )
            )
            Spacer(Modifier.height(16.dp))
            Text("Today's mission:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                mission.ifBlank { "Take a photo of a bench." },
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
            TextButton(
                enabled = !isChecking && !isRerolling,
                onClick = {
                    isRerolling = true
                    status = ""
                    scope.launch {
                        fetchNewMission()
                        isRerolling = false
                    }
                }
            ) {
                Text(if (isRerolling) "Rerolling..." else "Can't do this? Reroll mission")
            }
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = !isChecking && !isRerolling,
                onClick = {
                    val uri = createImageUri()
                    photoUri = uri
                    takePictureLauncher.launch(uri)
                }
            ) {
                Text(if (isChecking) "Checking..." else "Take Photo")
            }
            Spacer(Modifier.height(16.dp))
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    private fun createImageUri(): Uri {
        val imagesDir = File(cacheDir, "mission_photos").apply { mkdirs() }
        val file = File(imagesDir, "mission_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }
}
