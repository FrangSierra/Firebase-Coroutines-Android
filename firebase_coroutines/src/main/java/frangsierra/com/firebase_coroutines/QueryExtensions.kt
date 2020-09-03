package frangsierra.com.firebase_coroutines

import android.annotation.SuppressLint
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import java.nio.channels.ClosedChannelException

/**
 * Creates a [Flow] that will emit [SnapshotListenerEvent] event.
 * Flow calls works as cold observables, they are create and only start working after [Flow.collect]
 * is called.
 * [Flow] can't be cancelled, the only way to dispose them is to cancel the [Job] where the
 * [Flow.collect] was called.
 *
 * This specific case is using a [Channel] as a flow to be able to use the [Channel.invokeOnClose]
 * method to remove the listener when this [Flow] is canceled.
 *
 * If [skipFirstCacheHit] is set to true, the values recovered on the first cache hit will be saved until another
 * emission coming from the server is received so we can emit them together.
 */
inline fun <reified FirebaseModel, Model> Query.snapshotChangesFlow(
    skipFirstCacheHit: Boolean,
    crossinline documentMapping: (DocumentSnapshot) -> FirebaseModel? =
        { it.toObject(FirebaseModel::class.java) },
    crossinline mappingFn: (FirebaseModel, String) -> Model): Flow<SnapshotListenerEvent<Model>> {

    return callbackFlow {
        var firstCacheHitReceived = false
        var cachedDataToOffer: SnapshotListenerEvent.FirestoreDocumentChanges<Model>? = null

        val listener =
            this@snapshotChangesFlow.addSnapshotListener(
                MetadataChanges.INCLUDE,
                EventListener<QuerySnapshot?> { querySnapshot, error ->
                    if (error != null) {
                        close(error)
                        return@EventListener // TODO do we want to close and return?
                    }

                    querySnapshot?.let {
                        //isEmpty represents the documents in the collection, but not the changes made.
                        //We need to check both to avoid skip REMOVED items when there is none left.
                        if (!it.isEmpty || it.documentChanges.isNotEmpty()) {
                            val dataToOffer = SnapshotListenerEvent.fromSnapshot(it, mappingFn, documentMapping)

                            if (skipFirstCacheHit && !firstCacheHitReceived && it.metadata.isFromCache) {
                                firstCacheHitReceived = true
                                cachedDataToOffer = dataToOffer
                            } else {
                                var newDataToOffer = dataToOffer
                                if (cachedDataToOffer != null) {
                                    // Combine cached data with new changes if available
                                    newDataToOffer = SnapshotListenerEvent.FirestoreDocumentChanges(
                                        cachedDataToOffer!!.changes.plus(dataToOffer.changes))

                                    cachedDataToOffer = null
                                }
                                channel.offer(newDataToOffer)
                            }
                        } else {
                            channel.offer(SnapshotListenerEvent.IsEmpty<Model>())
                        }

                        if (!it.metadata.hasPendingWrites()) {
                            channel.offer(SnapshotListenerEvent.HasNotPendingWrites<Model>())
                        }
                    }
                })
        awaitClose { listener.remove() }
    }

}


/**
 * Returns a map representing the quantity of changes that a given [SnapshotListenerEvent.FirestoreDocumentChanges]
 * contains.
 *
 * Used for debug purposes.
 */
fun <Model> SnapshotListenerEvent.FirestoreDocumentChanges<Model>.changesSummary() =
    this.changes.groupBy { it.type }.mapValues { (_, value) -> value.size }

/**
 * Creates a [Flow] to filter [SnapshotListenerEvent.FirestoreDocumentChanges]
 * coming from [SnapshotListenerEvent] events to produce a list of [Model] typed values.
 * Flow calls works as cold observables, they are create and only start working after
 * [Flow.collect] is called.
 *
 * [Flow] can't be cancelled, the only way to dispose them is to cancel the [Job] where the
 * [Flow.collect] was called.
 *
 * This specific case is using a [Channel] as a flow to be able to use the [Channel.invokeOnClose]
 * method to remove the listener when this [Flow] is canceled.
 *
 * If [skipFirstCacheHit] is set to true, the values recovered on the first cache hit will be saved until another
 * emission coming from the server is received so we can emit them together.
 */
inline fun <reified FirebaseModel, Model> Query.snapshotFlow(
    skipFirstCacheHit: Boolean,
    crossinline mappingFn: (FirebaseModel, String) -> Model): Flow<List<Model>> {
    val modelFlow: Flow<SnapshotListenerEvent.FirestoreDocumentChanges<Model>> =
        snapshotChangesFlow(
            skipFirstCacheHit = skipFirstCacheHit,
            mappingFn = mappingFn).filterIsInstance()
    return modelFlow.map { it.changes.map { change -> change.document.model } }
}

/**
 * Creates a [Flow] that will emit [SnapshotListenerEvent] events.
 * Flow calls work as cold observables, they are created and only start working after [Flow.collect]
 * is called.
 *
 * [Flow] can't be cancelled , the only way to dispose them is to cancel the [Job] where the
 * [Flow.collect] was called.
 *
 * This specific case is using a [Channel] as a flow to be able to use the [Channel.invokeOnClose]
 * method to remove the listener when this [Flow] is canceled.
 */
inline fun <reified FirebaseModel, Model> DocumentReference.snapshotFlow(
    crossinline documentMapping: (DocumentSnapshot) -> FirebaseModel? =
        { it.toObject(FirebaseModel::class.java) },
    crossinline mappingFn: (FirebaseModel, String) -> Model,
    includeMetadata: Boolean = true): Flow<SnapshotListenerEvent<Model>> =
    callbackFlow {
        val metadataChanges =
            if (includeMetadata) MetadataChanges.INCLUDE else MetadataChanges.EXCLUDE
        val listener =
            this@snapshotFlow.addSnapshotListener(metadataChanges,
                EventListener<DocumentSnapshot?> { documentSnapshot, error ->

                    if (error != null) {
                        channel.close(error)
                        return@EventListener // TODO do we want to close and return?
                    }

                    documentSnapshot?.let {
                        if (it.exists()) {
                            channel.offer(SnapshotListenerEvent.fromSnapshot(it, mappingFn, documentMapping))
                        } else {
                            channel.offer(SnapshotListenerEvent.IsEmpty<Model>())
                        }

                        if (includeMetadata && !it.metadata.hasPendingWrites()) {
                            channel.offer(SnapshotListenerEvent.HasNotPendingWrites<Model>())
                        }

                    }
                })
        awaitClose { listener.remove() }
    }

/**
 * Creates a [Channel] that offers all the changes inside the current [DocumentReference] and map
 * it to the provided field given by [fieldName]. The [ListenerRegistration] will be removed when
 * the channel closes or an error happens.
 *
 * This method should not be used outside of [onFieldUpdated] and [onFieldUpdatedOrNull].
 * For cases where we would like to receive all the changes of a specific field we should use
 * [snapshotFlow] with a mapping clause.
 */
inline fun <reified T> DocumentReference.documentFieldChannel(fieldName: String): ReceiveChannel<T?> {
    val channel = Channel<T?>()
    val listener: ListenerRegistration = this.addSnapshotListener(
        EventListener<DocumentSnapshot?> { snapshot, error ->
            error?.let { channel.close(error) }

            val value = snapshot?.get(fieldName, T::class.java)
            channel.offer(value)
        })
    channel.invokeOnClose { listener.remove() }
    return channel
}

/**
 * Creates a [CompletableDeferred] that will wait until the given [fieldName] changes to a new
 * nullable value.
 *
 * If an error happens while waiting for the result, the [CompletableDeferred] will complete
 * with a null value.
 */
suspend inline fun <reified T> DocumentReference.onFieldUpdatedOrNull(
    fieldName: String,
    coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO): CompletableDeferred<T?> {

    val deferred = CompletableDeferred<T?>()
    val docChannel: ReceiveChannel<T?> = documentFieldChannel(fieldName)
    deferred.invokeOnCompletion { docChannel.cancel() }

    withContext(coroutineDispatcher) {
        while (!docChannel.isClosedForReceive) {
            val value = docChannel.receiveOrClosed()
            if (value.isClosed) {
                deferred.complete(null)
            } else {
                deferred.complete(value.value)
            }
        }
    }
    return deferred
}

/**
 * Creates a [CompletableDeferred] with a timeout that will wait until the given [fieldName]
 * changes to a new nullable value or the timeout expires.
 *
 * If an error happens while waiting for the result, the [CompletableDeferred] will complete
 * exceptionally with the given error.
 *
 * If the timeout expires, a [TimeoutCancellationException] will be throw.
 */
suspend inline fun <reified T> DocumentReference.onFieldUpdatedOrNull(fieldName: String,
                                                                      timeOutMillis: Long): T? =
    withTimeout(timeOutMillis) { return@withTimeout onFieldUpdatedOrNull<T>(fieldName).await() }

/**
 * Creates a [CompletableDeferred] that will wait until the given [fieldName] changes to a new
 * non nullable value.
 *
 * If an error happens while waiting for the result, the [CompletableDeferred] will complete
 * exceptionally with the given error.
 */
suspend inline fun <reified T> DocumentReference.onFieldUpdated(
    fieldName: String,
    coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO): CompletableDeferred<T> {

    val deferred = CompletableDeferred<T>()
    val docChannel = documentFieldChannel<T?>(fieldName)
    deferred.invokeOnCompletion { docChannel.cancel() }

    withContext(coroutineDispatcher) {
        while (!docChannel.isClosedForReceive) {
            val value = docChannel.receiveOrClosed()
            if (value.isClosed) {
                deferred.completeExceptionally(value.closeCause ?: ClosedChannelException())
            } else {
                value.value?.let { deferred.complete(it) }
            }
        }
    }

    return deferred
}

/**
 * Creates a [CompletableDeferred] with a timeout that will wait until the given [fieldName]
 * changes to a new non nullable value or the timeout expires.
 *
 * If an error happens while waiting for the result, the [CompletableDeferred] will complete
 * exceptionally with the given error.
 *
 * If the timeout expires, a [TimeoutCancellationException] will be throw.
 */
suspend inline fun <reified T> DocumentReference.onFieldUpdated(fieldName: String,
                                                                timeOutMillis: Long): T =
    withTimeout(timeOutMillis) { return@withTimeout onFieldUpdated<T>(fieldName).await() }

