package com.touchgrass.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppCategory(val label: String) {
    ALL("All"),
    SELECTED("Selected"),
    GAMES("Games"),
    SOCIAL("Social"),
    TOOLS("Tools"),
    OTHERS("Others")
}

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val category: AppCategory
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF6750A4),
                    background = Color(0xFFFBF8FF)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SetupScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
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

        val allPermissionsGranted = accessibilityGranted && overlayGranted && cameraGranted

        var permissionsExpanded by remember { mutableStateOf(!allPermissionsGranted) }
        LaunchedEffect(allPermissionsGranted) {
            permissionsExpanded = !allPermissionsGranted
        }

        val allApps = remember { loadLaunchableApps(context) }
        var selectedApps by remember { mutableStateOf(context.appState.getBlockedApps()) }
        var activated by remember { mutableStateOf(context.appState.isActivated()) }
        var BlockingStatus by remember { mutableStateOf("") }

        var searchQuery by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf(AppCategory.ALL) }

        val filteredApps = remember(searchQuery, selectedCategory, allApps, selectedApps) {
            allApps.filter { app ->
                val matchesSearch = app.label.contains(searchQuery, ignoreCase = true)
                val matchesCategory = when (selectedCategory) {
                    AppCategory.ALL -> true
                    AppCategory.SELECTED -> selectedApps.contains(app.packageName)
                    else -> app.category == selectedCategory
                }
                matchesSearch && matchesCategory
            }
        }

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
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(text = "🌿 ", fontSize = 28.sp)
                Text(
                    text = "Touch Grass",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = Color(0xFF1C1B1F)
                    )
                )
            }

            Text(
                text = "Set up permissions, pick apps to block, then activate.",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF49454F)),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECE6F0)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = allPermissionsGranted) {
                                permissionsExpanded = !permissionsExpanded
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (allPermissionsGranted) Color(0xFF2E7D32) else Color(0xFF79747E),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (allPermissionsGranted) "All permissions granted" else "Permissions required",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF1C1B1F),
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (allPermissionsGranted) {
                            Text(
                                text = if (permissionsExpanded) "▲" else "▼",
                                color = Color(0xFF49454F)
                            )
                        }
                    }

                    if (permissionsExpanded) {
                        Spacer(Modifier.height(10.dp))

                        PermissionRow(
                            label = "Accessibility Service",
                            granted = accessibilityGranted
                        ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

                        Spacer(Modifier.height(10.dp))

                        PermissionRow(
                            label = "Display over other apps",
                            granted = overlayGranted
                        ) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        PermissionRow(
                            label = "Camera",
                            granted = cameraGranted
                        ) { cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA) }
                    }
                }
            }

            Text(
                text = "Apps to block (${selectedApps.size} selected)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1C1B1F)
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps...", color = Color(0xFF49454F)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF49454F)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF7F2FA),
                    unfocusedContainerColor = Color(0xFFF7F2FA),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                items(AppCategory.values()) { category ->
                    val isSelected = selectedCategory == category
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFF6750A4) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF6750A4) else Color(0xFF79747E),
                                shape = CircleShape
                            )
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = category.label,
                            color = if (isSelected) Color.White else Color(0xFF1D1B20),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isSelected = selectedApps.contains(app.packageName)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            enabled = !activated,
                            onCheckedChange = { checked ->
                                selectedApps = if (checked) {
                                    selectedApps + app.packageName
                                } else {
                                    selectedApps - app.packageName
                                }
                                context.appState.setBlockedApps(selectedApps)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF6750A4)
                            )
                        )

                        Image(
                            painter = rememberDrawablePainter(drawable = app.icon),
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        Spacer(Modifier.width(12.dp))

                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1C1B1F)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "(${app.category.label})",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF49454F)),
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        if (isSelected) {
                            var minutesText by remember(app.packageName) {
                                mutableStateOf(
                                    context.appState.getTimeLimitMinutes(app.packageName).toString()
                                )
                            }
                            OutlinedTextField(
                                value = minutesText,
                                onValueChange = { text ->
                                    minutesText = text
                                    text.toIntOrNull()?.takeIf { it >= 0 }?.let {
                                        context.appState.setTimeLimitMinutes(app.packageName, it)
                                    }
                                },
                                label = { Text("min", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !activated,
                                singleLine = true,
                                modifier = Modifier.width(64.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFFF7F2FA),
                                    unfocusedContainerColor = Color(0xFFF7F2FA)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            val readyToActivate = allPermissionsGranted && selectedApps.isNotEmpty()

            Button(
                enabled = readyToActivate && !activated,
                onClick = {
                    scope.launch {
                        BlockingStatus = "Requesting today's mission from Gemini..."
                        val result = context.gemini.generateMission()
                        val mission = result.getOrElse { "Take a photo of the sky above you." }
                        context.appState.setMission(mission)
                        context.appState.setLastResetDate(todayString())
                        context.appState.resetAllUsage()
                        context.appState.setActivated(true)
                        AlarmScheduler.scheduleMidnightAlarm(context)
                        activated = true
                        BlockingStatus = "Blocking Activated!"
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6750A4),
                    disabledContainerColor = Color(0xFFE6E0E9)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "ACTIVATE BLOCKING",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    context.appState.setActivated(false)
                    activated = false
                    BlockingStatus = ""
                },
                enabled = activated,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "DEACTIVATE CURRENT BLOCK",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (activated) Color(0xFF1C1B1F) else Color(0xFF9E9E9E)
                )
            }

            if (BlockingStatus.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECE6F0)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "🖼️ ",
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = BlockingStatus,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF1C1B1F),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PermissionRow(
        label: String,
        granted: Boolean,
        onClick: () -> Unit
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (!granted) onClick() }
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = Color(0xFF49454F),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF1C1B1F),
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (granted) Color(0xFF2E7D32) else Color(0xFF79747E),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (granted) "Granted" else "Not Granted",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (granted) Color(0xFF2E7D32) else Color(0xFF79747E),
                    fontWeight = FontWeight.Medium
                )
            )
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

    private fun todayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun loadLaunchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            // A package can be uninstalled or replaced between the query above
            // and these per-app lookups, and any of the three throws once it
            // is gone. Drop that one app rather than lose the whole picker.
            .mapNotNull { info ->
                runCatching {
                    val appInfo = pm.getApplicationInfo(info.packageName, 0)
                    val category = when (appInfo.category) {
                        ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
                        ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> AppCategory.SOCIAL
                        ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_NEWS -> AppCategory.TOOLS
                        else -> AppCategory.OTHERS
                    }
                    InstalledApp(
                        packageName = info.packageName,
                        label = info.loadLabel(pm).toString(),
                        icon = info.loadIcon(pm),
                        category = category
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }
}