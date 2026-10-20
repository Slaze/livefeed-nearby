package com.example.bluetoothtest

/**
 * Timing of the viewer's "Recovering video" cover and notice.
 *
 * With [holdMs] = 0 (the pre-2026-10-11 behaviour, and the control arm `test_receiver_ui_stability=false`)
 * the cover follows the decoder's recovery flag one to one. Saved viewer logs show what that does when
 * recoveries recur: the S22 in 20261003-211741-...-isolated-hold-on-a-be31 covered the picture for
 * 0.7-4 s, uncovered it for 0.1-0.55 s and covered it again about a dozen times in 30 s; across all
 * logs 45% of the gaps between two recovery episodes (34 of 75) are under 1 s, and 334 covers were
 * dropped again after less than 0.4 s. Each end/start pair is a visible flash of the 70% black cover and
 * restarts the label reveal.
 *
 * Damping, not a longer cover for everything: only a *repeating* episode (one that began within
 * [REPEAT_WITHIN_MS] of the previous cover's release) is held for [holdMs] after it ends, and a recovery
 * that starts inside that hold continues the same episode (cover and label reveal carry on). An
 * isolated recovery clears the instant it ends, as before. The cover is never hidden while the decoder
 * reports recovery, so no corrupted picture is exposed.
 */
internal class ReceiverRecoveryNoticePolicy(
    private val holdMs: () -> Long = { 0L },
) {
    private var recoveryStartedAtMs: Long? = null
    private var active = false
    private var releaseAtMs: Long? = null
    private var repeating = false

    val recovering: Boolean
        get() = active

    fun update(recovering: Boolean, nowMs: Long) {
        if (recovering) {
            if (active) return
            val release = releaseAtMs
            val held = release != null && nowMs < release && recoveryStartedAtMs != null
            if (!held) {
                // A new episode. It repeats when the previous cover was released only moments ago.
                repeating = holdMs() > 0L && release != null && nowMs - release <= REPEAT_WITHIN_MS
                recoveryStartedAtMs = nowMs
            }
            active = true
            releaseAtMs = null
            return
        }
        if (!active) {
            // A duplicate "not recovering": nothing changes, except that with no hold configured the
            // old behaviour also forgot the episode start.
            if (holdMs() <= 0L) recoveryStartedAtMs = null
            return
        }
        active = false
        val hold = if (repeating) holdMs() else 0L
        releaseAtMs = nowMs + hold
        if (hold <= 0L) recoveryStartedAtMs = null
    }

    /** True while the cover must stay: recovering, or inside the hold after a repeating episode. */
    fun coverHeld(nowMs: Long): Boolean {
        if (active) return true
        val release = releaseAtMs ?: return false
        return nowMs < release
    }

    /** Milliseconds until a held cover may clear; 0 when nothing is held or the decoder still recovers. */
    fun holdRemainingMs(nowMs: Long): Long {
        if (active) return 0L
        val release = releaseAtMs ?: return 0L
        return (release - nowMs).coerceAtLeast(0L)
    }

    /** Forget everything (new receiver, pause, end, route change). */
    fun reset() {
        recoveryStartedAtMs = null
        active = false
        releaseAtMs = null
        repeating = false
    }

    fun noticeDelayRemainingMs(nowMs: Long): Long? = recoveryStartedAtMs?.let {
        (NOTICE_DELAY_MS - (nowMs - it)).coerceAtLeast(0L)
    }

    fun noticeAlpha(nowMs: Long): Float {
        val started = recoveryStartedAtMs ?: return 0f
        val progress = ((nowMs - started - NOTICE_DELAY_MS).toFloat() / NOTICE_REVEAL_MS)
            .coerceIn(0f, 1f)
        return progress * progress * progress * progress
    }

    companion object {
        private const val NOTICE_DELAY_MS = 350L
        private const val NOTICE_REVEAL_MS = 350L

        /** A cover that follows a release by less than this is a repeat (same burst, not a new event). */
        const val REPEAT_WITHIN_MS = 3_000L

        /** Quiet time a repeating episode needs before its cover clears: above the median sub-second gap. */
        const val REPEAT_HOLD_MS = 1_000L
    }
}

/**
 * The browser line shown when no stream is listed. A route that just failed (or was withdrawn) is kept
 * off the list for its cooldown (8 s after a failed join, 30 s after a withdrawal); the line used to say
 * "No nearby streams yet" although the stream was in range, which reads as the stream having vanished.
 * Saved A15s run bmaraw-ab-stream-9c121b30-s24a15s-run2c 0008 selects the same route again exactly one
 * cooldown (8.0 s) after each failure, so the route was visible to the app the whole time.
 */
internal fun receiverBrowserEmptyStatus(coolingRetryMs: Long?, stable: Boolean = true): String {
    if (!stable || coolingRetryMs == null) return "No nearby streams yet. Scanning continues."
    val seconds = ((coolingRetryMs.coerceAtLeast(0L) + 999L) / 1000L).coerceAtLeast(1L)
    return "A stream is in range. Trying it again in about $seconds s."
}

/**
 * Remaining cooldown for the empty Live list. A failed join removes that route from last-seen so
 * it is not retried until a fresh advertisement, which used to make [receiverBrowserEmptyStatus]
 * see no cooling stream and say "No nearby streams yet" for up to 8 s although suppression was
 * active. The suppression deadlines are the source of truth.
 */
internal fun receiverBrowserCoolingRetryMs(
    nowMs: Long,
    suppressionDeadlinesMs: Iterable<Long>,
): Long? = suppressionDeadlinesMs.minOfOrNull { it - nowMs }?.takeIf { it >= 0L }

internal fun receiverStartAllowed(
    hasSelectedStreamRoute: Boolean,
    isFindingRoute: Boolean,
    isDirectDebugReceiver: Boolean,
): Boolean = !isFindingRoute && (hasSelectedStreamRoute || isDirectDebugReceiver)

/**
 * Whether [AndroidVideoReceiver] may start on this screen. Watch ([playback]) is the usual place;
 * a fresh join with UI stability on keeps the Live browser visible and attaches a 1×1 TextureView
 * underneath so the surface exists without revealing Watch (PLAN: failed join showed Watch 0.25-0.4 s).
 */
internal fun receiverJoinScreenArmed(playback: Boolean, joinPendingOnBrowser: Boolean): Boolean =
    playback || joinPendingOnBrowser

/**
 * When to reveal Watch during a join, and the connecting copy.
 *
 * With [DebugTunables.receiverUiStability] on, a tap stays on the Live browser until the receiver
 * reports media flowing ([onStreamActive]). Revealing Watch on tap needed the TextureView, but a
 * join that then failed still showed Watch for 0.25-0.4 s (DECISIONS 2026-10-11). The TextureView
 * is kept at 1×1 px under the opaque browser so receiving can start without a visible Watch flash.
 *
 * Connecting copy: after 2 s the overlay (or the browser line) names the elapsed wait, so a 15 s
 * GATT accept timeout is not a frozen "Connecting to stream" spinner. Control arm (stability off):
 * Watch is shown on tap and the overlay stays the old static line.
 */
internal object ReceiverJoinUi {
    const val CONNECTING_PROGRESS_AFTER_MS = 2_000L
    const val CONNECTING_TICK_MS = 1_000L
    const val CONNECTING = "Connecting to stream"

    fun revealPlaybackOnTap(stable: Boolean, reconnectingWhilePlaying: Boolean): Boolean =
        !stable || reconnectingWhilePlaying

    fun attachHiddenPreview(stable: Boolean, reconnectingWhilePlaying: Boolean): Boolean =
        stable && !reconnectingWhilePlaying

    fun connectingCopy(stable: Boolean, elapsedMs: Long, lastStatus: String?): String {
        if (!stable) return CONNECTING
        val elapsed = elapsedMs.coerceAtLeast(0L)
        val seconds = elapsed / 1_000L
        val detail = lastStatus?.trim()?.takeIf(::isUserFacingJoinStatus)
        return when {
            elapsed < CONNECTING_PROGRESS_AFTER_MS -> detail ?: CONNECTING
            detail != null -> "$detail · ${seconds}s"
            else -> "Still connecting · ${seconds}s. Waiting for the other phone."
        }
    }

    private fun isUserFacingJoinStatus(message: String): Boolean {
        if (message.startsWith("BENCHMARK") || message.contains("BENCHMARK ")) return false
        if (message.contains("event=") || message.contains(" | ")) return false
        return true
    }
}
