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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
        subtitle = "Reconoce, practica y traduce señas desde la cámara de tu celular"
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                error = ""
            },
            label = { Text("Correo electrónico") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            colors = authTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                error = ""
            },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            colors = authTextFieldColors(),
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
                    error = "Completa correo y contraseña."
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
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF204F8C),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = if (isLoading) "Ingresando..." else "Entrar al detector",
                fontWeight = FontWeight.Bold
            )
        }
        TextButton(
            onClick = onCreateAccount,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Crear cuenta nueva")
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
        subtitle = "Guarda tu perfil y continúa tu práctica en otros dispositivos"
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                error = ""
            },
            label = { Text("Nombre") },
            singleLine = true,
            colors = authTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                error = ""
            },
            label = { Text("Correo electrónico") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            colors = authTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                error = ""
            },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            colors = authTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                error = ""
            },
            label = { Text("Confirmar contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            colors = authTextFieldColors(),
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
                    password.length < 6 -> "La contraseña debe tener al menos 6 caracteres."
                    password != confirmPassword -> "Las contraseñas no coinciden."
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
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF204F8C),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = if (isLoading) "Creando cuenta..." else "Crear mi cuenta",
                fontWeight = FontWeight.Bold
            )
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
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFECF6FF),
            Color(0xFFFFF6EF),
            Color(0xFFF7FBF1)
        )
    )
    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.96f),
            Color(0xFFFFF9F4).copy(alpha = 0.94f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AuthBackgroundArt(modifier = Modifier.fillMaxSize())

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(cardGradient)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFFC8A8),
                                    Color(0xFFBCE7D0),
                                    Color(0xFFAED7FF)
                                )
                            ),
                            CircleShape
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
                    text = "Aprende - Detecta - Traduce",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF204F8C),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color(0xFFEAF4FF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF263238),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF51616D),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AuthInfoChip(text = "Cámara IA", modifier = Modifier.weight(1f))
                    AuthInfoChip(text = "Práctica LSM", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(24.dp))
                content()
            }
        }
    }
}

@Composable
private fun AuthBackgroundArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(
            color = Color(0xFF9DD6FF).copy(alpha = 0.38f),
            radius = size.minDimension * 0.28f,
            center = Offset(size.width * 0.12f, size.height * 0.18f)
        )
        drawCircle(
            color = Color(0xFFFFC1A6).copy(alpha = 0.42f),
            radius = size.minDimension * 0.22f,
            center = Offset(size.width * 0.92f, size.height * 0.2f)
        )
        drawCircle(
            color = Color(0xFFAADDBD).copy(alpha = 0.34f),
            radius = size.minDimension * 0.26f,
            center = Offset(size.width * 0.18f, size.height * 0.92f)
        )
    }
}

@Composable
private fun AuthInfoChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFFF4F8FA), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF3B4A52),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF204F8C),
    focusedLabelColor = Color(0xFF204F8C),
    cursorColor = Color(0xFF204F8C),
    unfocusedBorderColor = Color(0xFFB8C6CF),
    unfocusedLabelColor = Color(0xFF61717A)
)

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
    var detectorMessage by remember { mutableStateOf("Coloca tu mano frente a la cámara.") }
    var prediction by remember { mutableStateOf<SignPrediction?>(null) }
    var motionBuffer by remember { mutableStateOf(emptyList<String>()) }
    var lastRecognitionTime by remember { mutableStateOf(0L) }
    var candidateLabel by remember { mutableStateOf("") }
    var candidateSince by remember { mutableStateOf(0L) }
    var lastMotionCommitTime by remember { mutableStateOf(0L) }
    var motionVotes by remember { mutableStateOf(emptyList<String>()) }
    var staticVotes by remember { mutableStateOf(emptyList<String>()) }
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
                        Text("Práctica")
                    }
                    TextButton(onClick = onLogout) {
                        Text("Salir")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hola, $userName",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "La cámara es el área principal de traducción",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DetectionPill(hasHand = latestLandmarks.isNotBlank())
                }
            }
            CameraPanel(
                modifier = Modifier.weight(2.45f),
                landmarks = latestLandmarks,
                onLandmarksDetected = { landmarks ->
                    latestLandmarks = landmarks
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastRecognitionTime >= RECOGNITION_INTERVAL_MS) {
                        lastRecognitionTime = now
                        motionBuffer = (motionBuffer + landmarks).takeLast(LIVE_MOTION_BUFFER_FRAMES)

                        val staticPrediction = recognizer.recognizeStatic(landmarks)
                        val previewStaticPrediction = staticPrediction
                            ?.takeIf { it.confidence >= MIN_RECOGNITION_CONFIDENCE }
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

                        val stableStaticPrediction = if (motionLocked) {
                            staticVotes = emptyList()
                            null
                        } else {
                            previewStaticPrediction?.let { static ->
                                staticVotes = (staticVotes + static.label)
                                    .takeLast(STATIC_REQUIRED_VOTES)
                                val stableStatic = staticVotes.size == STATIC_REQUIRED_VOTES &&
                                    staticVotes.all { it == static.label }
                                static.takeIf { stableStatic }
                            } ?: run {
                                staticVotes = emptyList()
                                null
                            }
                        }

                        // Solo se muestra un movimiento cuando ya acumula votos.
                        val stableMotionPrediction = motionPrediction?.takeIf {
                            !motionLocked && motionVotes.count { vote -> vote == it.label } >=
                                MOTION_DISPLAY_VOTES
                        }
                        prediction = stableMotionPrediction
                            ?: stableStaticPrediction
                            ?: previewStaticPrediction

                        val recognizedLabel = prediction?.label
                        val writableStaticLabel = stableStaticPrediction
                            ?.takeIf { it.confidence >= STATIC_WRITE_CONFIDENCE }
                            ?.label
                            ?.takeIf { it !in MotionLabels }

                        if (writableStaticLabel == null) {
                            candidateLabel = ""
                            candidateSince = 0L
                            confirmationProgress = 0f
                        } else if (writableStaticLabel != candidateLabel) {
                            candidateLabel = writableStaticLabel
                            candidateSince = now
                            confirmationProgress = 0f
                        } else {
                            // Las letras estáticas se escriben tras mantenerlas
                            // durante el tiempo configurado.
                            confirmationProgress =
                                ((now - candidateSince).toFloat() / STATIC_HOLD_DURATION_MS)
                                    .coerceIn(0f, 1f)
                            if (now - candidateSince >= STATIC_HOLD_DURATION_MS) {
                                phrase = phrase.appendRecognizedLabel(writableStaticLabel)
                                candidateSince = now
                                confirmationProgress = 0f
                            }
                        }

                        detectorMessage = if (motionLocked) {
                            "Movimiento escrito. Detén la mano un momento para continuar."
                        } else if (prediction != null) {
                            if (recognizedLabel in MotionLabels) {
                                "Movimiento posible. Completa la trayectoria con calma."
                            } else if (writableStaticLabel == null) {
                                "Seña posible. Manténla quieta y centrada para confirmar."
                            } else {
                                "Seña clara. Manténla estable para escribirla."
                            }
                        } else {
                            "Mano detectada, esperando una coincidencia confiable."
                        }
                    }
                },
                onNoHandDetected = {
                    latestLandmarks = ""
                    prediction = null
                    motionBuffer = emptyList()
                    motionVotes = emptyList()
                    staticVotes = emptyList()
                    motionLocked = false
                    motionQuietSince = 0L
                    candidateLabel = ""
                    candidateSince = 0L
                    confirmationProgress = 0f
                    detectorMessage = "Coloca tu mano frente a la cámara."
                },
                onLandmarkerError = { message ->
                    detectorMessage = message
                }
            )
            DetectionStatus(
                message = detectorMessage,
                prediction = prediction,
                confirmationProgress = confirmationProgress
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
        }
    }
}

@Composable
private fun DetectionPill(hasHand: Boolean) {
    Row(
        modifier = Modifier
            .background(
                if (hasHand) Color(0xFFE0F4E8) else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (hasHand) Color(0xFF18A66A) else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(5.dp)
                )
        )
        Text(
            text = if (hasHand) "Mano" else "Sin mano",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
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
                text = if (phrase.isBlank()) "La frase aparecerá aquí" else phrase,
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
    var statusMessage by remember { mutableStateOf("Selecciona una seña y guarda ejemplos.") }
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
                            "Cámara amplia para capturar mejor tus muestras",
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
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedLabel,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isMotionLabel) {
                                "Movimiento: ocupa todo el encuadre"
                            } else {
                                "Seña estática: mano centrada y clara"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${countsByLabel[selectedLabel] ?: 0} muestras",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            CameraPanel(
                modifier = Modifier.weight(3.6f),
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
                                            "Repite la grabación desde la posición inicial."
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
                        statusMessage = "Se perdió la mano. Vuelve a colocarla y reinicia la captura."
                    } else {
                        statusMessage = "Coloca tu mano frente a la cámara."
                    }
                }
            )
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isAutoCapturing) {
                        val target = if (isMotionLabel) {
                            MOTION_SEQUENCE_FRAMES
                        } else {
                            AUTO_CAPTURE_TARGET
                        }
                        LinearProgressIndicator(
                            progress = {
                                (autoCaptureProgress.toFloat() / target).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    if (!isMotionLabel) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (latestLandmarks.isBlank()) {
                                        statusMessage =
                                            "Pon tu mano frente a la cámara antes de guardar."
                                    } else {
                                        val saved = userDatabase.saveSignSample(
                                            selectedLabel,
                                            latestLandmarks
                                        )
                                        if (saved) {
                                            sampleCounts = userDatabase.getSignSampleCounts()
                                            statusMessage = "Puntos guardados para $selectedLabel."
                                        } else {
                                            statusMessage = "No se pudo guardar el ejemplo."
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Guardar")
                            }
                            Button(
                                onClick = {
                                    if (isAutoCapturing) {
                                        isAutoCapturing = false
                                        motionFrames = emptyList()
                                        sampleCounts = userDatabase.getSignSampleCounts()
                                        statusMessage =
                                            "Captura detenida en $autoCaptureProgress muestras."
                                    } else if (latestLandmarks.isBlank()) {
                                        statusMessage =
                                            "Primero coloca la mano frente a la cámara."
                                    } else {
                                        autoCaptureProgress = 0
                                        motionFrames = emptyList()
                                        lastAutoCaptureTime = 0L
                                        isAutoCapturing = true
                                        statusMessage =
                                            "Mantén la seña $selectedLabel y mueve ligeramente la mano."
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (isAutoCapturing) {
                                        "Detener ($autoCaptureProgress/$AUTO_CAPTURE_TARGET)"
                                    } else {
                                        "Auto $AUTO_CAPTURE_TARGET"
                                    }
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isAutoCapturing) {
                                    isAutoCapturing = false
                                    motionFrames = emptyList()
                                    sampleCounts = userDatabase.getSignSampleCounts()
                                    statusMessage = "Grabación de movimiento cancelada."
                                } else if (latestLandmarks.isBlank()) {
                                    statusMessage =
                                        "Primero coloca la mano frente a la cámara."
                                } else {
                                    autoCaptureProgress = 0
                                    motionFrames = emptyList()
                                    lastAutoCaptureTime = 0L
                                    isAutoCapturing = true
                                    statusMessage =
                                        "Realiza ahora el movimiento completo de $selectedLabel."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (isAutoCapturing) {
                                    "Detener movimiento ($autoCaptureProgress/$MOTION_SEQUENCE_FRAMES)"
                                } else {
                                    "Grabar movimiento"
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Exportar CSV")
                        }
                        OutlinedButton(
                            onClick = {
                                isAutoCapturing = false
                                autoCaptureProgress = 0
                                motionFrames = emptyList()
                                statusMessage = "Captura lista para reiniciar."
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reiniciar")
                        }
                    }
                }
            }
            Text(
                text = "Abecedario y frases",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TrainingLabels) { label ->
                    val count = countsByLabel[label] ?: 0
                    SignLabelChip(
                        label = label,
                        sampleCount = count,
                        isSelected = selectedLabel == label,
                        isMotion = label in MotionLabels,
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
private fun SignLabelChip(
    label: String,
    sampleCount: Int,
    isSelected: Boolean,
    isMotion: Boolean,
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
        Column(
            modifier = Modifier
                .width(if (label.length > 3) 132.dp else 78.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isMotion) "mov. $sampleCount" else "$sampleCount",
                style = MaterialTheme.typography.labelSmall,
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
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                shape = RoundedCornerShape(8.dp)
            ),
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
            CameraGuideOverlay(modifier = Modifier.fillMaxSize())
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
                        "Cámara frontal"
                    } else {
                        "Cámara trasera"
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
                    text = "Permite el acceso a la cámara para iniciar la detección.",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Permitir cámara")
                }
            }
        }
    }
}

@Composable
private fun CameraGuideOverlay(modifier: Modifier = Modifier) {
    val guideColor = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier = modifier) {
        val horizontalPadding = size.width * 0.12f
        val verticalPadding = size.height * 0.13f
        val guideWidth = size.width - horizontalPadding * 2
        val guideHeight = size.height - verticalPadding * 2
        drawRoundRect(
            color = guideColor.copy(alpha = 0.22f),
            topLeft = androidx.compose.ui.geometry.Offset(horizontalPadding, verticalPadding),
            size = androidx.compose.ui.geometry.Size(guideWidth, guideHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(32f, 32f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
        )
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
                        text = if (prediction != null) {
                            if (confirmationProgress > 0f) "Confirmando" else "Seña posible"
                        } else {
                            "Esperando seña"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (prediction != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Text(
                    text = prediction?.let { "${it.label} ${it.confidence}%" } ?: "",
                    style = MaterialTheme.typography.labelLarge,
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
    "GRACIAS",
    "SI",
    "NO",
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
    "AGUA",
    "COMIDA",
    "BAÑO",
    "ESCUELA",
    "DOCTOR",
    "EMERGENCIA"
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
private const val LIVE_MOTION_BUFFER_FRAMES = 22
private const val RECOGNITION_INTERVAL_MS = 100L
private const val MIN_RECOGNITION_CONFIDENCE = 25
private const val STATIC_WRITE_CONFIDENCE = 56
private const val STATIC_REQUIRED_VOTES = 3
private const val STATIC_HOLD_DURATION_MS = 2_400L
private const val MOTION_MIN_CONFIDENCE = 58
private const val STATIC_MOTION_GUARD_CONFIDENCE = 54
private const val MOTION_CONFIDENCE_MARGIN = 8
private const val MOTION_REQUIRED_VOTES = 3
private const val MOTION_DISPLAY_VOTES = 1
private const val MOTION_COMMIT_COOLDOWN_MS = 1_500L
private const val MOTION_RELEASE_MS = 800L

private val MotionLabels = setOf(
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

private fun String.appendRecognizedLabel(label: String): String {
    return if (label in PhraseLabels) {
        appendQuickPhrase(label)
    } else {
        this + label
    }
}

private fun String.appendQuickPhrase(phrase: String): String {
    val current = trimEnd()
    return if (current.isBlank()) {
        "$phrase "
    } else {
        "$current $phrase "
    }
}

private val PhraseLabels = setOf(
    "HOLA",
    "GRACIAS",
    "POR FAVOR",
    "SI",
    "NO",
    "TE QUIERO",
    "PERDON",
    "CON PERMISO",
    "COMIDA",
    "ESCUELA",
    "DOCTOR",
    "BUENOS DIAS",
    "BUENAS TARDES",
    "BUENAS NOCHES",
    "COMO ESTAS",
    "ME AYUDAS",
    "AYUDA",
    "AGUA",
    "BAÑO",
    "EMERGENCIA"
)

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
