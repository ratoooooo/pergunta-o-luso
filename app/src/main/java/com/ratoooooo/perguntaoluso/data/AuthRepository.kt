package com.ratoooooo.perguntaoluso.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class UserInfo(
    val uid: String,
    val email: String?,
    val isAnonymous: Boolean
)

class AuthRepository {

    private val auth get() = FirebaseAuth.getInstance()

    fun currentUserInfo(): UserInfo? = auth.currentUser?.let {
        UserInfo(uid = it.uid, email = it.email, isAnonymous = it.isAnonymous)
    }

    /**
     * Returns the current user, signing in anonymously first if no session exists.
     * Firebase Auth persists the session across app restarts on its own.
     */
    suspend fun ensureSignedIn(): FirebaseUser {
        auth.currentUser?.let { return it }
        return suspendCancellableCoroutine { cont ->
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) cont.resume(user)
                    else cont.resumeWithException(IllegalStateException("Sign-in anónimo sem utilizador"))
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    /**
     * Registers an email/password account.
     * If the current session is anonymous, the account is *linked* to it so the same
     * uid (and therefore all existing scores/profile progress) is preserved.
     * Otherwise a fresh account is created.
     */
    suspend fun registerWithEmail(email: String, password: String): FirebaseUser {
        val current = auth.currentUser
        val credential = EmailAuthProvider.getCredential(email, password)
        return suspendCancellableCoroutine { cont ->
            val task = if (current != null && current.isAnonymous) {
                current.linkWithCredential(credential)
            } else {
                auth.createUserWithEmailAndPassword(email, password)
            }
            task
                .addOnSuccessListener { result ->
                    val user = result.user ?: auth.currentUser
                    if (user != null) cont.resume(user)
                    else cont.resumeWithException(IllegalStateException("Registo sem utilizador"))
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    suspend fun loginWithEmail(email: String, password: String): FirebaseUser {
        return suspendCancellableCoroutine { cont ->
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) cont.resume(user)
                    else cont.resumeWithException(IllegalStateException("Login sem utilizador"))
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    /** Sign out and immediately re-establish an anonymous session so play still works. */
    suspend fun signOutToAnonymous(): FirebaseUser {
        auth.signOut()
        return ensureSignedIn()
    }

    /**
     * Proves the password again for a session that is too old to perform a sensitive operation.
     * Firebase refuses `delete()` with `FirebaseAuthRecentLoginRequiredException` when the sign-in
     * is stale; this is the only way back.
     */
    suspend fun reauthenticateWithPassword(password: String) {
        val user = auth.currentUser ?: error("reauthenticate sem sessão")
        val email = user.email ?: error("reauthenticate numa conta sem e-mail")
        val credential = EmailAuthProvider.getCredential(email, password)
        suspendCancellableCoroutine { cont ->
            user.reauthenticate(credential)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    /**
     * Deletes the Firebase Auth account. Call **only** after the database purge has succeeded —
     * once the account is gone the uid can no longer write anything, so any data left behind
     * would be unreachable forever.
     */
    suspend fun deleteCurrentUser() {
        val user = auth.currentUser ?: error("delete sem sessão")
        suspendCancellableCoroutine { cont ->
            user.delete()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }
}
