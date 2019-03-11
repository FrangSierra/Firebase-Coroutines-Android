package frangsierra.com.firebase_coroutines.auth

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import frangsierra.com.firebase_coroutines.await

/**
 * Helper method to simplify working with [FirebaseUser.updateProfile] during a [FirebaseUser] update.
 */
suspend fun FirebaseUser.updateProfile(refreshUser: Boolean = true,
                                       request: UserProfileChangeRequest.Builder.() -> Unit) {

    val builder = UserProfileChangeRequest.Builder()
    request(builder)
    this.updateProfile(builder.build()).await()
    if (refreshUser) this.reload().await()
}