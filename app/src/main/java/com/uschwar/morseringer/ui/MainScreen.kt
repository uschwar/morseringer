package com.uschwar.morseringer.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uschwar.morseringer.R
import com.uschwar.morseringer.domain.model.MorseSettings
import kotlin.math.roundToInt

private const val FREQUENCY_SLIDER_WIDTH = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDisclosureDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermissions() }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refreshPermissions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UnifiedStatusBar(
                hasContactsPermission = uiState.hasContactsPermission,
                hasPhoneStatePermission = uiState.hasPhoneStatePermission,
                isCallScreeningRoleHeld = uiState.isCallScreeningRoleHeld,
                isSilentMode = uiState.isSilentMode,
                onRequestPermissions = { showDisclosureDialog = true },
                onRequestCallScreeningRole = { requestCallScreeningRole(context, roleRequestLauncher::launch) },
            )

            if (showDisclosureDialog) {
                DisclosureDialog(
                    onAccept = {
                        showDisclosureDialog = false
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.READ_PHONE_STATE,
                            ),
                        )
                    },
                    onDismiss = { showDisclosureDialog = false },
                )
            }

            SettingsCard(
                wpm = uiState.wpm,
                frequencyHz = uiState.frequencyHz,
                onWpmChange = viewModel::updateWpm,
                onFrequencyChange = viewModel::updateFrequency,
                onValueChangeFinished = viewModel::confirmChange,
            )

            PreviewCard(
                previewText = uiState.previewText,
                previewMorse = uiState.previewMorse,
                isPlaying = uiState.isPlaying,
                onTextChange = viewModel::updatePreviewText,
                onPlayClick = viewModel::playPreview,
                onStopClick = viewModel::stopPreview,
            )

            RingtoneShortcutCard(onOpenSettings = viewModel::openSoundSettings)

        }
    }
}

private fun requestCallScreeningRole(context: Context, launch: (Intent) -> Unit) {
    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
    launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
}

@Composable
private fun UnifiedStatusBar(
    hasContactsPermission: Boolean,
    hasPhoneStatePermission: Boolean,
    isCallScreeningRoleHeld: Boolean,
    isSilentMode: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestCallScreeningRole: () -> Unit,
) {
    val isReady = hasContactsPermission && hasPhoneStatePermission && isCallScreeningRoleHeld
    val isWarning = !isReady || isSilentMode
    val containerColor = if (isWarning) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (isWarning) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isWarning) Icons.Default.Error else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.status_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = contentColor,
                    )
                    Text(
                        text = when {
                            !isReady -> stringResource(R.string.status_not_ready)
                            isSilentMode -> stringResource(R.string.status_silent_warning)
                            else -> stringResource(R.string.status_ready)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                    )
                }
            }

            if (isWarning) {
                // If it's both not ready AND silent, show the silent warning as a detail
                if (isSilentMode && !isReady) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.status_silent_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                    )
                }

                if (!hasContactsPermission || !hasPhoneStatePermission) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = contentColor,
                            contentColor = containerColor,
                        ),
                    ) {
                        Text(stringResource(R.string.btn_grant_permissions))
                    }
                }

                if (!isCallScreeningRoleHeld) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(
                        onClick = onRequestCallScreeningRole,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = contentColor,
                            contentColor = containerColor,
                        ),
                    ) {
                        Text(stringResource(R.string.btn_set_call_screening))
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclosureDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.disclosure_title)) },
        text = { Text(stringResource(R.string.disclosure_message)) },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.disclosure_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.disclosure_decline))
            }
        },
    )
}

@Composable
private fun RingtoneShortcutCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.ringtone_shortcut_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(R.string.ringtone_shortcut_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_open_sound_settings))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    wpm: Int,
    frequencyHz: Int,
    onWpmChange: (Int) -> Unit,
    onFrequencyChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
            )

            LabeledSlider(
                label = stringResource(R.string.settings_speed, wpm),
                value = wpm.toFloat(),
                onValueChange = { onWpmChange(it.roundToInt()) },
                onValueChangeFinished = { onValueChangeFinished() },
                valueRange = MorseSettings.MIN_WPM.toFloat()..MorseSettings.MAX_WPM.toFloat(),
                steps = (MorseSettings.MAX_WPM - MorseSettings.MIN_WPM) - 1,
            )

            LabeledSlider(
                label = stringResource(R.string.settings_pitch, frequencyHz),
                value = frequencyHz.toFloat(),
                onValueChange = { onFrequencyChange(it.roundToInt()) },
                onValueChangeFinished = { onValueChangeFinished() },
                valueRange = MorseSettings.MIN_FREQUENCY_HZ.toFloat()..MorseSettings.MAX_FREQUENCY_HZ.toFloat(),
                steps = (MorseSettings.MAX_FREQUENCY_HZ - MorseSettings.MIN_FREQUENCY_HZ) / FREQUENCY_SLIDER_WIDTH - 1,
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun PreviewCard(
    previewText: String,
    previewMorse: String,
    isPlaying: Boolean,
    onTextChange: (String) -> Unit,
    onPlayClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.preview_title),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = previewText,
                onValueChange = onTextChange,
                label = { Text(stringResource(R.string.preview_text_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (previewMorse.isNotBlank()) {
                Text(
                    text = previewMorse,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = if (isPlaying) onStopClick else onPlayClick,
                    modifier = Modifier.weight(1f),
                ) {
                    val label = stringResource(
                        if (isPlaying) R.string.btn_stop else R.string.btn_play_preview,
                    )
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = label,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MaterialTheme { MainScreen() }
}
