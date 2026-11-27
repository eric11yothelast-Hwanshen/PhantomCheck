package com.phantom.checker.ui.link

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.phantom.checker.R
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "QrScannerOverlay"

/**
 * #477 / #486 全屏 QR 掃碼器（CameraX Preview + ImageAnalysis + ML Kit bundled FORMAT_QR_CODE）：
 * - 無 Google Play 服務依賴（bundled 模型，離線多廠牌可用）
 * - #486 強化：
 *   1. BarcodeScanning.getClient 包 runCatching，任何初始化異常降級為 cameraError 絕不閃退
 *   2. 開相機前檢查 provider.hasCamera(DEFAULT_BACK_CAMERA)，無後鏡頭時顯示誠實文案引導手動輸入
 *   3. ON_PAUSE/ON_STOP unbind 後於 ON_RESUME 自動 rebind，解決回前景黑屏
 *   4. analyzer 內 scanner.process() 包 runCatching + imageProxy 必關保證
 *   5. onDispose 順序：closed flag → unbindAll → executor.shutdownNow + awaitTermination → scanner.close()，消除 close/process 競態
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScannerDialog(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val handled = remember { AtomicBoolean(false) }
    val closed = remember { AtomicBoolean(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val scannerResult = remember {
        runCatching {
            val opts = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            BarcodeScanning.getClient(opts)
        }
    }
    val scanner: BarcodeScanner? = scannerResult.getOrNull()
    var cameraError by remember {
        mutableStateOf(
            if (scanner == null) {
                val reason = scannerResult.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName } ?: "MLKit init error"
                context.getString(R.string.link_hub_camera_init_failed, reason)
            } else {
                null
            }
        )
    }

    var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var activePreviewView by remember { mutableStateOf<PreviewView?>(null) }
    var needsRebindOnResume by remember { mutableStateOf(false) }

    val dismissSafely: () -> Unit = {
        if (closed.compareAndSet(false, true)) {
            handled.set(true)
            runCatching { boundProvider?.unbindAll() }
            currentOnDismiss()
        }
    }

    fun bindCameraUseCases(provider: ProcessCameraProvider, previewView: PreviewView) {
        if (closed.get() || handled.get()) return
        val activeScanner = scanner ?: return
        runCatching {
            if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                if (!closed.get()) {
                    cameraError = context.getString(R.string.link_hub_camera_no_back_camera)
                }
                return
            }
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                if (closed.get() || handled.get()) {
                    runCatching { imageProxy.close() }
                    return@setAnalyzer
                }
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    runCatching { imageProxy.close() }
                    return@setAnalyzer
                }
                var handedToAsync = false
                try {
                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    val task = activeScanner.process(inputImage)
                    handedToAsync = true
                    task.addOnSuccessListener { barcodes ->
                        if (closed.get() || handled.get()) return@addOnSuccessListener
                        val raw = barcodes.firstOrNull()?.rawValue?.trim()
                        if (!raw.isNullOrBlank() && !closed.get() && handled.compareAndSet(false, true)) {
                            if (closed.get()) return@addOnSuccessListener
                            ContextCompat.getMainExecutor(context).execute {
                                if (closed.compareAndSet(false, true)) {
                                    runCatching { boundProvider?.unbindAll() }
                                    currentOnScanned(raw)
                                }
                            }
                        }
                    }.addOnFailureListener { err ->
                        if (!closed.get()) {
                            Log.w(TAG, "ML Kit barcode process failed: ${err.javaClass.simpleName}: ${err.message}")
                        }
                    }.addOnCompleteListener {
                        runCatching { imageProxy.close() }
                    }
                } catch (err: Throwable) {
                    Log.w(TAG, "Analyzer error: ${err.javaClass.simpleName}: ${err.message}")
                    if (!closed.get()) {
                        val msg = context.getString(R.string.link_hub_camera_init_failed, err.message ?: err.javaClass.simpleName)
                        ContextCompat.getMainExecutor(context).execute {
                            if (!closed.get() && cameraError == null) {
                                cameraError = msg
                            }
                        }
                    }
                } finally {
                    if (!handedToAsync) {
                        runCatching { imageProxy.close() }
                    }
                }
            }
            if (closed.get() || handled.get()) return
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
            if (!closed.get()) {
                cameraError = null
            }
        }.onFailure { err ->
            Log.w(TAG, "Camera bindToLifecycle failed: ${err.javaClass.simpleName}: ${err.message}")
            if (!closed.get()) {
                cameraError = context.getString(R.string.link_hub_camera_init_failed, err.message ?: err.javaClass.simpleName)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    needsRebindOnResume = true
                    runCatching { boundProvider?.unbindAll() }
                }
                Lifecycle.Event.ON_RESUME -> {
                    val provider = boundProvider
                    val pv = activePreviewView
                    if (needsRebindOnResume && provider != null && pv != null && !closed.get() && !handled.get()) {
                        needsRebindOnResume = false
                        bindCameraUseCases(provider, pv)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            // #486 嚴格釋放順序：先標記 closed → unbind → 停止 executor 並短等排空 → 最後關閉 ML Kit scanner
            closed.set(true)
            handled.set(true)
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { boundProvider?.unbindAll() }
            boundProvider = null
            activePreviewView = null
            runCatching {
                cameraExecutor.shutdownNow()
                cameraExecutor.awaitTermination(300L, TimeUnit.MILLISECONDS)
            }
            runCatching { scanner?.close() }
        }
    }

    Dialog(
        onDismissRequest = dismissSafely,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    activePreviewView = previewView
                    if (scanner != null) {
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener(
                            {
                                if (closed.get() || handled.get()) return@addListener
                                runCatching {
                                    val provider = providerFuture.get()
                                    if (closed.get() || handled.get()) {
                                        provider.unbindAll()
                                        return@runCatching
                                    }
                                    boundProvider = provider
                                    bindCameraUseCases(provider, previewView)
                                }.onFailure { err ->
                                    Log.w(TAG, "ProcessCameraProvider.getInstance failed: ${err.javaClass.simpleName}")
                                    if (!closed.get()) {
                                        cameraError = ctx.getString(R.string.link_hub_camera_init_failed, err.message ?: err.javaClass.simpleName)
                                    }
                                }
                            },
                            ContextCompat.getMainExecutor(ctx)
                        )
                    }
                    previewView
                }
            )

            // Top bar with close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.link_hub_qr_scan_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Button(onClick = dismissSafely) {
                    Text(stringResource(R.string.link_hub_qr_scan_close))
                }
            }

            // Center viewfinder frame
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(3.dp, Color(0xFF6EA8FE), RoundedCornerShape(20.dp))
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = cameraError ?: stringResource(R.string.link_hub_qr_scan_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (cameraError != null) Color(0xFFFF7B72) else Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}
