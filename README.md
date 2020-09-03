# Firebase-Coroutines-Android
Kotlin Coroutines helpers for Firebase Library

Extension functions:

### Google Play API

| **Name** | **Description**
| -------- | ---------------
| [Task.await] | Awaits for completion of the Task and return the result
| [Task.asDeferred] | Converts the Task in a the instance of Deferred
| [Deferred.asTask] | Converts the Deferred in a the instance of Task

### Firebase Firestore

| **Name** | **Description**
| -------- | ---------------
| [CollectionReference.getDocument] | Awaits for the document and returns it casted to the desired type. Throws exception over null values
| [CollectionReference.getDocumentOrNull] | Awaits for the document and returns it casted to the desired type
| [CollectionReference.addDocumentBlocking] | Blocking call over the `add` document method
| [Query.getCollection] | Awaits for the collection and returns it casted to a list of the  desired type
| [DocumentReference.getCollection] | Awaits for the collection and returns it casted to a list of the  desired type
| [DocumentReference.setValueBlocking] | Awaits for the given value to be set on the current reference
| [DocumentReference.updateFieldBlocking] | Awaits for the given field over the current reference to be updated
| [DocumentReference.updateBlocking] | Awaits for the given fields over the current reference to be updated
| [DocumentReference.deleteBlocking] | Awaits for the current reference to be deleted
| [DocumentReference.snapshotChangesChannel] | Returns a `Channel` with unlimited capacity that will send a new message every time that there is a change on the current reference

## Flow Utilities
This library includes extensions to provide compatibility with `flow` while using Firestore snapshot listeners. The extensions are ready to take into account
the possibility of offline and cached data, together with the casting from Firebase model to application models. Example:
```kotlin
scope.launch {
             firestore.collection("example")
                 .snapshotChangesFlow(
                     skipFirstCacheHit = ConnectivityUtils.hasInternetConnection(context),
                     documentMapping = { documentSnapshot -> documentSnapshot.toObject(FirebaseModel::class.java) },
                     mappingFn = { firebaseModel, id -> Model(id= id, data = firebaseModel) })
                 .catch { manageError(it) }
                 .collect { event ->
                     when (event) {
                         is SnapshotListenerEvent.FirestoreDocumentChanges -> {
                             //manage event based on type(modified, deleted, added)
                         }
                         is SnapshotListenerEvent.IsEmpty -> {
                             //Empty result
                         }
                         is SnapshotListenerEvent.HasNotPendingWrites -> {
                             //Data updated from offline mode and after connect to internet there is no needed changes
                         }
                     }
                 }
         }
```
### Firebase Authentication

| **Name** | **Description**
| -------- | ---------------
| [FirebaseAuth.authStateChannel] | Returns a `Channel` with conflated capacity that will send a new message every time that there is a change on the authentication user over the current `FirebaseAuth` instance
| [FirebaseAuth.idTokenChannel] | Returns a `Channel` with conflated capacity that will send a new message every time that there is a change on the authentication id token over the current `FirebaseAuth` instance
| [FirebaseAuth.updateCurrentUserProfile] | Updates the current `FirebaseUser` inside the current `FirebaseAuth` instance
| [FirebaseUser.updateProfile] | Helper method to simplify working with `FirebaseUser.updateProfile` during a `FirebaseUser` update

### Firebase Storage

| **Name** | **Description**
| -------- | ---------------
| [StorageReference.updateMetadata] | Helper method to simplify working with `StorageMetadata.Builder` during a `StorageMetadata` update
| [StorageReference.deleteBlocking] | Awaits for the current reference to be deleted

## Working with Firebase and Kotlin coroutines

The following code shows some examples of the use of Firebase with coroutines instead of the common Play Services API.

The standard approach for send an email verification would be:

```kotlin
    private fun sendVerificationEmailToUser(user: FirebaseUser) {
        user.reload().addOnCompleteListener { reloadResult ->
            if (reloadResult.isSuccessful) {
                user.sendEmailVerification().addOnCompleteListener { emailResult ->
                    if (reloadResult.isSuccessful) {
                        //manage success
                    } else {
                        //Manage error
                    }
                }
            } else {
                //Manage error
            }
        }
    }
```

The coroutines approach:
```kotlin
 private suspend fun sendVerificationEmailToUser(user: FirebaseUser) {
        try {
            user.reload().await()
            user.sendEmailVerification().await()
            //Manage success
        } catch (e: Exception) {
            //Manage exception
        }
    }
```
