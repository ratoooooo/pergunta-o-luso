package com.starforge.app.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Simple online-presence tracker. Each connected client writes `/presenca/{uid} = true`,
 * with an `onDisconnect().removeValue()` that the RTDB server runs automatically when the
 * socket drops (same mechanism used for player desistência in the multiplayer rooms).
 *
 * "A jogar agora" = **any player with the app open** (a live socket), not only players inside
 * an active match — distinguishing "in game" would need extra shared state. Documented in
 * GAME_DESIGN.md. The Início counter reads the child count in real time.
 */
class PresenceRepository {

    private val db get() = FirebaseDatabase.getInstance()
    private fun presenca() = db.getReference("presenca")

    private var connectedListener: ValueEventListener? = null

    /**
     * Marks [uid] present and keeps it present across reconnects. Listening on
     * `.info/connected` re-arms the `onDisconnect` and re-writes presence every time the
     * client (re)connects — a dropped-then-restored socket would otherwise lose its entry.
     */
    fun goOnline(uid: String) {
        val userRef = presenca().child(uid)
        val connectedRef = db.getReference(".info/connected")
        connectedListener?.let { connectedRef.removeEventListener(it) }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    userRef.onDisconnect().removeValue()
                    userRef.setValue(true)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        connectedListener = listener
        connectedRef.addValueEventListener(listener)
    }

    /** Explicitly leave (e.g. sign-out / process teardown) and stop re-arming. */
    fun goOffline(uid: String) {
        connectedListener?.let { db.getReference(".info/connected").removeEventListener(it) }
        connectedListener = null
        presenca().child(uid).removeValue()
    }

    /** Live set of uids currently present — used to gate the "Desafiar" button on friends. */
    fun observeOnlineUids(): Flow<Set<String>> = callbackFlow {
        val ref = presenca()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.key }.toSet())
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Live count of players currently present (children of `/presenca`). */
    fun observeCount(): Flow<Int> = callbackFlow {
        val ref = presenca()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot.childrenCount.toInt()) }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
