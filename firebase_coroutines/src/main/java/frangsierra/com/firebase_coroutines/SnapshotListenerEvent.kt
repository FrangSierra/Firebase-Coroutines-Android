package frangsierra.com.firebase_coroutines

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot

/**
 * Generic type alias that associates a [DocumentChangeType] to a list of [Model] items.
 * This is used to listen to changes on the stores regarding the modified/deleted/added states.
 */
typealias ItemsChangesMap<Model> = Map<DocumentChangeType, List<Model>>

/**
 * Application wrapper over [DocumentChangeType].
 */
enum class DocumentChangeType {
    ADDED, MODIFIED, REMOVED, UNKNOWN;

    companion object {
        /**
         * Maps Firebase [DocumentChange.Type] to [DocumentChangeType].
         */
        fun fromDocumentChange(type: DocumentChange.Type?) =
            when (type) {
                DocumentChange.Type.ADDED -> ADDED
                DocumentChange.Type.MODIFIED -> MODIFIED
                DocumentChange.Type.REMOVED -> REMOVED
                else -> UNKNOWN
            }
    }
}

/**
 * Application wrapper over [DocumentChange] to expose changes on a Firestore document with
 * its related type change.
 */
data class FirestoreDocumentChange<T>(
    val document: FirestoreResultDocument<T>,
    val type: DocumentChangeType = DocumentChangeType.UNKNOWN)

/**
 * Application wrapper over a [DocumentSnapshot] to expose the result model with its metadata.
 */
data class FirestoreResultDocument<T>(val model: T,
                                      val hasPendingWrites: Boolean,
                                      val isFromCache: Boolean) {
    companion object {
        /**
         * Returns a [FirestoreResultDocument] from a [DocumentSnapshot] and a mapping function
         * from a [FirebaseModel] to a [Model].
         */
        inline fun <reified FirebaseModel, Model> fromSnapshot(
            documentSnapshot: DocumentSnapshot,
            mappingFn: ModelMapping<FirebaseModel, Model>,
            documentMapping: (DocumentSnapshot) -> FirebaseModel?): FirestoreResultDocument<Model> {

            if (!documentSnapshot.exists()) {
                throw IllegalArgumentException("Document is empty")
            }
            return FirestoreResultDocument(
                model = mappingFn(documentMapping(documentSnapshot)!!, documentSnapshot.id),
                hasPendingWrites = documentSnapshot.metadata.hasPendingWrites(),
                isFromCache = documentSnapshot.metadata.isFromCache)
        }
    }
}

typealias ModelMapping<FirebaseModel, Model> = (FirebaseModel, String) -> Model

/**
 * Sealed class that wraps the different kind of events that may occur during a [ListenerRegistration] over
 * a [DocumentSnapshot] or a [QuerySnapshot]. These events are inside a Sealed class to be exposed as a single
 * type through [snapshotFlow] and [snapshotChangesFlow].
 *
 * These events can be:
 *
 * - [FirestoreDocumentChanges] -> Event that exposes a list of [FirestoreDocumentChange]. Only occurs when listening to a [QuerySnapshot].
 * - [FirestoreDocument] -> Event that exposes a list of [FirestoreDocument]. Only occurs when listening to a [DocumentSnapshot].
 * - [HasNotPendingWrites] -> Event that notifies when the current [DocumentSnapshot] or [QuerySnapshot] has uploaded all pending writes to the server.
 * - [IsEmpty] -> Event that notifies when the [DocumentSnapshot] or [QuerySnapshot] query was successful but the result data was empty.
 */
sealed class SnapshotListenerEvent<Model> {

    companion object {

        /**
         * Returns a [FirestoreDocument] from a [DocumentSnapshot] and a mapping function from a [FirebaseModel] to a [Model].
         */
        inline fun <reified FirebaseModel, Model> fromSnapshot(
            documentSnapshot: DocumentSnapshot,
            mappingFn: ModelMapping<FirebaseModel, Model>,
            documentMapping: (DocumentSnapshot) -> FirebaseModel?): FirestoreDocument<Model> =
            FirestoreDocument(FirestoreResultDocument.fromSnapshot(documentSnapshot, mappingFn, documentMapping))

        /**
         * Returns a [FirestoreDocumentChanges] from a [QuerySnapshot] and a mapping function from a [FirebaseModel] to a [Model].
         */
        inline fun <reified FirebaseModel, Model> fromSnapshot(
            querySnapshot: QuerySnapshot,
            mappingFn: ModelMapping<FirebaseModel, Model>,
            documentMapping: (DocumentSnapshot) -> FirebaseModel?): FirestoreDocumentChanges<Model> {

            if (querySnapshot.isEmpty
                && querySnapshot.documentChanges.isEmpty()
                && querySnapshot.documents.isEmpty()) {
                throw IllegalArgumentException("Document is empty")
            }

            val documentChanges =
                querySnapshot.documentChanges
                    .map {
                        FirestoreDocumentChange(
                            document = FirestoreResultDocument.fromSnapshot(
                                documentSnapshot = it.document,
                                mappingFn = mappingFn,
                                documentMapping = documentMapping
                            ),
                            type = DocumentChangeType.fromDocumentChange(it.type)
                        )
                    }
            return FirestoreDocumentChanges(documentChanges)
        }
    }

    /**
     *  Event that exposes a list of [FirestoreDocumentChange]. Only occurs when listening to
     *  a [QuerySnapshot].
     */
    data class FirestoreDocumentChanges<Model>(val changes: List<FirestoreDocumentChange<Model>>)
        : SnapshotListenerEvent<Model>()

    /**
     * Event that exposes a [FirestoreDocument]. Only occurs when listening to a [DocumentSnapshot].
     */
    data class FirestoreDocument<Model>(val document: FirestoreResultDocument<Model>)
        : SnapshotListenerEvent<Model>()

    /**
     *  Event that notifies when the current [DocumentSnapshot] or [QuerySnapshot]
     *  has uploaded all pending writes to the server.
     */
    class HasNotPendingWrites<Model> : SnapshotListenerEvent<Model>()

    /**
     * Event that notifies when the [DocumentSnapshot] or [QuerySnapshot] query
     * was successful but the result data was empty.
     */
    class IsEmpty<Model> : SnapshotListenerEvent<Model>()
}