package frangsierra.com.firebase_coroutines.firestore

import com.google.firebase.firestore.*
import frangsierra.com.firebase_coroutines.await
import kotlinx.coroutines.channels.Channel

/**
 * Blocking call over [DocumentReference.collection] method joined to
 * cast the generic type of the [DocumentSnapshot].
 */
suspend fun <T> DocumentReference.getCollection(path: String,
                                                type: Class<T>,
                                                source: Source = Source.DEFAULT): List<T> {
    return this.collection(path).get(source).await().toObjects(type)
}

/**
 * Blocking call over [DocumentReference.set] method.
 */
suspend fun DocumentReference.setValueBlocking(data: Any) {
    this.set(data).await()
}

/**
 * Blocking call over [DocumentReference.set] method.
 */
suspend fun DocumentReference.setValueBlocking(data: Any,
                                               setOptions: SetOptions) {
    this.set(data, setOptions).await()
}

/**
 * Blocking call over [DocumentReference.update] method.
 */
suspend fun DocumentReference.updateFieldBlocking(field: String,
                                                  value: Any) {
    this.update(field, value).await()
}

/**
 * Blocking call over [DocumentReference.update] method.
 */
suspend fun DocumentReference.updateBlocking(data: Map<String, Any>) {
    this.update(data).await()
}

/**
 * Blocking call over [DocumentReference.delete] method.
 */
suspend fun DocumentReference.deleteBlocking() {
    this.delete().await()
}

/**
 * Returns a [Channel] with unlimited capacity that will send a new message every time that there is a change
 * inside the [DocumentReference]. The listeners will be canceled together with the channel closing.
 */
fun <T> DocumentReference.snapshotChangesChannel(type: Class<T>,
                                                 closeOnError: Boolean = true,
                                                 channelCapacity: Int = Channel.UNLIMITED) {
    val channel = Channel<T>(channelCapacity)
    val listener = EventListener<DocumentSnapshot> { data, exception ->
        if (closeOnError && exception != null) {
            channel.close(exception)
            return@EventListener
        }
        data?.toObject(type)?.let { channel.offer(it) }
    }
    val listenerRegistration = addSnapshotListener(listener)
    channel.invokeOnClose { listenerRegistration.remove() }
}