package frangsierra.com.firebase_coroutines.firestore

import com.google.firebase.firestore.*
import frangsierra.com.firebase_coroutines.await
import java.lang.NullPointerException

/**
 * Blocking call over [CollectionReference.get] method joined to
 * cast the generic type of the [QuerySnapshot].
 */
suspend fun <T> CollectionReference.getDocument(path: String,
                                                type: Class<T>,
                                                source: Source = Source.DEFAULT): T {
    val snapshot = this.document(path).get(source).await()
    return snapshot.toObject(type) ?: throw NullPointerException("Document from snapshot item can't be null")
}

/**
 * Blocking call over [Query.get] method joined to
 * cast the generic type of the [QuerySnapshot].
 */
suspend fun <T> CollectionReference.getDocumentOrNull(path: String,
                                                      type: Class<T>,
                                                      source: Source = Source.DEFAULT): T? {
    return this.document(path).get(source).await().toObject(type)
}

/**
 * Blocking call over [Query.get] method joined to
 * cast the generic type of the [QuerySnapshot].
 */
suspend fun <T> Query.getCollection(type: Class<T>,
                                    source: Source = Source.DEFAULT): List<T> {
    return this.get(source).await().toObjects(type)
}

/**
 * Blocking call over [CollectionReference.add] method.
 */
suspend fun CollectionReference.addDocumentBlocking(data: Any) {
    this.add(data).await()
}