package com.touchgrass.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

data class InstalledApp(val packageName: String, val label: String)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SetupScreen()
            }
        }
    }

    @Composable
    private fun SetupScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var accessibilityGranted by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
        var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
        var cameraGranted by remember {
            mutableStateOf(
                context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            )
        }

        val allApps = remember { loadLaunchableApps(context) }
        var selectedApps by remember { mutableStateOf(AppStateManager.getBlockedApps(context)) }
        var activated by remember { mutableStateOf(AppStateManager.isActivated(context)) }
        var missionStatus by remember { mutableStateOf("") }

        // Re-check permissions whenever the user comes back from the Settings screen.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    accessibilityGranted = isAccessibilityServiceEnabled(context)
                    overlayGranted = Settings.canDrawOverlays(context)
                    cameraGranted = context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text("Touch the Grass", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Set up permissions, pick apps to block, then activate.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            PermissionRow(label = "Accessibility service", granted = accessibilityGranted) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            PermissionRow(label = "Display over other apps", granted = overlayGranted) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
            PermissionRow(label = "Camera", granted = cameraGranted) {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Apps to block (${selectedApps.size} selected)",
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(allApps, key = { it.packageName }) { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = selectedApps.contains(app.packageName),
                            onCheckedChange = { checked ->
                                selectedApps = if (checked) {
                                    selectedApps + app.packageName
                                } else {
                                    selectedApps - app.packageName
                                }
                                AppStateManager.setBlockedApps(context, selectedApps)
                            }
                        )
                        Text(app.label, modifier = Modifier.weight(1f))
                        if (selectedApps.contains(app.packageName)) {
                            var minutesText by remember(app.packageName) {
                                mutableStateOf(
                                    AppStateManager.getTimeLimitMinutes(context, app.packageName).toString()
                                )
                            }
                            OutlinedTextField(
                                value = minutesText,
                                onValueChange = { text ->
                                    minutesText = text
                                    text.toIntOrNull()?.takeIf { it > 0 }?.let {
                                        AppStateManager.setTimeLimitMinutes(context, app.packageName, it)
                                    }
                                },
                                label = { Text("min") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !activated,
                                modifier = Modifier.width(90.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            val readyToActivate = accessibilityGranted && overlayGranted && cameraGranted &&
                selectedApps.isNotEmpty()

            Button(
                enabled = readyToActivate && !activated,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        missionStatus = "Asking Gemini for today's mission..."
                        val result = GeminiRepository.generateMission()
                        val mission = result.getOrElse { "Go outside and take a photo of the sky." }
                        AppStateManager.setMission(context, mission)
                        AppStateManager.setLastResetDate(context, todayString())
                        AppStateManager.resetAllUsage(context)
                        AppStateManager.setActivated(context, true)
                        AlarmScheduler.scheduleMidnightAlarm(context)
                        activated = true
                        missionStatus = "Activated! Mission: $mission"
                    }
                }
            ) {
                Text(if (activated) "Activated" else "Activate")
            }

            if (activated) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        AppStateManager.setActivated(context, false)
                        activated = false
                        missionStatus = "Deactivated."
                    }
                ) {
                    Text("Deactivate")
                }
            }

            if (missionStatus.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(missionStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(label, modifier = Modifier.weight(1f))
            if (granted) {
                Text("Granted", style = MaterialTheme.typography.labelMedium)
            } else {
                Button(onClick = onClick) { Text("Grant") }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, TouchGrassAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val componentName = ComponentName.unflattenFromString(splitter.next())
            if (componentName != null && componentName == expectedComponent) return true
        }
        return false
    }

    private fun loadLaunchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { InstalledApp(it.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }
}
