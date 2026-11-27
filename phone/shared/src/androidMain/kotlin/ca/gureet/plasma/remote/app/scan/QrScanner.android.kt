// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors

@Composable
actual fun QrScanner(onText: (String) -> Unit, onProblem: (ScanProblem) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val text by rememberUpdatedState(onText)
    val problem by rememberUpdatedState(onProblem)
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (!granted) problem(ScanProblem.Denied)
    }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.CAMERA) }
    if (!allowed) {
        Box(modifier)
        return
    }
    val view = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    AndroidView({ view }, modifier)
    DisposableEffect(owner) {
        val reading = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener problem(ScanProblem.Unavailable)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            // The QR code with the certificate's fingerprint is dense: a 720p picture reads it from across a room.
            val sized = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(sized)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(reading, QrReader { found -> view.post { if (!disposed) text(found) } })
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure { problem(ScanProblem.Unavailable) }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            if (future.isDone) runCatching { future.get().unbindAll() }
            reading.shutdown()
        }
    }
}

/** Reads a QR code from each picture's brightness (the Y plane), at any angle. */
private class QrReader(private val found: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = QRCodeReader()

    override fun analyze(image: ImageProxy) {
        image.use {
            val plane = it.planes[0]
            val buffer = plane.buffer.apply { rewind() }
            val bytes = ByteArray(buffer.remaining()).also(buffer::get)
            val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, it.height, 0, 0, it.width, it.height, false)
            try {
                found(reader.decode(BinaryBitmap(HybridBinarizer(source))).text)
            } catch (_: ReaderException) {
                // No code in this picture.
            } finally {
                reader.reset()
            }
        }
    }
}

@Composable
actual fun rememberOpenSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
