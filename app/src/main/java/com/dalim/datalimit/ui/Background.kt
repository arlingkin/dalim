package com.dalim.datalimit.ui

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Serialized background worker for the UI screens.
 *
 * The screens used to spawn a raw `Thread` per refresh tick (dashboard vault
 * count, battery chart, usage history) and per keystroke (vault search). Each
 * came with a throwaway store instance, so a long-lived dashboard leaked one
 * SQLite connection every 5 s until the process was killed. This helper keeps
 * exactly one worker thread and runs every job on it, which also serializes
 * SQLite access instead of letting several threads open the database at once.
 *
 * Refresh jobs are coalesced: while one is pending or running, a newer refresh
 * replaces it rather than piling up behind it. Mutating jobs (delete, mark
 * read) pass `coalesce = false` so they are never dropped and keep their order.
 *
 * Failure rule follows the rest of the codebase: a failing job goes to
 * [onError] and never propagates, so it cannot take the process down. [then]
 * runs only after a successful job, so a screen never renders a value that was
 * not actually read.
 */
class Background(
    private val onError: (Throwable) -> Unit = {}
) {

    private class Job(val work: () -> Unit, val then: (() -> Unit)?, val coalesce: Boolean)

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "dalim-bg").apply { isDaemon = true }
    }
    private val lock = Any()
    private val queue = ArrayDeque<Job>()
    private var running = false

    /** Run [work] off the main thread, then deliver [then] on it. */
    fun run(then: (() -> Unit)? = null, coalesce: Boolean = true, work: () -> Unit) {
        val job = Job(work, then, coalesce)
        var start = false
        synchronized(lock) {
            if (coalesce) queue.removeAll { it.coalesce }
            queue.addLast(job)
            while (queue.size > MAX_QUEUE) queue.removeFirst()
            if (!running) {
                running = true
                start = true
            }
        }
        if (start) drain()
    }

    private fun drain() {
        val job: Job? = synchronized(lock) {
            val next = queue.removeFirstOrNull()
            if (next == null) running = false
            next
        }
        if (job == null) return
        executor.execute {
            var succeeded = false
            try {
                job.work()
                succeeded = true
            } catch (t: Throwable) {
                if (t is InterruptedException) Thread.currentThread().interrupt()
                report(t)
            }
            post {
                if (succeeded && job.then != null) {
                    try {
                        job.then.invoke()
                    } catch (t: Throwable) {
                        report(t)
                    }
                }
                drain()
            }
        }
    }

    private fun report(t: Throwable) {
        post {
            try {
                onError(t)
            } catch (_: Throwable) {
            }
        }
    }

    /** Post to the main thread, running inline when already on it. */
    fun post(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action()
        else main.post { action() }
    }

    private companion object {
        const val MAX_QUEUE = 64
    }
}
