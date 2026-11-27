// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.scan

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
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

@Composable
actual fun QrScanner(onText: (String) -> Unit, onProblem: (ScanProblem) -> Unit, modifier: Modifier) {
    val text by rememberUpdatedState(onText)
    val problem by rememberUpdatedState(onProblem)
    val video = requireNotNull(AVMediaTypeVideo)
    var allowed by remember { mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(video) == AVAuthorizationStatusAuthorized) }
    LaunchedEffect(Unit) {
        when (AVCaptureDevice.authorizationStatusForMediaType(video)) {
            AVAuthorizationStatusAuthorized -> allowed = true
            AVAuthorizationStatusNotDetermined -> AVCaptureDevice.requestAccessForMediaType(video) { granted ->
                dispatch_async(dispatch_get_main_queue()) { if (granted) allowed = true else problem(ScanProblem.Denied) }
            }
            else -> problem(ScanProblem.Denied)
        }
    }
    if (!allowed) {
        Box(modifier)
        return
    }
    val camera = remember { Camera { text(it) } }
    DisposableEffect(camera) {
        if (!camera.start()) problem(ScanProblem.Unavailable)
        onDispose { camera.stop() }
    }
    UIKitView(factory = { camera.view }, modifier = modifier)
}

/** The back camera, its picture in [view], and the QR codes it reads. */
@OptIn(ExperimentalForeignApi::class)
private class Camera(onText: (String) -> Unit) {
    private val session = AVCaptureSession()
    private val reader = QrRead(onText)
    private val queue = dispatch_queue_create("ca.gureet.plasma.remote.camera", null)
    private val preview = AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill }
    val view: UIView = PreviewView(preview)

    /** False when there's no camera to use (the simulator has none). */
    fun start(): Boolean {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(requireNotNull(AVMediaTypeVideo)) ?: return false
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return false
        if (!session.canAddInput(input)) return false
        session.addInput(input)
        val output = AVCaptureMetadataOutput()
        if (!session.canAddOutput(output)) return false
        session.addOutput(output)
        val qr = requireNotNull(AVMetadataObjectTypeQRCode)
        if (qr !in output.availableMetadataObjectTypes) return false
        output.setMetadataObjectsDelegate(reader, dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(qr)
        // startRunning waits for the camera: not on the main thread.
        dispatch_async(queue) { session.startRunning() }
        return true
    }

    fun stop() = dispatch_async(queue) { session.stopRunning() }
}

private class QrRead(private val onText: (String) -> Unit) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(output: AVCaptureOutput, didOutputMetadataObjects: List<*>, fromConnection: AVCaptureConnection) {
        didOutputMetadataObjects.firstNotNullOfOrNull { (it as? AVMetadataMachineReadableCodeObject)?.stringValue }?.let(onText)
    }
}

/** A view whose camera picture fills it, whatever size Compose gives it. */
@OptIn(ExperimentalForeignApi::class)
private class PreviewView(private val preview: AVCaptureVideoPreviewLayer) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        layer.addSublayer(preview)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        preview.setFrame(bounds)
        CATransaction.commit()
    }
}

@Composable
actual fun rememberOpenSettings(): () -> Unit = remember {
    {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        if (url != null) UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any?>(), null)
    }
}
