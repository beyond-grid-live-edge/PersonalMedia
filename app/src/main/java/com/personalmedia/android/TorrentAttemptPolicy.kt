package com.personalmedia.android

/** Only the currently selected preparation owns the native cleanup and UI.
 * Cancellation advances the generation before the old coroutine is cancelled.
 * Stale completions must never close a newly started libtorrent session. */
internal object TorrentAttemptPolicy {
    fun isCurrent(attempt: Long, generation: Long): Boolean = attempt == generation
}
