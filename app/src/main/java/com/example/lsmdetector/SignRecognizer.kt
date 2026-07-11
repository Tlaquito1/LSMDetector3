package com.example.lsmdetector

import kotlin.math.abs
import kotlin.math.sqrt

data class SignPrediction(
    val label: String,
    val confidence: Int
)

private data class HandFrame(
    val pose: FloatArray,
    val wristX: Float,
    val wristY: Float,
    val scale: Float
)

/**
 * Reconocedor provisional basado en similitud contra las muestras SQLite.
 *
 * No reemplaza al futuro modelo TFLite: permite probar las señas mientras se
 * recopilan datos. Las poses estáticas y los movimientos se comparan distinto.
 */
class SignRecognizer(samples: List<SignSample>) {
    // Agrupa varias poses por letra para no depender de una única muestra.
    private val staticSamples = samples
        .filter { it.sampleType == TYPE_STATIC && it.label !in MOTION_LABELS }
        .mapNotNull { sample ->
            sample.landmarks.toHandFrame()?.let { frame -> sample.label to frame }
        }
        .groupBy({ it.first }, { it.second })

    // Cada muestra dinámica contiene una secuencia completa separada por "|".
    private val motionSamples = samples
        .filter { it.sampleType == TYPE_MOTION && it.label in MOTION_LABELS }
        .mapNotNull { sample ->
            val sequence = sample.landmarks
                .split(FRAME_SEPARATOR)
                .mapNotNull { it.toHandFrame() }
            if (sequence.size >= MIN_STORED_MOTION_FRAMES) sample.label to sequence else null
        }
        .groupBy({ it.first }, { it.second })

    fun recognizeStatic(landmarks: String): SignPrediction? {
        val current = landmarks.toHandFrame() ?: return null
        // Promedia los vecinos más cercanos de cada clase para reducir ruido.
        val ranked = staticSamples.mapNotNull { (label, samples) ->
            val nearest = samples
                .asSequence()
                .map { frameDistance(current.pose, it.pose) }
                .sorted()
                .take(NEIGHBORS_PER_LABEL)
                .toList()
            if (nearest.isEmpty()) null else label to nearest.average().toFloat()
        }.sortedBy { it.second }

        return ranked.toPrediction(STATIC_CONFIDENCE_SCALE)
    }

    fun recognizeMotion(frames: List<String>): SignPrediction? {
        if (frames.size < MIN_LIVE_MOTION_FRAMES) return null
        val current = frames.mapNotNull { it.toHandFrame() }
        // Una mano casi inmóvil no debe activar K, J, Z, HOLA u otra seña
        // dinámica. Se mide desplazamiento real desde el inicio, no la suma de
        // pequeños temblores que puede producir MediaPipe.
        if (current.size < MIN_LIVE_MOTION_FRAMES || !hasIntentionalMotion(current)) {
            return null
        }

        val ranked = motionSamples.mapNotNull { (label, samples) ->
            val nearest = samples
                .asSequence()
                .map { sequenceDistance(current, it) }
                .sorted()
                .take(MOTION_NEIGHBORS_PER_LABEL)
                .toList()
            if (nearest.isEmpty()) null else label to nearest.average().toFloat()
        }.sortedBy { it.second }

        return ranked.toPrediction(MOTION_CONFIDENCE_SCALE)
    }

    private fun List<Pair<String, Float>>.toPrediction(scale: Float): SignPrediction? {
        val best = firstOrNull() ?: return null
        val secondDistance = getOrNull(1)?.second ?: scale
        val absoluteScore = 1f - (best.second / scale).coerceIn(0f, 1f)
        val separationScore = if (secondDistance <= 0f) {
            0f
        } else {
            ((secondDistance - best.second) / secondDistance).coerceIn(0f, 1f)
        }
        // Combina cercanía absoluta y separación respecto a la segunda opción.
        val confidence = ((absoluteScore * 0.75f + separationScore * 0.25f) * 100).toInt()
        return SignPrediction(best.first, confidence)
    }

    private fun sequenceDistance(first: List<HandFrame>, second: List<HandFrame>): Float {
        val sampleCount = minOf(MOTION_COMPARE_FRAMES, first.size, second.size)
        if (sampleCount < MIN_LIVE_MOTION_FRAMES) return Float.MAX_VALUE

        val firstStart = first.first()
        val secondStart = second.first()
        var poseTotal = 0f
        var trajectoryTotal = 0f

        repeat(sampleCount) { index ->
            val firstFrame = first[index * (first.size - 1) / maxOf(sampleCount - 1, 1)]
            val secondFrame = second[index * (second.size - 1) / maxOf(sampleCount - 1, 1)]
            poseTotal += frameDistance(firstFrame.pose, secondFrame.pose)

            val firstDx = (firstFrame.wristX - firstStart.wristX) / firstStart.scale
            val firstDy = (firstFrame.wristY - firstStart.wristY) / firstStart.scale
            val secondDx = (secondFrame.wristX - secondStart.wristX) / secondStart.scale
            val secondDy = (secondFrame.wristY - secondStart.wristY) / secondStart.scale
            val dx = firstDx - secondDx
            val dy = firstDy - secondDy
            trajectoryTotal += sqrt(dx * dx + dy * dy)
        }

        // La forma de la mano y la trayectoria de la muñeca aportan al resultado.
        val poseDistance = poseTotal / sampleCount
        val trajectoryDistance = trajectoryTotal / sampleCount
        val endDistance = endpointDistance(first, second)
        val pathDistance = abs(normalizedPathLength(first) - normalizedPathLength(second))
        return poseDistance * POSE_WEIGHT +
            trajectoryDistance * TRAJECTORY_WEIGHT +
            endDistance * ENDPOINT_WEIGHT +
            pathDistance * PATH_WEIGHT
    }

    private fun hasIntentionalMotion(frames: List<HandFrame>): Boolean {
        val start = frames.first()
        var maximumWristDisplacement = 0f
        var maximumPoseChange = 0f
        var wristPathLength = 0f
        var previous = start

        frames.drop(1).forEach { frame ->
            val dx = (frame.wristX - start.wristX) / start.scale
            val dy = (frame.wristY - start.wristY) / start.scale
            val pathDx = (frame.wristX - previous.wristX) / start.scale
            val pathDy = (frame.wristY - previous.wristY) / start.scale
            wristPathLength += sqrt(pathDx * pathDx + pathDy * pathDy)
            maximumWristDisplacement = maxOf(
                maximumWristDisplacement,
                sqrt(dx * dx + dy * dy)
            )
            maximumPoseChange = maxOf(
                maximumPoseChange,
                frameDistance(start.pose, frame.pose)
            )
            previous = frame
        }

        return (maximumWristDisplacement >= MIN_WRIST_DISPLACEMENT &&
            wristPathLength >= MIN_WRIST_PATH_LENGTH) ||
            maximumPoseChange >= MIN_POSE_CHANGE
    }

    private fun endpointDistance(first: List<HandFrame>, second: List<HandFrame>): Float {
        val firstStart = first.first()
        val secondStart = second.first()
        val firstEnd = first.last()
        val secondEnd = second.last()
        val firstDx = (firstEnd.wristX - firstStart.wristX) / firstStart.scale
        val firstDy = (firstEnd.wristY - firstStart.wristY) / firstStart.scale
        val secondDx = (secondEnd.wristX - secondStart.wristX) / secondStart.scale
        val secondDy = (secondEnd.wristY - secondStart.wristY) / secondStart.scale
        val dx = firstDx - secondDx
        val dy = firstDy - secondDy
        return sqrt(dx * dx + dy * dy)
    }

    private fun normalizedPathLength(frames: List<HandFrame>): Float {
        if (frames.size < 2) return 0f
        val start = frames.first()
        var total = 0f
        frames.zipWithNext { previous, current ->
            val dx = (current.wristX - previous.wristX) / start.scale
            val dy = (current.wristY - previous.wristY) / start.scale
            total += sqrt(dx * dx + dy * dy)
        }
        return total
    }

    private fun frameDistance(first: FloatArray, second: FloatArray): Float {
        if (first.size != second.size) return Float.MAX_VALUE
        var sum = 0f
        first.indices.forEach { index ->
            val difference = first[index] - second[index]
            sum += difference * difference
        }
        return sqrt(sum / first.size)
    }

    private fun String.toHandFrame(): HandFrame? {
        val values = split(",").mapNotNull { it.toFloatOrNull() }
        if (values.size < LANDMARK_VALUE_COUNT) return null

        val wristX = values[0]
        val wristY = values[1]
        val wristZ = values[2]
        var scale = 0f

        for (index in 0 until HAND_LANDMARK_COUNT) {
            val offset = index * 3
            val dx = values[offset] - wristX
            val dy = values[offset + 1] - wristY
            val dz = values[offset + 2] - wristZ
            scale = maxOf(scale, sqrt(dx * dx + dy * dy + dz * dz))
        }
        if (scale < 0.0001f) return null

        // Trasladar al origen de la muñeca y dividir por escala hace la
        // comparación menos sensible a distancia y posición frente a cámara.
        val pose = FloatArray(LANDMARK_VALUE_COUNT) { index ->
            val axisOrigin = when (index % 3) {
                0 -> wristX
                1 -> wristY
                else -> wristZ
            }
            (values[index] - axisOrigin) / scale
        }
        return HandFrame(pose, wristX, wristY, scale)
    }

    companion object {
        private const val TYPE_STATIC = "STATIC"
        private const val TYPE_MOTION = "MOTION"
        private const val FRAME_SEPARATOR = "|"
        private const val HAND_LANDMARK_COUNT = 21
        private const val LANDMARK_VALUE_COUNT = 63
        private const val MIN_STORED_MOTION_FRAMES = 15
        private const val MIN_LIVE_MOTION_FRAMES = 12
        private const val MOTION_COMPARE_FRAMES = 20
        private const val NEIGHBORS_PER_LABEL = 5
        private const val MOTION_NEIGHBORS_PER_LABEL = 3
        private const val MIN_WRIST_DISPLACEMENT = 0.24f
        private const val MIN_WRIST_PATH_LENGTH = 0.32f
        private const val MIN_POSE_CHANGE = 0.19f
        private const val POSE_WEIGHT = 0.46f
        private const val TRAJECTORY_WEIGHT = 0.34f
        private const val ENDPOINT_WEIGHT = 0.14f
        private const val PATH_WEIGHT = 0.06f
        private const val STATIC_CONFIDENCE_SCALE = 0.32f
        private const val MOTION_CONFIDENCE_SCALE = 0.62f
        private val MOTION_LABELS = setOf(
            "J",
            "K",
            "\u00D1",
            "Q",
            "X",
            "Z",
            "HOLA",
            "GRACIAS",
            "POR FAVOR",
            "BUENOS DIAS",
            "BUENAS TARDES",
            "BUENAS NOCHES",
            "COMO ESTAS",
            "ME AYUDAS",
            "TE QUIERO",
            "PERDON",
            "CON PERMISO",
            "AYUDA",
            "BAÑO",
            "EMERGENCIA"
        )
    }
}
