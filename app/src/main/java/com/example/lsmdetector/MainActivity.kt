package com.example.lsmdetector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraPreviewUseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.lsmdetector.ui.theme.LSMDetectorTheme
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LSMDetectorTheme {
                LSMDetectorApp()
            }
        }
    }
}

private enum class AppScreen {
    Login,
    Register,
    Detector,
    Training
}

/**
 * Raíz de la aplicación.
 *
 * Inicializa SQLite para señas, restaura la sesión de Firebase y decide qué
 * pantalla mostrar. Firebase se ocupa de usuarios; SQLite se ocupa de muestras.
 */
@Composable
fun LSMDetectorApp() {
    val context = LocalContext.current
    val userDatabase = remember {
        UserDatabaseHelper(context).also {
            it.importBundledSamplesIfEmpty(context)
        }
    }
    // Firebase restaura automáticamente al usuario que ya inició sesión.
    val authManager = remember { FirebaseAuthManager() }
    val savedSession = remember { authManager.currentUser() }
    var currentScreen by remember {
        mutableStateOf(if (savedSession != null) AppScreen.Detector else AppScreen.Login)
    }
    var userName by remember { mutableStateOf(savedSession?.name.orEmpty()) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentScreen) {
            AppScreen.Login -> LoginScreen(
                onLogin = { email, password, onResult ->
                    // El resultado llega de forma asíncrona porque requiere red.
                    authManager.login(email, password) { result ->
                        result.onSuccess { user ->
                            userName = user.name
                            currentScreen = AppScreen.Detector
                        }
                        onResult(result)
                    }
                },
                onCreateAccount = { currentScreen = AppScreen.Register }
            )

            AppScreen.Register -> RegisterScreen(
                onRegister = { name, email, password, onResult ->
                    // Firebase crea la cuenta y guarda el nombre en el perfil.
                    authManager.register(name, email, password) { result ->
                        result.onSuccess { user ->
                            userName = user.name
                            currentScreen = AppScreen.Detector
                        }
                        onResult(result)
                    }
                },
                onBackToLogin = { currentScreen = AppScreen.Login }
            )

            AppScreen.Detector -> DetectorScreen(
                userName = userName,
                userDatabase = userDatabase,
                onOpenTraining = { currentScreen = AppScreen.Training },
                onLogout = {
                    authManager.logout()
                    userName = ""
                    currentScreen = AppScreen.Login
                }
            )

            AppScreen.Training -> TrainingScreen(
                userDatabase = userDatabase,
                onBack = { currentScreen = AppScreen.Detector }
            )
        }
    }
}

// Formulario de acceso conectado a Firebase Authentication.
@Composable
private fun LoginScreen(
    onLogin: (String, String, (Result<AuthenticatedUser>) -> Unit) -> Unit,
    onCreateAccount: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    AuthScaffold(
        title = "Lengua de Señas Mexicana",
        subtitle = "Inicia sesión para comenzar a reconocer y traducir señas"
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                error = ""
            },
            label = { Text("Correo electronico") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                error = ""
            },
            label = { Text("Contrasena") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    error = "Completa correo y contrasena."
                } else {
                    isLoading = true
                    error = ""
                    onLogin(email, password) { result ->
                        isLoading = false
                        result.exceptionOrNull()?.let {
                            error = firebaseErrorMessage(it, "Correo o contrasena incorrectos.")
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Ingresando..." else "Iniciar sesion")
        }
        TextButton(
            onClick = onCreateAccount,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Crear cuenta")
        }
    }
}

// Formulario de registro. Valida localmente antes de llamar a Firebase.
@Composable
private fun RegisterScreen(
    onRegister: (String, String, String, (Result<AuthenticatedUser>) -> Unit) -> Unit,
    onBackToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    AuthScaffold(
        title = "Crear cuenta",
        subtitle = "Guarda tu perfil para continuar con el detector"
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                error = ""
            },
            label = { Text("Nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                error = ""
            },
            label = { Text("Correo electronico") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                error = ""
            },
            label = { Text("Contrasena") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                error = ""
            },
            label = { Text("Confirmar contrasena") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                error = when {
                    name.isBlank() || email.isBlank() || password.isBlank() -> "Completa todos los campos."
                    password.length < 6 -> "La contrasena debe tener al menos 6 caracteres."
                    password != confirmPassword -> "Las contrasenas no coinciden."
                    else -> ""
                }
                if (error.isBlank()) {
                    isLoading = true
                    onRegister(name, email, password) { result ->
                        isLoading = false
                        result.exceptionOrNull()?.let {
                            error = firebaseErrorMessage(it, "No se pudo crear la cuenta.")
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Creando cuenta..." else "Registrar")
        }
        TextButton(
            onClick = onBackToLogin,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ya tengo cuenta")
        }
    }
}

// Encabezado visual compartido por inicio de sesión y creación de cuenta.
@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "Icono de Lengua de Señas Mexicana",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                content()
            }
        }
    }
}

/**
 * Pantalla principal de traducción.
 *
 * Mantiene un búfer de frames para movimientos, aplica filtros temporales para
 * evitar falsos positivos y construye la frase confirmada por el usuario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetectorScreen(
    userName: String,
    userDatabase: UserDatabaseHelper,
    onOpenTraining: () -> Unit,
    onLogout: () -> Unit
) {
    val recognizer = remember { SignRecognizer(userDatabase.getAllSignSamples()) }
    var latestLandmarks by remember { mutableStateOf("") }
    var detectorMessage by remember { mutableStateOf("Coloca tu mano frente a la camara.") }
    var prediction by remember { mutableStateOf<SignPrediction?>(null) }
    var motionBuffer by remember { mutableStateOf(emptyList<String>()) }
    var lastRecognitionTime by remember { mutableStateOf(0L) }
    var candidateLabel by remember { mutableStateOf("") }
    var candidateSince by remember { mutableStateOf(0L) }
    var lastMotionCommitTime by remember { mutableStateOf(0L) }
    var motionVotes by remember { mutableStateOf(emptyList<String>()) }
    var motionLocked by remember { mutableStateOf(false) }
    var motionQuietSince by remember { mutableStateOf(0L) }
    var phrase by remember { mutableStateOf("") }
    var confirmationProgress by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Lengua de Señas Mexicana",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Reconocimiento y traducción en tiempo real",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onOpenTraining) {
                        Text("Entrenar")
                    }
                    TextButton(onClick = onLogout) {
                        Text("Salir")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hola, $userName",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Haz una sena dentro del encuadre",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (latestLandmarks.isBlank()) {
                                MaterialTheme.colorScheme.outline
                            } else {
                                Color(0xFF18A66A)
                            },
                            RoundedCornerShape(6.dp)
                        )
                )
            }
            CameraPanel(
                modifier = Modifier.weight(1f),
                landmarks = latestLandmarks,
                onLandmarksDetected = { landmarks ->
                    latestLandmarks = landmarks
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastRecognitionTime >= RECOGNITION_INTERVAL_MS) {
                        lastRecognitionTime = now
                        motionBuffer = (motionBuffer + landmarks).takeLast(LIVE_MOTION_BUFFER_FRAMES)

                        val staticPrediction = recognizer.recognizeStatic(landmarks)
                        val motionPrediction = recognizer.recognizeMotion(motionBuffer)
                            ?.takeIf { motion ->
                                val staticConfidence = staticPrediction?.confidence ?: 0
                                motion.confidence >= MOTION_MIN_CONFIDENCE &&
                                    (
                                        staticConfidence < STATIC_MOTION_GUARD_CONFIDENCE ||
                                            motion.confidence >=
                                            staticConfidence + MOTION_CONFIDENCE_MARGIN
                                        )
                            }

                        // Después de escribir un movimiento se bloquea la
                        // detección hasta que la mano repose. Así no se repite.
                        if (motionLocked) {
                            if (motionPrediction == null) {
                                if (motionQuietSince == 0L) motionQuietSince = now
                                if (now - motionQuietSince >= MOTION_RELEASE_MS) {
                                    motionLocked = false
                                    motionQuietSince = 0L
                                    motionVotes = emptyList()
                                }
                            } else {
                                motionQuietSince = 0L
                            }
                        } else if (motionPrediction != null) {
                            // Exige varias predicciones iguales antes de escribir.
                            motionVotes = (motionVotes + motionPrediction.label)
                                .takeLast(MOTION_REQUIRED_VOTES)
                            val stableMotion = motionVotes.size == MOTION_REQUIRED_VOTES &&
                                motionVotes.all { it == motionPrediction.label }

                            if (
                                stableMotion &&
                                now - lastMotionCommitTime >= MOTION_COMMIT_COOLDOWN_MS
                            ) {
                                phrase = phrase.appendRecognizedLabel(motionPrediction.label)
                                lastMotionCommitTime = now
                                motionLocked = true
                                motionQuietSince = 0L
                                motionVotes = emptyList()
                                motionBuffer = emptyList()
                            }
                        } else {
                            motionVotes = emptyList()
                        }

                        // Solo se muestra un movimiento cuando ya acumula votos.
                        val stableMotionPrediction = motionPrediction?.takeIf {
                            !motionLocked && motionVotes.count { vote -> vote == it.label } >= 2
                        }
                        prediction = stableMotionPrediction
                            ?: staticPrediction?.takeIf {
                                it.confidence >= MIN_RECOGNITION_CONFIDENCE
                            }

                        val recognizedLabel = prediction?.label
                        if (recognizedLabel == null || recognizedLabel in MotionLabels) {
                            candidateLabel = ""
                            candidateSince = 0L
                            confirmationProgress = 0f
                        } else if (recognizedLabel != candidateLabel) {
                            candidateLabel = recognizedLabel
                            candidateSince = now
                            confirmationProgress = 0f
                        } else {
                            // Las letras estáticas se escriben tras mantenerlas
                            // durante el tiempo configurado.
                            confirmationProgress =
                                ((now - candidateSince).toFloat() / STATIC_HOLD_DURATION_MS)
                                    .coerceIn(0f, 1f)
                            if (now - candidateSince >= STATIC_HOLD_DURATION_MS) {
                                phrase = phrase.appendRecognizedLabel(recognizedLabel)
                                candidateSince = now
                                confirmationProgress = 0f
                            }
                        }

                        detectorMessage = if (prediction != null) {
                            if (recognizedLabel in MotionLabels) {
                                "Movimiento consistente detectado."
                            } else {
                                "Manten la letra durante 3 segundos para escribirla."
                            }
                        } else if (motionLocked) {
                            "Movimiento escrito. Deten la mano un momento para continuar."
                        } else {
                            "Mano detectada, pero aun no hay coincidencia confiable."
                        }
                    }
                },
                onNoHandDetected = {
                    latestLandmarks = ""
                    prediction = null
                    motionBuffer = emptyList()
                    motionVotes = emptyList()
                    motionLocked = false
                    motionQuietSince = 0L
                    candidateLabel = ""
                    candidateSince = 0L
                    confirmationProgress = 0f
                    detectorMessage = "Coloca tu mano frente a la camara."
                },
                onLandmarkerError = { message ->
                    detectorMessage = message
                }
            )
            PhraseComposer(
                phrase = phrase,
                onAddSpace = {
                    if (phrase.isNotEmpty() && !phrase.endsWith(" ")) {
                        phrase += " "
                    }
                },
                onDelete = {
                    phrase = phrase.dropLast(1)
                },
                onClear = {
                    phrase = ""
                }
            )
            DetectionStatus(
                message = detectorMessage,
                prediction = prediction,
                confirmationProgress = confirmationProgress
            )
        }
    }
}

// Área editable donde se acumulan letras y palabras reconocidas.
@Composable
private fun PhraseComposer(
    phrase: String,
    onAddSpace: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Tu frase",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (phrase.isBlank()) "La frase aparecera aqui" else phrase,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (phrase.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                color = if (phrase.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAddSpace,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Espacio")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Borrar")
                }
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Limpiar")
                }
            }
        }
    }
}

/**
 * Modo temporal de recopilación.
 *
 * Las letras estáticas guardan poses individuales; las dinámicas guardan una
 * secuencia de frames que representa el movimiento completo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainingScreen(
    userDatabase: UserDatabaseHelper,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedLabel by remember { mutableStateOf(TrainingLabels.first()) }
    var sampleCounts by remember { mutableStateOf(userDatabase.getSignSampleCounts()) }
    var statusMessage by remember { mutableStateOf("Selecciona una sena y guarda ejemplos.") }
    var latestLandmarks by remember { mutableStateOf("") }
    var isAutoCapturing by remember { mutableStateOf(false) }
    var autoCaptureProgress by remember { mutableStateOf(0) }
    var lastAutoCaptureTime by remember { mutableStateOf(0L) }
    var motionFrames by remember { mutableStateOf(emptyList<String>()) }
    val isMotionLabel = selectedLabel in MotionLabels
    val countsByLabel = sampleCounts.associate { it.label to it.count }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Entrenamiento de señas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Lengua de Señas Mexicana",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CameraPanel(
                modifier = Modifier.weight(0.8f),
                landmarks = latestLandmarks,
                onLandmarksDetected = { landmarks ->
                    latestLandmarks = landmarks
                    if (isAutoCapturing) {
                        // Limita la frecuencia para no guardar frames idénticos
                        // ni saturar SQLite.
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastAutoCaptureTime >= AUTO_CAPTURE_INTERVAL_MS) {
                            lastAutoCaptureTime = now
                            if (isMotionLabel) {
                                motionFrames = motionFrames + landmarks
                                autoCaptureProgress = motionFrames.size
                                statusMessage = "Grabando movimiento $selectedLabel: " +
                                    "$autoCaptureProgress/$MOTION_SEQUENCE_FRAMES"

                                if (motionFrames.size >= MOTION_SEQUENCE_FRAMES) {
                                    val saved = userDatabase.saveSignSequence(
                                        selectedLabel,
                                        motionFrames
                                    )
                                    isAutoCapturing = false
                                    sampleCounts = userDatabase.getSignSampleCounts()
                                    statusMessage = if (saved) {
                                        "Movimiento guardado para $selectedLabel. " +
                                            "Repite la grabacion desde la posicion inicial."
                                    } else {
                                        "No se pudo guardar el movimiento."
                                    }
                                    motionFrames = emptyList()
                                }
                            } else if (userDatabase.saveSignSample(selectedLabel, landmarks)) {
                                autoCaptureProgress += 1
                                statusMessage =
                                    "Capturando $selectedLabel: $autoCaptureProgress/$AUTO_CAPTURE_TARGET"

                                if (autoCaptureProgress >= AUTO_CAPTURE_TARGET) {
                                    isAutoCapturing = false
                                    sampleCounts = userDatabase.getSignSampleCounts()
                                    statusMessage =
                                        "Captura terminada: $AUTO_CAPTURE_TARGET muestras de $selectedLabel."
                                }
                            } else {
                                isAutoCapturing = false
                                statusMessage = "La captura se detuvo por un error al guardar."
                            }
                        }
                    } else {
                        statusMessage = "Mano detectada. Lista para guardar $selectedLabel."
                    }
                },
                onLandmarkerError = { message ->
                    statusMessage = message
                },
                onNoHandDetected = {
                    latestLandmarks = ""
                    if (isAutoCapturing) {
                        isAutoCapturing = false
                        motionFrames = emptyList()
                        statusMessage = "Se perdio la mano. Vuelve a colocarla y reinicia la captura."
                    } else {
                        statusMessage = "Coloca tu mano frente a la camara."
                    }
                }
            )
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sena seleccionada: $selectedLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isMotionLabel) {
                            "Movimientos guardados: ${countsByLabel[selectedLabel] ?: 0}"
                        } else {
                            "Muestras guardadas: ${countsByLabel[selectedLabel] ?: 0}"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isMotionLabel) {
                        Button(
                            onClick = {
                                if (latestLandmarks.isBlank()) {
                                    statusMessage = "Pon tu mano frente a la camara antes de guardar."
                                } else {
                                    val saved =
                                        userDatabase.saveSignSample(selectedLabel, latestLandmarks)
                                    if (saved) {
                                        sampleCounts = userDatabase.getSignSampleCounts()
                                        statusMessage = "Puntos guardados para $selectedLabel."
                                    } else {
                                        statusMessage = "No se pudo guardar el ejemplo."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Guardar ejemplo")
                        }
                    }
                    Button(
                        onClick = {
                            if (isAutoCapturing) {
                                isAutoCapturing = false
                                motionFrames = emptyList()
                                sampleCounts = userDatabase.getSignSampleCounts()
                                statusMessage = if (isMotionLabel) {
                                    "Grabacion de movimiento cancelada."
                                } else {
                                    "Captura detenida en $autoCaptureProgress muestras."
                                }
                            } else if (latestLandmarks.isBlank()) {
                                statusMessage =
                                    "Primero coloca la mano frente a la camara."
                            } else {
                                autoCaptureProgress = 0
                                motionFrames = emptyList()
                                lastAutoCaptureTime = 0L
                                isAutoCapturing = true
                                statusMessage = if (isMotionLabel) {
                                    "Realiza ahora el movimiento completo de $selectedLabel."
                                } else {
                                    "Manten la sena $selectedLabel y mueve ligeramente la mano."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isAutoCapturing) {
                                if (isMotionLabel) {
                                    "Detener movimiento ($autoCaptureProgress/$MOTION_SEQUENCE_FRAMES)"
                                } else {
                                    "Detener captura ($autoCaptureProgress/$AUTO_CAPTURE_TARGET)"
                                }
                            } else if (isMotionLabel) {
                                "Grabar movimiento"
                            } else {
                                "Capturar $AUTO_CAPTURE_TARGET automaticamente"
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val exportFile = exportTrainingSamplesToCsv(
                                context = context,
                                samples = userDatabase.getAllSignSamples()
                            )
                            statusMessage = if (exportFile != null) {
                                "CSV guardado en: ${exportFile.absolutePath}"
                            } else {
                                "No hay muestras para exportar."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Exportar CSV")
                    }
                }
            }
            Text(
                text = "Abecedario y palabras",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TrainingLabels) { label ->
                    val count = countsByLabel[label] ?: 0
                    SignLabelRow(
                        label = label,
                        sampleCount = count,
                        isSelected = selectedLabel == label,
                        onSelect = {
                            isAutoCapturing = false
                            autoCaptureProgress = 0
                            motionFrames = emptyList()
                            selectedLabel = label
                            statusMessage = if (label in MotionLabels) {
                                "Lista para grabar el movimiento completo de $label."
                            } else {
                                "Lista para capturar ejemplos de $label."
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SignLabelRow(
    label: String,
    sampleCount: Int,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$sampleCount muestras",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Vista de cámara compartida por detector y entrenamiento.
@Composable
private fun CameraPanel(
    modifier: Modifier = Modifier,
    landmarks: String = "",
    onLandmarksDetected: ((String) -> Unit)? = null,
    onNoHandDetected: (() -> Unit)? = null,
    onLandmarkerError: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                lensFacing = lensFacing,
                onLandmarksDetected = onLandmarksDetected,
                onNoHandDetected = onNoHandDetected,
                onLandmarkerError = onLandmarkerError
            )
            HandLandmarksOverlay(
                landmarks = landmarks,
                mirrorHorizontally = lensFacing == CameraSelector.LENS_FACING_FRONT,
                modifier = Modifier.fillMaxSize()
            )
            OutlinedButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Text(
                    if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        "Camara frontal"
                    } else {
                        "Camara trasera"
                    }
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Permite el acceso a la camara para iniciar la deteccion.",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Permitir camara")
                }
            }
        }
    }
}

/**
 * Une CameraX con MediaPipe.
 *
 * Preview dibuja el video e ImageAnalysis entrega frames al detector de mano.
 */
@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    lensFacing: Int,
    onLandmarksDetected: ((String) -> Unit)? = null,
    onNoHandDetected: (() -> Unit)? = null,
    onLandmarkerError: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val handLandmarkerHelper = remember(onLandmarksDetected) {
        onLandmarksDetected?.let {
            HandLandmarkerHelper(
                context = context,
                onLandmarksDetected = it,
                onNoHandDetected = { onNoHandDetected?.invoke() },
                onError = { message -> onLandmarkerError?.invoke(message) }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            handLandmarkerHelper?.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val owner = lifecycleOwner ?: return@AndroidView
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = CameraPreviewUseCase.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageAnalysis = handLandmarkerHelper?.let { helper ->
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    helper.detect(imageProxy)
                                }
                            }
                    }
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()
                    cameraProvider.unbindAll()
                    if (imageAnalysis != null) {
                        cameraProvider.bindToLifecycle(
                            owner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } else {
                        cameraProvider.bindToLifecycle(
                            owner,
                            cameraSelector,
                            preview
                        )
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    )
}

// Dibuja las 21 articulaciones y sus conexiones encima de la cámara.
@Composable
private fun HandLandmarksOverlay(
    landmarks: String,
    mirrorHorizontally: Boolean,
    modifier: Modifier = Modifier
) {
    val points = remember(landmarks) { landmarks.toNormalizedHandPoints() }
    if (points.isEmpty()) return

    Canvas(modifier = modifier) {
        HandConnections.forEach { (start, end) ->
            val startPoint = points.getOrNull(start)
            val endPoint = points.getOrNull(end)
            if (startPoint != null && endPoint != null) {
                drawLine(
                    color = Color(0xFFE8A598),
                    start = androidx.compose.ui.geometry.Offset(
                        x = startPoint.first.overlayX(size.width, mirrorHorizontally),
                        y = startPoint.second * size.height
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        x = endPoint.first.overlayX(size.width, mirrorHorizontally),
                        y = endPoint.second * size.height
                    ),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
            }
        }

        points.forEach { point ->
            drawCircle(
                color = Color(0xFFD7F0DE),
                radius = 7f,
                center = androidx.compose.ui.geometry.Offset(
                    x = point.first.overlayX(size.width, mirrorHorizontally),
                    y = point.second * size.height
                )
            )
            drawCircle(
                color = Color(0xFF263238),
                radius = 3f,
                center = androidx.compose.ui.geometry.Offset(
                    x = point.first.overlayX(size.width, mirrorHorizontally),
                    y = point.second * size.height
                )
            )
        }
    }
}

private fun Float.overlayX(width: Float, mirrorHorizontally: Boolean): Float {
    val normalizedX = if (mirrorHorizontally) 1f - this else this
    return normalizedX * width
}

@Composable
private fun DetectionStatus(
    message: String = "Modelo pendiente de entrenamiento",
    prediction: SignPrediction? = null,
    confirmationProgress: Float = 0f
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Seña detectada",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = prediction?.label ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (prediction != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Text(
                    text = prediction?.let { "${it.confidence}%" } ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            LinearProgressIndicator(
                progress = { confirmationProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val TrainingLabels = listOf(
    "A",
    "B",
    "C",
    "D",
    "E",
    "F",
    "G",
    "H",
    "I",
    "J",
    "K",
    "L",
    "M",
    "N",
    "\u00D1",
    "O",
    "P",
    "Q",
    "R",
    "S",
    "T",
    "U",
    "V",
    "W",
    "X",
    "Y",
    "Z",
    "HOLA",
    "GRACIAS"
)

private val HandConnections = listOf(
    0 to 1,
    1 to 2,
    2 to 3,
    3 to 4,
    0 to 5,
    5 to 6,
    6 to 7,
    7 to 8,
    5 to 9,
    9 to 10,
    10 to 11,
    11 to 12,
    9 to 13,
    13 to 14,
    14 to 15,
    15 to 16,
    13 to 17,
    17 to 18,
    18 to 19,
    19 to 20,
    0 to 17
)

private const val AUTO_CAPTURE_TARGET = 100
private const val AUTO_CAPTURE_INTERVAL_MS = 120L
private const val MOTION_SEQUENCE_FRAMES = 30
private const val LIVE_MOTION_BUFFER_FRAMES = 18
private const val RECOGNITION_INTERVAL_MS = 80L
private const val MIN_RECOGNITION_CONFIDENCE = 40
private const val STATIC_HOLD_DURATION_MS = 3_000L
private const val MOTION_MIN_CONFIDENCE = 68
private const val STATIC_MOTION_GUARD_CONFIDENCE = 52
private const val MOTION_CONFIDENCE_MARGIN = 12
private const val MOTION_REQUIRED_VOTES = 3
private const val MOTION_COMMIT_COOLDOWN_MS = 1_200L
private const val MOTION_RELEASE_MS = 650L

private val MotionLabels = setOf("J", "K", "\u00D1", "Q", "X", "Z", "HOLA")

private fun String.appendRecognizedLabel(label: String): String {
    return if (label == "HOLA") {
        this + "HOLA "
    } else {
        this + label
    }
}

private fun String.toNormalizedHandPoints(): List<Pair<Float, Float>> {
    val values = split(",").mapNotNull { value ->
        value.toFloatOrNull()
    }
    if (values.size < 63) return emptyList()

    return values.chunked(3)
        .take(21)
        .map { coordinates ->
            coordinates[0].coerceIn(0f, 1f) to coordinates[1].coerceIn(0f, 1f)
        }
}

private fun exportTrainingSamplesToCsv(
    context: Context,
    samples: List<SignSample>
): File? {
    // El CSV puede extraerse con Device Explorer y usarse para entrenar TFLite.
    if (samples.isEmpty()) return null

    val exportDir = File(context.filesDir, "training_exports")
    exportDir.mkdirs()

    val exportFile = File(exportDir, "sign_samples.csv")
    val csv = buildString {
        appendLine("id,label,sample_type,frame_count,landmarks,created_at")
        samples.forEach { sample ->
            appendLine(
                listOf(
                    sample.id.toString(),
                    sample.label,
                    sample.sampleType,
                    sample.frameCount.toString(),
                    sample.landmarks,
                    sample.createdAt.toString()
                ).joinToString(",") { it.toCsvValue() }
            )
        }
    }

    exportFile.writeText(csv)
    return exportFile
}

private fun String.toCsvValue(): String {
    val escaped = replace("\"", "\"\"")
    return "\"$escaped\""
}

private fun firebaseErrorMessage(error: Throwable, fallback: String): String {
    val message = error.localizedMessage.orEmpty()
    return when {
        "email address is already in use" in message.lowercase() ->
            "Ese correo ya esta registrado."

        "password is invalid" in message.lowercase() ||
            "credential is incorrect" in message.lowercase() ->
            "Correo o contrasena incorrectos."

        "network error" in message.lowercase() ->
            "No hay conexion a internet."

        "badly formatted" in message.lowercase() ->
            "El correo electronico no es valido."

        else -> fallback
    }
}

@ComposePreview(showBackground = true)
@Composable
fun LoginPreview() {
    LSMDetectorTheme {
        LoginScreen(
            onLogin = { _, _, onResult ->
                onResult(Result.success(AuthenticatedUser("demo@correo.com", "Demo")))
            },
            onCreateAccount = {}
        )
    }
}
