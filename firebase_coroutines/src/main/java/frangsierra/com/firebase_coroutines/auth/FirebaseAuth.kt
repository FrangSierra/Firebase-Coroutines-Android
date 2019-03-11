package frangsierra.com.firebase_coroutines.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.Channel


/**
 * Returns a [Channel] with conflated capacity that will send a new message every time that there is a change
 * on the authentication user over the current [FirebaseAuth] instance.
 *
 * The listeners will be canceled together with the channel closing.
 */
fun FirebaseAuth.authStateChannel(): Channel<FirebaseAuth> {
    val channel = Channel<FirebaseAuth>(Channel.CONFLATED)
    val authListener = FirebaseAuth.AuthStateListener { channel.offer(it) }
    this.addAuthStateListener(authListener)
    channel.invokeOnClose { this.removeAuthStateListener(authListener) }
    return channel
}

/**
 * Returns a [Channel] with conflated capacity that will send a new message every time that there is a change
 * on the authentication id token over the current [FirebaseAuth] instance.
 *
 * The listeners will be canceled together with the channel closing.
 */
fun FirebaseAuth.idTokenChannel(): Channel<FirebaseAuth> {
    val channel = Channel<FirebaseAuth>(Channel.CONFLATED)
    val authListener = FirebaseAuth.IdTokenListener { channel.offer(it) }
    this.addIdTokenListener(authListener)
    channel.invokeOnClose { this.removeIdTokenListener(authListener) }
    return channel
}

/**
 * Updates the current [FirebaseUser] inside the current [FirebaseAuth] instance.
 */
suspend fun FirebaseAuth.updateCurrentUserProfile(refreshUser: Boolean = true,
                                                  request: UserProfileChangeRequest.Builder.() -> Unit): FirebaseUser {
    if (this.currentUser == null) {
        throw NullPointerException("There is no user logged on this Firebase Auth instance")
    }
    this.currentUser!!.updateProfile(refreshUser, request)
    return this.currentUser!! //This would be already the updated instance
}