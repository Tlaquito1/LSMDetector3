package com.example.lsmdetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Convierte cada frame de CameraX en una entrada para MediaPipe Hand Landmarker.
 * El resultado contiene 21 puntos; cada punto aporta x, y y z (63 valores).
 */
class HandLandmarkerHelper(
    context: Context,
    private val onLandmarksDetected: (String) -> Unit,
    private val onNoHandDetected: () -> Unit,
    private val onError: (String) -> Unit
) {
    private var handLandmarker: HandLandmarker? = null
    private val isProcessing = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        try {
            // El modelo oficial vive en app/src/main/assets/hand_landmarker.task.
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    isProcessing.set(false)
                    handleResult(result)
                }
                .setErrorListener { error ->
                    isProcessing.set(false)
                    notifyError(error.message ?: "Error de MediaPipe.")
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (error: Exception) {
            notifyError(error.message ?: "No se pudo iniciar MediaPipe.")
        }
    }

    fun detect(imageProxy: ImageProxy) {
        val landmarker = handLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        // Descarta frames mientras MediaPipe sigue procesando el anterior.
        // Esto evita acumular imágenes y mantiene fluida la cámara.
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        try {
            val rotatedBitmap = imageProxy.toRgbaBitmap()
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            landmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
        } catch (error: Exception) {
            isProcessing.set(false)
            notifyError(error.message ?: "No se pudo procesar la imagen.")
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        handLandmarker?.close()
        handLandmarker = null
        isProcessing.set(false)
    }

    private fun handleResult(result: HandLandmarkerResult) {
        val firstHand = result.landmarks().firstOrNull()
        if (firstHand == null) {
            mainHandler.post {
                onNoHandDetected()
            }
            return
        }
        val values = firstHand.flatMap { landmark ->
            listOf(landmark.x(), landmark.y(), landmark.z())
        }

        // SQLite y el CSV guardan los 63 valores en una sola cadena.
        val landmarks = values.joinToString(separator = ",") { value ->
            String.format(Locale.US, "%.6f", value)
        }

        // Compose solo debe recibir cambios de estado desde el hilo principal.
        mainHandler.post {
            onLandmarksDetected(landmarks)
        }
    }

    private fun notifyError(message: String) {
        mainHandler.post {
            onError(message)
        }
    }

    private fun ImageProxy.toRgbaBitmap(): Bitmap {
        // ImageAnalysis entrega RGBA_8888; se copia a Bitmap y se corrige
        // la rotación indicada por la cámara antes de enviarlo a MediaPipe.
        val buffer = planes[0].buffer
        buffer.rewind()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        val rotationDegrees = imageInfo.rotationDegrees
        if (rotationDegrees == 0) return bitmap

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private const val MODEL_ASSET_PATH = "hand_landmarker.task"
    }
}
