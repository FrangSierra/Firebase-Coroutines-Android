package frangsierra.com.firebase_coroutines.storage

import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import frangsierra.com.firebase_coroutines.await

/**
 * Helper method to simplify working with [StorageMetadata.Builder] during a [StorageMetadata] update.
 */
suspend fun StorageReference.updateMetadata(request: StorageMetadata.Builder.() -> Unit) {

    val builder = StorageMetadata.Builder()
    request(builder)
    this.updateMetadata(builder.build()).await()
}

/**
 * Blocking call for [StorageReference.delete] method.
 */
suspend fun StorageReference.deleteBlocking() {
    this.delete().await()
}