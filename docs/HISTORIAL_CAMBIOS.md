# Historial técnico del proyecto

Este documento reconstruye las etapas funcionales del desarrollo para fines de evidencia académica. Los cambios anteriores a la adopción formal de Git se encuentran reunidos en el commit base `eb14be2`; por esa razón, las etapas descritas aquí no representan commits históricos independientes.

## 1. Creación de la aplicación base

Se creó un proyecto Android con Kotlin, Jetpack Compose y Material 3. Se reemplazó la pantalla inicial de ejemplo por una navegación interna con:

- Inicio de sesión.
- Creación de cuenta.
- Pantalla principal del detector.
- Pantalla temporal de entrenamiento.

**Archivos principales:** `MainActivity.kt`, tema Compose y recursos Android.

## 2. Cámara del dispositivo

Se integró CameraX para mostrar video en tiempo real, solicitar permiso de cámara y permitir alternar entre lente frontal y trasera.

También se corrigió el efecto espejo de la cámara frontal para que los puntos dibujados coincidan con la mano visible.

**Tecnologías:** CameraX Camera2, Lifecycle y View.

## 3. Detección de manos con MediaPipe

Se agregó el modelo oficial `hand_landmarker.task` y un helper que:

- Convierte frames CameraX a mapas RGBA.
- Corrige la rotación del dispositivo.
- Procesa video en modo `LIVE_STREAM`.
- Extrae 21 puntos con coordenadas `x`, `y` y `z`.
- Devuelve 63 valores por frame.
- Elimina el overlay cuando ya no hay una mano en cámara.

**Archivos:** `HandLandmarkerHelper.kt`, `hand_landmarker.task`.

## 4. Visualización de landmarks

Se añadió un Canvas sobre la cámara para dibujar:

- Puntos sobre las articulaciones.
- Conexiones entre dedos y palma.
- Reflejo horizontal con la cámara frontal.

La visualización funciona tanto en el detector normal como en entrenamiento.

## 5. Base SQLite para entrenamiento

Se creó `lsm_detector.db` con la tabla `sign_samples`. Cada registro almacena:

- Etiqueta de la seña.
- Tipo `STATIC` o `MOTION`.
- Número de frames.
- Landmarks.
- Fecha de creación.

Las muestras estáticas guardan un frame. Las señas dinámicas guardan una secuencia completa separada por frames.

**Archivo:** `UserDatabaseHelper.kt`.

## 6. Entrenamiento temporal desde el celular

Se incluyeron las letras del alfabeto español, además de `HOLA` y `GRACIAS`.

Para agilizar la captura se implementó:

- Guardado manual de una pose.
- Captura automática de 100 muestras.
- Conteo por etiqueta.
- Grabación de movimientos en secuencias de 30 frames.
- Cancelación cuando MediaPipe pierde la mano.

Las etiquetas dinámicas configuradas son `J`, `K`, `Ñ`, `Q`, `X`, `Z` y `HOLA`.

## 7. Exportación e importación de muestras

Las muestras pueden exportarse como `sign_samples.csv`. El archivo incluido en `assets` se importa automáticamente cuando una instalación nueva no tiene muestras locales.

Esto permite distribuir el reconocedor provisional con datos ya disponibles, sin incluir fotografías ni videos.

## 8. Reconocedor provisional

Se implementó un clasificador por similitud que normaliza landmarks respecto a la muñeca y escala de la mano.

### Señas estáticas

- Compara varios vecinos por etiqueta.
- Reduce sensibilidad a posición y distancia.
- Exige mantener la predicción antes de escribirla.

### Señas dinámicas

- Compara pose y trayectoria.
- Exige movimiento intencional.
- Usa confianza mínima y margen contra la mejor pose estática.
- Requiere varios votos consecutivos.
- Bloquea repeticiones hasta detectar reposo.

Este reconocedor es temporal. El modelo final TFLite queda pendiente hasta recopilar datos de varias personas.

**Archivo:** `SignRecognizer.kt`.

## 9. Construcción de frases

La pantalla principal permite:

- Escribir una letra estática después de mantenerla.
- Escribir una seña dinámica después de confirmar su trayectoria.
- Agregar espacios.
- Borrar el último carácter.
- Limpiar toda la frase.
- Consultar predicción, confianza y progreso de confirmación.

## 10. Autenticación entre dispositivos

La primera versión usaba usuarios SQLite locales. Posteriormente se migró a Firebase Authentication para:

- Crear cuentas con correo y contraseña.
- Usar una cuenta en varios dispositivos.
- Restaurar la sesión automáticamente.
- Guardar el nombre en el perfil Firebase.
- Cerrar sesión de forma explícita.

SQLite permanece reservado para muestras de entrenamiento.

**Archivos:** `FirebaseAuthManager.kt`, `google-services.json`, configuración Gradle y manifiesto.

## 11. Identidad visual y accesibilidad

Se diseñó una interfaz Material 3 con:

- Tonos piel y colores pastel.
- Melocotón como color principal.
- Salvia y lavanda como apoyos.
- Modo claro y oscuro.
- Jerarquía visual para cámara, frase y detección.
- Icono propio con mano y nodos de detección.
- Nombre completo “Lengua de Señas Mexicana”.

## 12. Manual de usuario

Se creó un manual Word que cubre:

- Requisitos e instalación.
- Cuenta Firebase.
- Uso del detector.
- Construcción de frases.
- Entrenamiento estático y dinámico.
- Exportación de datos.
- Solución de problemas.
- Limitaciones actuales.

El manual se genera mediante `docs/build_user_manual.py` para facilitar actualizaciones futuras.

## 13. Verificación aplicada

Durante el desarrollo se ejecutó repetidamente:

```powershell
.\gradlew.bat assembleDebug
```

Las etapas funcionales terminaron con compilaciones `BUILD SUCCESSFUL`. También se probó la instalación sobre dispositivo físico mediante Android Studio y ADB durante el desarrollo.

## Próximas etapas

- Reunir muestras de distintas personas.
- Separar datos de entrenamiento, validación y prueba.
- Entrenar modelos locales para poses y movimientos.
- Evaluar precisión por clase y matriz de confusión.
- Exportar e integrar modelos finales portables.
- Retirar el modo de entrenamiento de la versión destinada a usuarios finales.
