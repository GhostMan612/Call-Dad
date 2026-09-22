// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/PairingScreen.kt
// Location: app/src/main/java/com/calldad/ui/screens/PairingScreen.kt
package com.calldad.ui.screens

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calldad.ui.components.GiantButton
import com.calldad.ui.theme.CallGreen
import com.calldad.ui.theme.CallGreenDark
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PairingViewModel = pairingViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resetTrigger by viewModel.resetTrigger.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is PairingUiState.Paired) onPaired()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CallGreenDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val s = state) {
                is PairingUiState.CheckingModule -> {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Getting the scanner ready…",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This may take a moment on first use.",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }

                is PairingUiState.Loading -> {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Getting ready…",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                }

                is PairingUiState.Ready -> {
                    Text(
                        text = "Show this to the other device",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Image(
                        bitmap = s.qrBitmap.asImageBitmap(),
                        contentDescription = "Pairing QR code",
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Now scan the other device's code",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    QrScannerView(
                        onQrDecoded = viewModel::onQrScanned,
                        resetTrigger = resetTrigger,
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                is PairingUiState.Error -> {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    GiantButton(
                        label = "Try Again",
                        icon = Icons.Filled.Refresh,
                        onClick = viewModel::retryScanner,
                        containerColor = CallGreen
                    )
                }

                is PairingUiState.Paired -> {
                    Text(
                        "Paired!",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                }

                else -> Unit
            }
        }
    }
}

/**
 * Activity-scoped accessor (same doctrine as callViewModel()).
 * A bare viewModel() call cannot construct an AndroidViewModel
 * (no zero-arg constructor) and crashes at composition.
 */
@Composable
fun pairingViewModel(): PairingViewModel {
    val application = LocalContext.current.applicationContext as Application
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PairingViewModel(application) as T
            }
        }
    )
}

@Composable
private fun QrScannerView(
    onQrDecoded: (String) -> Unit,
    resetTrigger: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasPermission) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Camera permission is needed to scan.",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(
                androidx.camera.view.CameraController.IMAGE_ANALYSIS
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Release the camera hardware when the composable leaves
            // the composition. The state transition to Paired or Error
            // removes QrScannerView from the tree while the
            // NavBackStackEntry lifecycle remains active, so the
            // controller would otherwise keep the camera LED on.
            //
            // NOTE: the method is unbind(), NOT unbindAll().
            // unbindAll() is @hide and @RestrictTo(LIBRARY_GROUP) on
            // the CameraX class. It is not callable from application
            // code.
            cameraController.unbind()
        }
    }

    var decoded by remember { mutableStateOf(false) }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    LaunchedEffect(resetTrigger, scanner) {
        decoded = false
        cameraController.clearImageAnalysisAnalyzer()
        cameraController.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(context),
            MlKitAnalyzer(
                listOf(scanner),
                ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                ContextCompat.getMainExecutor(context)
            ) { result ->
                if (decoded) return@MlKitAnalyzer
                val barcodes = result?.getValue(scanner)
                val raw = barcodes?.firstOrNull()?.rawValue
                if (!raw.isNullOrBlank()) {
                    decoded = true
                    onQrDecoded(raw)
                }
            }
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { preview ->
                preview.controller = cameraController
                cameraController.bindToLifecycle(lifecycleOwner)
            }
        }
    )
}
