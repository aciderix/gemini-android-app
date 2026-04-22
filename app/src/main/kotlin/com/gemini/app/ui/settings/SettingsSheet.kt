package com.gemini.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.gemini.app.ui.chat.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeChange: (ThemeMode) -> Unit = {}
) {
    val context = LocalContext.current
    val currentModel by viewModel.model.collectAsState()
    val autoApprove by viewModel.autoApprove.collectAsState()
    val workspaceLabel by viewModel.workspaceLabel.collectAsState()
    val workspacePath by viewModel.workspacePath.collectAsState()
    val workspaceReason by viewModel.workspaceReason.collectAsState()
    val models by viewModel.availableModels.collectAsState()
    val autoCompress by viewModel.autoCompressEnabled.collectAsState()
    val compressThreshold by viewModel.autoCompressThreshold.collectAsState()
    val tokenUsage by viewModel.tokenUsage.collectAsState()
    val autoSave by viewModel.autoSaveEnabled.collectAsState()
    val imagenModel by viewModel.imagenModel.collectAsState()

    var customModel by remember { mutableStateOf("") }
    var customImagenModel by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(emptySet<String>()) }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.setProjectFolder(uri.toString())
    }

    fun toggle(name: String) {
        expanded = if (name in expanded) expanded - name else expanded + name
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun dismissAnimated() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { dismissAnimated() }) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(12.dp))

            SettingsAccordion(
                title = "Account",
                icon = Icons.Default.AccountCircle,
                expanded = "Account" in expanded,
                onToggle = { toggle("Account") }
            ) {
                Text(
                    "API key from Google AI Studio. Sign in with your Google " +
                        "account there to create one, then paste it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openUrl(context, "https://aistudio.google.com/app/apikey") }) {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Get API key")
                    }
                    OutlinedButton(onClick = {
                        viewModel.signOut()
                        onDismiss()
                    }) { Text("Sign out") }
                }
            }

            SettingsAccordion(
                title = "Model",
                icon = Icons.Default.Memory,
                expanded = "Model" in expanded,
                onToggle = { toggle("Model") }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${models.size} available",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.refreshModels() }) { Text("Refresh") }
                }
                Spacer(Modifier.height(4.dp))
                var dropdownOpen by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = dropdownOpen,
                    onExpandedChange = { dropdownOpen = it }
                ) {
                    OutlinedTextField(
                        value = currentModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Current model") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOpen)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = dropdownOpen,
                        onDismissRequest = { dropdownOpen = false }
                    ) {
                        models.forEach { name ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        name,
                                        style = if (name == currentModel)
                                            MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        else MaterialTheme.typography.bodyMedium
                                    )
                                },
                                onClick = {
                                    viewModel.setModel(name)
                                    dropdownOpen = false
                                    dismissAnimated()
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Or type a custom model ID",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customModel,
                        onValueChange = { customModel = it },
                        placeholder = { Text("e.g. gemini-3.0-pro-exp") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (customModel.isNotBlank()) {
                                viewModel.setModel(customModel.trim())
                                customModel = ""
                                dismissAnimated()
                            }
                        }
                    ) { Text("Use") }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Image generation model (Imagen)",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                var imagenDropdownOpen by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = imagenDropdownOpen,
                    onExpandedChange = { imagenDropdownOpen = it }
                ) {
                    OutlinedTextField(
                        value = imagenModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Imagen model") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = imagenDropdownOpen)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = imagenDropdownOpen,
                        onDismissRequest = { imagenDropdownOpen = false }
                    ) {
                        com.gemini.bridge.RestGeminiCore.AVAILABLE_IMAGEN_MODELS.forEach { name ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        name,
                                        style = if (name == imagenModel)
                                            MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        else MaterialTheme.typography.bodyMedium
                                    )
                                },
                                onClick = {
                                    viewModel.setImagenModel(name)
                                    imagenDropdownOpen = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customImagenModel,
                        onValueChange = { customImagenModel = it },
                        placeholder = { Text("e.g. imagen-4.0-generate-001") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (customImagenModel.isNotBlank()) {
                                viewModel.setImagenModel(customImagenModel.trim())
                                customImagenModel = ""
                            }
                        }
                    ) { Text("Use") }
                }
                Text(
                    "The model uses `generate_image` automatically when you ask for " +
                        "a drawing/illustration. Imagen is billed separately from Gemini.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsAccordion(
                title = "Workspace",
                icon = Icons.Default.Folder,
                expanded = "Workspace" in expanded,
                onToggle = { toggle("Workspace") }
            ) {
                Text(
                    workspaceLabel,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                Spacer(Modifier.height(6.dp))
                val path = workspacePath
                val reason = workspaceReason
                if (path != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "✓ Termux-visible path",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                path,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "⚠ Not reachable from Termux",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                reason ?: "Unknown reason.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Shell commands will run in Termux's \$HOME. Use the " +
                                    "file tools for workspace files, or pick a folder " +
                                    "under /storage/emulated/0/ below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { folderLauncher.launch(null) }) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pick folder")
                }
            }

            SettingsAccordion(
                title = "Tool approvals",
                icon = Icons.Default.Security,
                expanded = "Tool approvals" in expanded,
                onToggle = { toggle("Tool approvals") }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-approve destructive tools", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Skips the approval dialog for write/edit/delete/shell.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoApprove,
                        onCheckedChange = { viewModel.setAutoApprove(it) }
                    )
                }
            }

            SettingsAccordion(
                title = "Autosave conversation",
                icon = Icons.Default.History,
                expanded = "Autosave" in expanded,
                onToggle = { toggle("Autosave") }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep the current conversation", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Saves the ongoing chat after each turn and restores it " +
                                "when you reopen the app. Turning this off clears the " +
                                "saved snapshot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSave,
                        onCheckedChange = { viewModel.setAutoSaveEnabled(it) }
                    )
                }
            }

            SettingsAccordion(
                title = "Auto-compression",
                icon = Icons.Default.Memory,
                expanded = "Auto-compression" in expanded,
                onToggle = { toggle("Auto-compression") }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Compress automatically", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Summarises the history when the context fills up so the " +
                                "chat can keep going without hitting the token limit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoCompress,
                        onCheckedChange = { viewModel.setAutoCompressEnabled(it) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Trigger at ${(compressThreshold * 100).toInt()}% of the context window",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = compressThreshold,
                    onValueChange = { viewModel.setAutoCompressThreshold(it) },
                    valueRange = 0.5f..0.95f,
                    steps = 8,
                    enabled = autoCompress
                )
                val (total, limit) = tokenUsage.total to tokenUsage.limit
                if (total > 0) {
                    val pct = if (limit != null && limit > 0)
                        " (${((total.toFloat() / limit) * 100).toInt()}% used)" else ""
                    Text(
                        "Current session: $total tokens" +
                            (if (limit != null) " / $limit" else "") + pct,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.compressSession() }) {
                    Text("Compress now")
                }
            }

            SettingsAccordion(
                title = "Theme",
                icon = Icons.Default.Palette,
                expanded = "Theme" in expanded,
                onToggle = { toggle("Theme") }
            ) {
                ThemeMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeChange(mode) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == themeMode,
                            onClick = { onThemeChange(mode) }
                        )
                        Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            SettingsAccordion(
                title = "Tools available",
                icon = Icons.Default.Build,
                expanded = "Tools available" in expanded,
                onToggle = { toggle("Tools available") }
            ) {
                viewModel.availableTools.forEach { tool ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tool.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val badge = tool.category.name.lowercase() +
                            if (tool.destructive) " · write" else ""
                        Surface(
                            color = if (tool.destructive) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            SettingsAccordion(
                title = "Termux shell",
                icon = Icons.Default.Terminal,
                expanded = "Termux shell" in expanded,
                onToggle = { toggle("Termux shell") }
            ) {
                TermuxBody(viewModel)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark")
}

@Composable
private fun SettingsAccordion(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    content()
                }
            }
        }
    }
}

private const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"

private fun hasRunCommandPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, RUN_COMMAND_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED

private enum class TermuxSource(val label: String, val isPlayStore: Boolean) {
    PLAY_STORE("Play Store (outdated, won't work)", true),
    F_DROID("F-Droid", false),
    GITHUB("GitHub / sideload", false),
    OTHER_STORE("Other store", false),
    UNKNOWN("Unknown source", false),
}

private fun detectTermuxSource(context: Context): TermuxSource {
    val pm = context.packageManager
    val installer = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo("com.termux").installingPackageName
        } else {
            @Suppress("DEPRECATION") pm.getInstallerPackageName("com.termux")
        }
    }.getOrNull()
    return when (installer) {
        "com.android.vending" -> TermuxSource.PLAY_STORE
        "org.fdroid.fdroid", "org.fdroid.basic", "com.aurora.store" -> TermuxSource.F_DROID
        null, "com.google.android.packageinstaller",
        "com.android.packageinstaller", "com.github.android" -> TermuxSource.GITHUB
        else -> TermuxSource.OTHER_STORE
    }
}

private fun termuxDeclaresRunCommand(context: Context): Boolean = runCatching {
    context.packageManager.getPermissionInfo(RUN_COMMAND_PERMISSION, 0)
    true
}.getOrDefault(false)

@Composable
private fun TermuxBody(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val installed = viewModel.termuxInstalled

    if (!installed) {
        Text(
            "Termux is not installed. It powers real shell commands " +
                "(git, gradle, curl…). Install from F-Droid — the Play Store " +
                "build is outdated and does NOT accept external commands.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { openUrl(context, "https://f-droid.org/packages/com.termux/") }) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Install Termux")
            }
            OutlinedButton(onClick = { openUrl(context, "https://f-droid.org/packages/com.termux.api/") }) {
                Text("+ Termux:API")
            }
        }
        return
    }

    val source = remember { detectTermuxSource(context) }
    val declaresPermission = remember { termuxDeclaresRunCommand(context) }
    var permissionGranted by remember { mutableStateOf(hasRunCommandPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (!granted) {
            Toast.makeText(
                context,
                "Permission not granted. If no dialog appeared, your Termux " +
                    "build does not declare RUN_COMMAND — reinstall from F-Droid.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Text(
        "Three things must all be true for shell commands to work:",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(8.dp))

    TermuxStep(
        number = "1",
        title = "Termux build supports external commands",
        ok = !source.isPlayStore && declaresPermission,
        body = {
            Text(
                "Installed from: ${source.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (source.isPlayStore || !declaresPermission) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (source.isPlayStore)
                        "The Play Store version of Termux was abandoned in 2020. It " +
                            "does NOT register the RUN_COMMAND service or permission, " +
                            "which is why nothing appears under Permissions in Android " +
                            "Settings. Uninstall it and reinstall the F-Droid build."
                    else
                        "The installed Termux does not declare the RUN_COMMAND " +
                            "permission, so Android can't list it under Permissions. " +
                            "Reinstall Termux from F-Droid (or termux/termux-app on GitHub).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openUrl(context, "https://f-droid.org/packages/com.termux/") }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("F-Droid Termux")
                    }
                    OutlinedButton(onClick = {
                        openUrl(context, "https://github.com/termux/termux-app/releases/latest")
                    }) { Text("GitHub APK") }
                }
            } else {
                Text(
                    "Good — this build declares com.termux.permission.RUN_COMMAND.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )

    TermuxStep(
        number = "2",
        title = "Android RUN_COMMAND permission",
        ok = permissionGranted,
        body = {
            when {
                permissionGranted -> {
                    Text(
                        "Granted. You should be able to run shell commands now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "If it was just granted, open Termux and run " +
                            "`termux-reload-settings` once so the change takes effect.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                !declaresPermission -> Text(
                    "Skipped — fix step 1 first. Android can't grant a permission " +
                        "that no installed app declares.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {
                    Text(
                        "Android treats com.termux.permission.RUN_COMMAND as a " +
                            "dangerous permission — it must be granted explicitly. " +
                            "Tap Request and approve the popup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { permissionLauncher.launch(RUN_COMMAND_PERMISSION) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Request permission")
                        }
                        OutlinedButton(
                            onClick = { openAppSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open app settings")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "If \"Request\" does nothing, open app settings → Permissions. " +
                            "On some Android versions the entry is hidden under " +
                            "\"Unused permissions\" or \"More permissions\".",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "After granting: open Termux once and run `termux-reload-settings` " +
                            "so the permission change is picked up by the running session.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )

    TermuxStep(
        number = "3",
        title = "Termux allows external apps",
        ok = null,
        body = {
            Text(
                "With the permission granted, Termux still needs `allow-external-apps=true` " +
                    "in its config. One tap: the command is copied to the clipboard and " +
                    "Termux opens. Long-press in Termux, tap Paste, press Enter — done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { copyBootstrapAndOpenTermux(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Copy & open Termux")
            }
            Spacer(Modifier.height(10.dp))
            var showManual by remember { mutableStateOf(false) }
            TextButton(onClick = { showManual = !showManual }) {
                Text(
                    if (showManual) "Hide manual commands" else "Prefer manual? Show the two commands",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (showManual) {
                TermuxCommandRow(
                    step = "a.",
                    command = "mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties",
                    context = context
                )
                TermuxCommandRow(
                    step = "b.",
                    command = "termux-reload-settings",
                    context = context
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { openTermux(context) }) {
                    Icon(Icons.Default.Terminal, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Open Termux")
                }
            }
        }
    )

    Spacer(Modifier.height(10.dp))
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testOutput by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf<Boolean?>(null) }
    Button(
        enabled = !testing && permissionGranted && declaresPermission,
        onClick = {
            testing = true
            testOutput = null
            scope.launch {
                val out = runCatching { viewModel.testTermuxShell() }
                    .getOrElse { it.message ?: "unknown error" }
                testOk = !out.startsWith("exit=")
                testOutput = out
                testing = false
            }
        }
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(if (testing) "Testing…" else "Test shell now")
    }
    testOutput?.let { out ->
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    if (testOk == true) "Shell reachable ✓" else "Shell failed",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (testOk == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    out,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}

@Composable
private fun TermuxStep(
    number: String,
    title: String,
    ok: Boolean?,
    body: @Composable () -> Unit
) {
    val accent = when (ok) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = accent.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        number,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                val badge = when (ok) { true -> "OK"; false -> "missing"; null -> "check" }
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
            Spacer(Modifier.height(6.dp))
            body()
        }
    }
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun TermuxCommandRow(step: String, command: String, context: Context) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$step $command",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.IconButton(onClick = {
                copyToClipboard(context, "termux-command", command)
                Toast.makeText(context, "Copied — paste it in Termux", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy command")
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openTermux(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("com.termux")
    if (launch != null) {
        try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
    openUrl(context, "https://f-droid.org/packages/com.termux/")
}

// The chicken-and-egg of Termux bootstrap: we can't run commands via
// RUN_COMMAND until `allow-external-apps=true` is set, but setting it
// requires running commands. The one-tap path: drop the whole chained
// command onto the clipboard and foreground Termux — the user just
// long-presses, pastes, presses Enter once. No per-line toggling.
private const val TERMUX_BOOTSTRAP_CMD =
    "mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings && echo '✓ Gemini bridge ready'"

private fun copyBootstrapAndOpenTermux(context: Context) {
    copyToClipboard(context, "termux-bootstrap", TERMUX_BOOTSTRAP_CMD)
    Toast.makeText(
        context,
        "Command copied. Long-press in Termux → Paste → Enter, then come back.",
        Toast.LENGTH_LONG
    ).show()
    openTermux(context)
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
fun TermuxSetupDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("Enable shell commands")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                Text(
                    "Gemini can run real shell commands (git, gradle, curl…) " +
                        "through Termux. This is optional — skip if you only need " +
                        "chat and in-app file tools.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                TermuxBody(viewModel)
                Spacer(Modifier.height(8.dp))
                Text(
                    "You can reopen this guide anytime from Settings → Termux shell.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
