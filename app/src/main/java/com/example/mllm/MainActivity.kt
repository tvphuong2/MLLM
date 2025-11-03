package com.example.mllm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mllm.agent.AgentViewModel
import com.example.mllm.agent.AgentViewModelFactory
import com.example.mllm.ui.AgentHomeScreen
import com.example.mllm.ui.theme.MLLMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MLLMTheme {
                val context = LocalContext.current
                val viewModel: AgentViewModel = viewModel(factory = AgentViewModelFactory(context))
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val permission = Manifest.permission.RECORD_AUDIO
                val lifecycleOwner = LocalLifecycleOwner.current
                var hasPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
                    )
                }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    hasPermission = granted
                    if (granted) {
                        viewModel.onPermissionGranted()
                    } else {
                        viewModel.onPermissionDenied()
                    }
                }

                LaunchedEffect(Unit) {
                    if (hasPermission) {
                        viewModel.onPermissionGranted()
                    } else {
                        launcher.launch(permission)
                    }
                }

                DisposableEffect(lifecycleOwner, permission) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission != granted) {
                                hasPermission = granted
                            }
                            if (granted) {
                                viewModel.onPermissionGranted()
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                AgentHomeScreen(
                    uiState = uiState,
                    onRequestPermission = { launcher.launch(permission) },
                    onHoldStart = viewModel::onStartRecording,
                    onHoldEnd = viewModel::onStopRecording,
                )
            }
        }
    }
}
