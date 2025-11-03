package com.example.mllm.ui

import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mllm.R
import com.example.mllm.agent.AgentViewModel

@Composable
fun AgentHomeScreen(
    uiState: AgentViewModel.AgentUiState,
    onRequestPermission: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = uiState.errorMessage
    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusRow(uiState)
            if (!uiState.permissionGranted) {
                PermissionCard(onRequestPermission = onRequestPermission)
            } else {
                TranscriptCard(title = stringResource(id = R.string.label_transcription), text = uiState.transcription)
                TranscriptCard(title = stringResource(id = R.string.label_response), text = uiState.response)
                Spacer(modifier = Modifier.height(8.dp))
                HoldToTalkButton(
                    isRecording = uiState.isRecording,
                    enabled = !uiState.isProcessing,
                    onHoldStart = onHoldStart,
                    onHoldEnd = onHoldEnd,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(state: AgentViewModel.AgentUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusChip(
            label = if (state.integrityVerified) R.string.status_integrity_passed else R.string.status_integrity_pending,
            active = state.integrityVerified,
        )
        StatusChip(
            label = if (state.isAsrReady) R.string.status_asr_ready else R.string.status_asr_stub,
            active = state.isAsrReady,
        )
        StatusChip(
            label = if (state.isLlmReady) R.string.status_llm_ready else R.string.status_llm_stub,
            active = state.isLlmReady,
        )
        if (state.isProcessing) {
            StatusChip(label = R.string.status_processing, active = true)
        }
    }
}

@Composable
private fun StatusChip(label: Int, active: Boolean) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text = stringResource(id = label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            labelColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun TranscriptCard(title: String, text: String) {
    ElevatedCard(tonalElevation = 4.dp, colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (text.isBlank()) {
                Text(
                    text = stringResource(id = R.string.placeholder_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(text = text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    ElevatedCard(tonalElevation = 4.dp) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(id = R.string.permission_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(id = R.string.permission_rationale), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRequestPermission) {
                Text(text = stringResource(id = R.string.permission_button))
            }
        }
    }
}

@Composable
private fun HoldToTalkButton(
    isRecording: Boolean,
    enabled: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val label = if (isRecording) R.string.release_to_send else R.string.hold_to_talk
        Button(
            onClick = {},
            enabled = enabled,
            modifier = Modifier
                .pointerInteropFilter { motionEvent ->
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            if (enabled) onHoldStart()
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (enabled) onHoldEnd()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            Text(text = stringResource(id = label))
        }
    }
}
