# Lengua de Señas Mexicana para Android

Aplicación Android desarrollada con Kotlin y Jetpack Compose para detectar puntos de la mano, reconocer señas de la Lengua de Señas Mexicana y construir frases en tiempo real.

## Funciones actuales

- Registro e inicio de sesión con Firebase Authentication.
- Cámara frontal y trasera mediante CameraX.
- Detección de 21 puntos de la mano con MediaPipe Hand Landmarker.
- Visualización de puntos y conexiones sobre la cámara.
- Reconocimiento provisional de letras estáticas y señas dinámicas.
- Construcción de frases con confirmación temporal.
- Modo temporal de entrenamiento desde el teléfono.
- Captura automática de muestras estáticas y secuencias de movimiento.
- Almacenamiento local SQLite y exportación CSV.
- Importación de muestras incluidas en el APK.
- Manual de usuario en Word.

## Tecnologías

- Kotlin
- Jetpack Compose y Material 3
- CameraX
- MediaPipe Tasks Vision
- SQLite
- Firebase Authentication
- Gradle Kotlin DSL

## Estructura principal

```text
app/src/main/java/com/example/lsmdetector/
├── MainActivity.kt
├── FirebaseAuthManager.kt
├── HandLandmarkerHelper.kt
├── SignRecognizer.kt
└── UserDatabaseHelper.kt
```

## Compilar

Abre la carpeta que contiene `settings.gradle.kts` en Android Studio, sincroniza Gradle y ejecuta la configuración `app` sobre un dispositivo Android.

```powershell
.\gradlew.bat assembleDebug
```

## Documentación

- [Historial técnico del desarrollo](docs/HISTORIAL_CAMBIOS.md)
- [Evidencia de tareas asignadas a Samuel](docs/TAREAS_SAMUEL.md)
- [Validación de tareas asignadas a JC](docs/VALIDACION_TAREAS_JC.md)
- [Manual de usuario](docs/Manual_de_usuario_Lengua_de_Senas_Mexicana.docx)

## Estado del proyecto

El reconocimiento actual compara landmarks contra muestras guardadas. Queda pendiente entrenar e integrar modelos finales portables cuando se reúna un conjunto de datos más diverso.
