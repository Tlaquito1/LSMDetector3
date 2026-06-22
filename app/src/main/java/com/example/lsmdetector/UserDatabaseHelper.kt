package com.example.lsmdetector

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SignSampleCount(
    val label: String,
    val count: Int
)

data class SignSample(
    val id: Long,
    val label: String,
    val landmarks: String,
    val sampleType: String,
    val frameCount: Int,
    val createdAt: Long
)

/**
 * Base SQLite local para las muestras de entrenamiento.
 *
 * Las cuentas activas ya no dependen de esta clase: Firebase Authentication
 * administra usuarios y contraseñas entre dispositivos.
 */
class UserDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        createUsersTable(db)
        createSignSamplesTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Las migraciones agregan campos sin eliminar muestras ya capturadas.
        if (oldVersion < 2) {
            createSignSamplesTable(db)
        } else if (oldVersion < 3) {
            db.execSQL(
                "ALTER TABLE $TABLE_SIGN_SAMPLES ADD COLUMN $COLUMN_SAMPLE_TYPE TEXT NOT NULL DEFAULT '$TYPE_STATIC'"
            )
            db.execSQL(
                "ALTER TABLE $TABLE_SIGN_SAMPLES ADD COLUMN $COLUMN_FRAME_COUNT INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    // Funciones heredadas de la primera versión local. La UI actual usa Firebase.
    fun createUser(name: String, email: String, password: String): Boolean {
        if (emailExists(email)) return false

        val values = ContentValues().apply {
            put(COLUMN_NAME, name.trim())
            put(COLUMN_EMAIL, email.trim().lowercase())
            put(COLUMN_PASSWORD, password)
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
        }

        return writableDatabase.insert(TABLE_USERS, null, values) != -1L
    }

    fun validateUser(email: String, password: String): Boolean {
        val cursor = readableDatabase.query(
            TABLE_USERS,
            arrayOf(COLUMN_ID),
            "$COLUMN_EMAIL = ? AND $COLUMN_PASSWORD = ?",
            arrayOf(email.trim().lowercase(), password),
            null,
            null,
            null
        )

        cursor.use {
            return it.moveToFirst()
        }
    }

    fun getUserName(email: String): String? {
        val cursor = readableDatabase.query(
            TABLE_USERS,
            arrayOf(COLUMN_NAME),
            "$COLUMN_EMAIL = ?",
            arrayOf(email.trim().lowercase()),
            null,
            null,
            null
        )

        cursor.use {
            return if (it.moveToFirst()) {
                it.getString(it.getColumnIndexOrThrow(COLUMN_NAME))
            } else {
                null
            }
        }
    }

    fun saveSignSample(label: String, landmarks: String): Boolean {
        // Una pose estática contiene exactamente un frame de 63 valores.
        return saveSample(label, landmarks, TYPE_STATIC, 1)
    }

    fun saveSignSequence(label: String, frames: List<String>): Boolean {
        if (frames.isEmpty()) return false
        // Una seña dinámica guarda todos sus frames como un solo ejemplo.
        return saveSample(
            label = label,
            landmarks = frames.joinToString(FRAME_SEPARATOR),
            sampleType = TYPE_MOTION,
            frameCount = frames.size
        )
    }

    private fun saveSample(
        label: String,
        landmarks: String,
        sampleType: String,
        frameCount: Int
    ): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_SAMPLE_LABEL, label)
            put(COLUMN_SAMPLE_LANDMARKS, landmarks)
            put(COLUMN_SAMPLE_TYPE, sampleType)
            put(COLUMN_FRAME_COUNT, frameCount)
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
        }

        return writableDatabase.insert(TABLE_SIGN_SAMPLES, null, values) != -1L
    }

    fun getSignSampleCounts(): List<SignSampleCount> {
        val counts = mutableListOf<SignSampleCount>()
        val cursor = readableDatabase.rawQuery(
            """
            SELECT $COLUMN_SAMPLE_LABEL, COUNT(*) AS sample_count
            FROM $TABLE_SIGN_SAMPLES
            GROUP BY $COLUMN_SAMPLE_LABEL
            """.trimIndent(),
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                counts += SignSampleCount(
                    label = it.getString(it.getColumnIndexOrThrow(COLUMN_SAMPLE_LABEL)),
                    count = it.getInt(it.getColumnIndexOrThrow("sample_count"))
                )
            }
        }

        return counts
    }

    fun getAllSignSamples(): List<SignSample> {
        val samples = mutableListOf<SignSample>()
        val cursor = readableDatabase.query(
            TABLE_SIGN_SAMPLES,
            arrayOf(
                COLUMN_ID,
                COLUMN_SAMPLE_LABEL,
                COLUMN_SAMPLE_LANDMARKS,
                COLUMN_SAMPLE_TYPE,
                COLUMN_FRAME_COUNT,
                COLUMN_CREATED_AT
            ),
            null,
            null,
            null,
            null,
            "$COLUMN_CREATED_AT ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                samples += SignSample(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    label = it.getString(it.getColumnIndexOrThrow(COLUMN_SAMPLE_LABEL)),
                    landmarks = it.getString(it.getColumnIndexOrThrow(COLUMN_SAMPLE_LANDMARKS)),
                    sampleType = it.getString(it.getColumnIndexOrThrow(COLUMN_SAMPLE_TYPE)),
                    frameCount = it.getInt(it.getColumnIndexOrThrow(COLUMN_FRAME_COUNT)),
                    createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT))
                )
            }
        }

        return samples
    }

    fun importBundledSamplesIfEmpty(context: Context): Int {
        // En una instalación nueva importa el CSV incluido en assets. De esta
        // forma otro celular recibe las muestras con las que se creó el APK.
        val currentCount = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_SIGN_SAMPLES",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        if (currentCount > 0) return 0

        val database = writableDatabase
        var importedCount = 0
        // La transacción evita dejar una importación incompleta.
        database.beginTransaction()
        try {
            context.assets.open(BUNDLED_SAMPLES_FILE).bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val columns = parseCsvLine(line)
                    if (columns.size >= 6) {
                        val values = ContentValues().apply {
                            put(COLUMN_SAMPLE_LABEL, columns[1])
                            put(COLUMN_SAMPLE_TYPE, columns[2])
                            put(COLUMN_FRAME_COUNT, columns[3].toIntOrNull() ?: 1)
                            put(COLUMN_SAMPLE_LANDMARKS, columns[4])
                            put(
                                COLUMN_CREATED_AT,
                                columns[5].toLongOrNull() ?: System.currentTimeMillis()
                            )
                        }
                        if (database.insert(TABLE_SIGN_SAMPLES, null, values) != -1L) {
                            importedCount += 1
                        }
                    }
                }
            }
            database.setTransactionSuccessful()
        } catch (_: Exception) {
            importedCount = 0
        } finally {
            database.endTransaction()
        }
        return importedCount
    }

    private fun parseCsvLine(line: String): List<String> {
        // Parser pequeño que respeta comas y comillas dentro de landmarks.
        val columns = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false
        var index = 0

        while (index < line.length) {
            val character = line[index]
            when {
                character == '"' && insideQuotes &&
                    index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }

                character == '"' -> insideQuotes = !insideQuotes
                character == ',' && !insideQuotes -> {
                    columns += current.toString()
                    current.clear()
                }

                else -> current.append(character)
            }
            index += 1
        }
        columns += current.toString()
        return columns
    }

    private fun emailExists(email: String): Boolean {
        val cursor = readableDatabase.query(
            TABLE_USERS,
            arrayOf(COLUMN_ID),
            "$COLUMN_EMAIL = ?",
            arrayOf(email.trim().lowercase()),
            null,
            null,
            null
        )

        cursor.use {
            return it.moveToFirst()
        }
    }

    private fun createUsersTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_USERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_EMAIL TEXT NOT NULL UNIQUE,
                $COLUMN_PASSWORD TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createSignSamplesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SIGN_SAMPLES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SAMPLE_LABEL TEXT NOT NULL,
                $COLUMN_SAMPLE_LANDMARKS TEXT NOT NULL,
                $COLUMN_SAMPLE_TYPE TEXT NOT NULL DEFAULT '$TYPE_STATIC',
                $COLUMN_FRAME_COUNT INTEGER NOT NULL DEFAULT 1,
                $COLUMN_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    companion object {
        private const val DATABASE_NAME = "lsm_detector.db"
        private const val DATABASE_VERSION = 3

        private const val TABLE_USERS = "users"
        private const val TABLE_SIGN_SAMPLES = "sign_samples"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PASSWORD = "password"
        private const val COLUMN_SAMPLE_LABEL = "label"
        private const val COLUMN_SAMPLE_LANDMARKS = "landmarks"
        private const val COLUMN_SAMPLE_TYPE = "sample_type"
        private const val COLUMN_FRAME_COUNT = "frame_count"
        private const val COLUMN_CREATED_AT = "created_at"

        private const val TYPE_STATIC = "STATIC"
        private const val TYPE_MOTION = "MOTION"
        private const val FRAME_SEPARATOR = "|"
        private const val BUNDLED_SAMPLES_FILE = "sign_samples.csv"
    }
}
