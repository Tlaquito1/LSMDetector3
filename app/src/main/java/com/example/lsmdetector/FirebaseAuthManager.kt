package com.example.lsmdetector

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

data class AuthenticatedUser(
    val email: String,
    val name: String
)

/**
 * Punto central de acceso a Firebase Authentication.
 *
 * Firebase guarda las cuentas y sesiones en la nube, por lo que un usuario
 * puede iniciar sesión con las mismas credenciales desde distintos celulares.
 */
class FirebaseAuthManager {
    // Firebase conserva internamente la sesión hasta que se llama signOut().
    private val auth = FirebaseAuth.getInstance()

    /** Devuelve la sesión que Firebase restauró al abrir la aplicación. */
    fun currentUser(): AuthenticatedUser? {
        val user = auth.currentUser ?: return null
        val email = user.email ?: return null
        return AuthenticatedUser(
            email = email,
            name = user.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
        )
    }

    fun login(
        email: String,
        password: String,
        onResult: (Result<AuthenticatedUser>) -> Unit
    ) {
        // La contraseña se envía a Firebase; nunca se guarda en SQLite.
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                val user = currentUser()
                if (user != null) {
                    onResult(Result.success(user))
                } else {
                    onResult(Result.failure(IllegalStateException("No se pudo obtener el usuario.")))
                }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    fun register(
        name: String,
        email: String,
        password: String,
        onResult: (Result<AuthenticatedUser>) -> Unit
    ) {
        // Primero se crea la cuenta remota con correo y contraseña.
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    onResult(Result.failure(IllegalStateException("No se pudo crear el usuario.")))
                    return@addOnSuccessListener
                }

                // El nombre se guarda en el perfil de Firebase para recuperarlo
                // al iniciar sesión desde otro dispositivo.
                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(name.trim())
                    .build()
                firebaseUser.updateProfile(profile)
                    .addOnSuccessListener {
                        onResult(
                            Result.success(
                                AuthenticatedUser(
                                    email = firebaseUser.email.orEmpty(),
                                    name = name.trim()
                                )
                            )
                        )
                    }
                    .addOnFailureListener { error ->
                        onResult(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    fun logout() {
        // Elimina la sesión de Firebase en este dispositivo.
        auth.signOut()
    }
}
