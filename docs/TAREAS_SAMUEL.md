# Evidencia de tareas asignadas a Samuel

Este documento resume las tareas realizadas por Samuel Ibarra Gonzalez para el
proyecto LSMDetector3. Sirve como evidencia rapida para ClickUp y para la
revision del repositorio.

## 1. Definir objetivo y alcance del detector LSM

Se definio que la aplicacion tendria como objetivo detectar puntos de la mano,
reconocer senas de la Lengua de Senas Mexicana y construir frases en tiempo
real. El alcance quedo limitado a un reconocedor provisional basado en muestras,
dejando como mejora futura el entrenamiento de un modelo final portable.

## 2. Crear proyecto Android base

Se preparo el proyecto Android con Kotlin, Jetpack Compose, Material 3 y Gradle
Kotlin DSL. La estructura principal quedo organizada en el paquete
`com.example.lsmdetector`, separando actividad principal, autenticacion,
deteccion de mano, reconocimiento y base de datos.

## 3. Disenar navegacion principal de la app

Se organizo la navegacion de la aplicacion alrededor de inicio de sesion,
registro, detector principal y entrenamiento temporal. Esto permite que el
usuario entre con cuenta, use la camara y capture muestras desde el telefono.

## 4. Integrar Firebase Authentication

Se integro Firebase Authentication para registro, inicio de sesion, sesion
persistente, nombre de perfil y cierre de sesion. La base SQLite local se dejo
para muestras de entrenamiento, no para la cuenta activa del usuario.

## 5. Configurar archivo google-services

Se agrego la configuracion necesaria de Google Services para conectar el modulo
Android con Firebase. Tambien se dejo documentado en Gradle que el plugin de
Google Services conecta el modulo con el proyecto configurado.

## 6. Integrar MediaPipe Hand Landmarker

Se incorporo el modelo `hand_landmarker.task` y el helper encargado de procesar
frames en tiempo real. La deteccion trabaja con los 21 puntos de la mano y se
usa como base para el overlay, entrenamiento y reconocimiento.

## 7. Extraer landmarks de la mano

Se establecio el formato de 63 valores por frame: 21 puntos con coordenadas
`x`, `y` y `z`. Ese formato se reutiliza para guardar muestras estaticas,
secuencias dinamicas y comparar senas.

## 8. Crear base de datos SQLite

Se creo la tabla `sign_samples` para almacenar etiqueta, tipo de muestra,
cantidad de frames, landmarks y fecha de creacion. La clase
`UserDatabaseHelper` centraliza guardado, lectura, importacion y conteos.

## 9. Implementar entrenamiento estatico

Se habilito el guardado de poses estaticas como muestras de un solo frame. Esto
permite capturar letras y consultar conteos por etiqueta para saber cuantas
muestras existen de cada sena.

## 10. Agregar captura automatica de muestras

Se agrego el flujo de captura automatica para generar varias muestras desde el
telefono y detener la captura cuando se pierde la mano. Esto acelera la
creacion de datos para el reconocedor provisional.

## 11. Importar muestras incluidas en APK

Se incorporo `sign_samples.csv` dentro de assets para que una instalacion nueva
pueda cargar muestras iniciales automaticamente cuando la base local esta vacia.
La importacion se ejecuta en transaccion para evitar datos incompletos.

## 12. Implementar reconocedor provisional

Se implemento `SignRecognizer`, un clasificador por similitud que normaliza los
landmarks respecto a la muneca y escala de la mano. El objetivo fue validar el
flujo completo antes de entrenar un modelo final.

## 13. Validar senas dinamicas

Se agrego comparacion por trayectoria y pose para senas dinamicas. El
reconocedor exige movimiento intencional, compara secuencias y evita activar
senas dinamicas cuando la mano esta casi inmovil.

## 14. Probar compilacion debug

Se valido la compilacion de la aplicacion con la tarea `assembleDebug` durante
el desarrollo. Esta revision confirma que Gradle, dependencias y recursos
principales se integran correctamente.

## 15. Documentar historial tecnico

Se documento el historial tecnico del proyecto en `docs/HISTORIAL_CAMBIOS.md`,
incluyendo decisiones de arquitectura, tecnologias, limitaciones actuales y
proximas etapas.
