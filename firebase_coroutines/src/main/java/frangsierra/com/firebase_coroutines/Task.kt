package frangsierra.com.firebase_coroutines

import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.RuntimeExecutionException
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Converts this deferred to the instance of [Task].
 * If deferred is cancelled then resulting task will be cancelled as well.
 *
 * This method is a lightly modification of the official KT coroutines for Google Play Services
 * @see https://github.com/Kotlin/kotlinx.coroutines/blob/master/integration/kotlinx-coroutines-play-services/src/Tasks.kt
 */
fun <T> Deferred<T>.asTask(): Task<T> {
    val cancellation = CancellationTokenSource()
    val source = TaskCompletionSource<T>(cancellation.token)

    invokeOnCompletion callback@{
        if (it is CancellationException) {
            cancellation.cancel()
            return@callback
        }

        val t = getCompletionExceptionOrNull()
        if (t == null) {
            source.setResult(getCompleted())
        } else {
            source.setException(t as? Exception ?: RuntimeExecutionException(t))
        }
    }

    return source.task
}

/**
 * Converts this task to an instance of [Deferred].
 * If task is cancelled then resulting deferred will be cancelled as well.
 *
 * This method is a lightly modification of the official KT coroutines for Google Play Services
 * @see https://github.com/Kotlin/kotlinx.coroutines/blob/master/integration/kotlinx-coroutines-play-services/src/Tasks.kt
 */
fun <T> Task<T>.asDeferred(): Deferred<T> {
    if (isComplete) {
        val e = exception
        return if (e == null) {
            CompletableDeferred<T>().apply { if (isCanceled) cancel() else complete(result!!) }
        } else {
            CompletableDeferred<T>().apply { completeExceptionally(e) }
        }
    }

    val result = CompletableDeferred<T>()
    addOnCompleteListener {
        val e = it.exception
        if (e == null) {
            if (isCanceled) result.cancel() else result.complete(it.result!!)
        } else {
            result.completeExceptionally(e)
        }
    }
    return result
}

/**
 * Awaits for completion of the task without blocking a thread.
 *
 * This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function is waiting, this function
 * stops waiting for the completion stage and immediately resumes with [CancellationException].
 *
 * This method is a lightly modification of the official KT coroutines for Google Play Services
 * @see https://github.com/Kotlin/kotlinx.coroutines/blob/master/integration/kotlinx-coroutines-play-services/src/Tasks.kt
 */
suspend fun <T> Task<T>.await(): T {
    // fast path
    if (isComplete) {
        val e = exception
        return if (e == null) {
            if (isCanceled) {
                throw CancellationException("Task $this was cancelled normally.")
            } else {
                result!!
            }
        } else {
            throw e
        }
    }

    return suspendCancellableCoroutine { cont ->
        addOnCompleteListener {
            val e = exception
            if (e == null) {
                if (isCanceled) cont.cancel() else cont.resume(result!!)
            } else {
                cont.resumeWithException(e)
            }
        }
    }
}