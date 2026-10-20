package com.example.bluetoothtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Viewer cover/notice hysteresis and the cooling-route browser line (PLAN: Connecting/Recovering flashes).
 * The episode list is the S22 viewer of 20261003-211741-s24-s22-readysetgo-golden1100-isolated-hold-on-a-be31
 * (s22-21254.log, "Receiver recovery UI: recovering=true/false"), relative to the first true at 21:17:49.376.
 */
class ReceiverUiStabilityTest {
    private val s22Episodes = listOf(
        0L to 1_103L,
        1_655L to 3_060L,
        3_462L to 5_120L,
        5_235L to 7_159L,
        7_297L to 11_462L,
        11_925L to 15_622L,
    )

    private fun stable() = ReceiverRecoveryNoticePolicy(holdMs = { ReceiverRecoveryNoticePolicy.REPEAT_HOLD_MS })

    /** Replays episodes the way MainActivity drives the policy and counts how often the cover turns off. */
    private fun coverOffCount(policy: ReceiverRecoveryNoticePolicy, episodes: List<Pair<Long, Long>>): Int {
        var offs = 0
        var shown = false
        var releaseAt: Long? = null
        for ((start, end) in episodes) {
            // A held cover that expired before this episode started is released first.
            if (shown && releaseAt != null && releaseAt <= start) { shown = false; offs++ }
            policy.update(true, start)
            shown = true
            releaseAt = null
            policy.update(false, end)
            val hold = policy.holdRemainingMs(end)
            if (hold > 0L) releaseAt = end + hold else { shown = false; offs++ }
        }
        if (shown) offs++ // the last held cover releases
        return offs
    }

    @Test
    fun theControlArmFlashesOncePerEpisode() {
        assertEquals(s22Episodes.size, coverOffCount(ReceiverRecoveryNoticePolicy(), s22Episodes))
    }

    @Test
    fun aRepeatingBurstKeepsOneCoverInsteadOfFlashingEveryGap() {
        // The first episode is isolated (clears at once); the five that follow are one held cover.
        assertEquals(2, coverOffCount(stable(), s22Episodes))
    }

    @Test
    fun anIsolatedRecoveryClearsTheInstantItEnds() {
        val policy = stable()
        policy.update(true, 1_000L)
        policy.update(false, 1_230L)
        assertFalse(policy.recovering)
        assertEquals(0L, policy.holdRemainingMs(1_230L))
        assertFalse(policy.coverHeld(1_230L))
    }

    @Test
    fun twoRecoveriesFarApartAreBothIsolated() {
        val policy = stable()
        policy.update(true, 1_000L)
        policy.update(false, 1_500L)
        policy.update(true, 1_500L + ReceiverRecoveryNoticePolicy.REPEAT_WITHIN_MS + 1)
        policy.update(false, 6_000L)
        assertEquals(0L, policy.holdRemainingMs(6_000L))
    }

    @Test
    fun aRecoveryThatFollowsARecentReleaseIsHeldAfterItEnds() {
        val policy = stable()
        policy.update(true, 1_000L)
        policy.update(false, 1_500L)
        policy.update(true, 2_000L)
        policy.update(false, 2_400L)
        assertEquals(ReceiverRecoveryNoticePolicy.REPEAT_HOLD_MS, policy.holdRemainingMs(2_400L))
        assertTrue(policy.coverHeld(3_399L))
        assertFalse(policy.coverHeld(3_400L))
    }

    @Test
    fun theCoverIsNeverReleasedWhileTheDecoderStillReportsRecovery() {
        val policy = stable()
        policy.update(true, 1_000L)
        policy.update(false, 1_100L)
        policy.update(true, 1_300L)
        assertTrue(policy.recovering)
        assertEquals(0L, policy.holdRemainingMs(60_000L))
        assertTrue(policy.coverHeld(60_000L))
    }

    @Test
    fun aRecoveryInsideTheHoldContinuesTheSameNoticeReveal() {
        val policy = stable()
        policy.update(true, 1_000L)
        policy.update(false, 1_300L)
        policy.update(true, 1_800L)
        policy.update(false, 4_000L)
        // Third pulse 0.5 s into the hold: the label reveal started at 1.8 s (the second episode), not now.
        policy.update(true, 4_500L)
        assertEquals(0L, policy.noticeDelayRemainingMs(4_500L))
        assertEquals(1f, policy.noticeAlpha(4_500L), 0f)
    }

    @Test
    fun aRecoveryAfterTheHoldExpiredStartsAFreshReveal() {
        val policy = stable()
        policy.update(true, 1_000L)
        policy.update(false, 1_300L)
        policy.update(true, 1_800L)
        policy.update(false, 4_000L)
        policy.update(true, 5_200L)
        assertEquals(350L, policy.noticeDelayRemainingMs(5_200L))
    }

    @Test
    fun resetForgetsAHeldCoverSoANewReceiverStartsClean() {
        val policy = stable()
        policy.update(true, 1_000L)
        policy.update(false, 1_300L)
        policy.update(true, 1_800L)
        policy.update(false, 2_200L)
        assertTrue(policy.coverHeld(2_300L))
        policy.reset()
        assertFalse(policy.coverHeld(2_300L))
        policy.update(true, 2_400L)
        assertEquals(350L, policy.noticeDelayRemainingMs(2_400L))
    }

    @Test
    fun theBrowserNamesACoolingStreamInsteadOfSayingNoneIsNearby() {
        assertEquals(
            "A stream is in range. Trying it again in about 8 s.",
            receiverBrowserEmptyStatus(coolingRetryMs = 7_100L),
        )
        assertEquals(
            "A stream is in range. Trying it again in about 1 s.",
            receiverBrowserEmptyStatus(coolingRetryMs = 0L),
        )
        assertEquals("No nearby streams yet. Scanning continues.", receiverBrowserEmptyStatus(coolingRetryMs = null))
    }

    @Test
    fun theControlArmKeepsTheOldBrowserLine() {
        assertEquals(
            "No nearby streams yet. Scanning continues.",
            receiverBrowserEmptyStatus(coolingRetryMs = 7_100L, stable = false),
        )
    }

    @Test
    fun aFailedJoinStillNamesTheCoolingStreamAfterLastSeenIsCleared() {
        // returnToStreamBrowser removes the failed route from last-seen so it is not retried
        // from cache; the 8 s suppression must still drive the empty-list line.
        val nowMs = 10_000L
        val retryMs = receiverBrowserCoolingRetryMs(nowMs, listOf(nowMs + 8_000L))
        assertEquals(8_000L, retryMs)
        assertEquals(
            "A stream is in range. Trying it again in about 8 s.",
            receiverBrowserEmptyStatus(retryMs),
        )
        assertEquals(1_000L, receiverBrowserCoolingRetryMs(nowMs, listOf(nowMs + 8_000L, nowMs + 1_000L)))
        assertEquals(null, receiverBrowserCoolingRetryMs(nowMs, emptyList()))
        assertEquals(null, receiverBrowserCoolingRetryMs(nowMs, listOf(nowMs - 1L)))
    }

    @Test
    fun aFreshJoinWithStabilityOnDoesNotRevealWatchUntilMediaFlows() {
        // A failed join used to show Watch for 0.25-0.4 s because connectToStream opened
        // playback before the hop existed. Stability keeps the browser until onStreamActive.
        assertFalse(ReceiverJoinUi.revealPlaybackOnTap(stable = true, reconnectingWhilePlaying = false))
        assertTrue(ReceiverJoinUi.attachHiddenPreview(stable = true, reconnectingWhilePlaying = false))
        assertTrue(receiverJoinScreenArmed(playback = false, joinPendingOnBrowser = true))
        assertFalse(receiverJoinScreenArmed(playback = false, joinPendingOnBrowser = false))
    }

    @Test
    fun reconnectingWhileAlreadyWatchingStaysOnPlayback() {
        assertTrue(ReceiverJoinUi.revealPlaybackOnTap(stable = true, reconnectingWhilePlaying = true))
        assertFalse(ReceiverJoinUi.attachHiddenPreview(stable = true, reconnectingWhilePlaying = true))
        assertTrue(receiverJoinScreenArmed(playback = true, joinPendingOnBrowser = false))
    }

    @Test
    fun theControlArmStillOpensWatchOnTap() {
        assertTrue(ReceiverJoinUi.revealPlaybackOnTap(stable = false, reconnectingWhilePlaying = false))
        assertFalse(ReceiverJoinUi.attachHiddenPreview(stable = false, reconnectingWhilePlaying = false))
        assertEquals(
            ReceiverJoinUi.CONNECTING,
            ReceiverJoinUi.connectingCopy(stable = false, elapsedMs = 8_000L, lastStatus = "Requesting stream…"),
        )
    }

    @Test
    fun connectingCopyNamesTheWaitAfterTwoSeconds() {
        assertEquals(
            ReceiverJoinUi.CONNECTING,
            ReceiverJoinUi.connectingCopy(stable = true, elapsedMs = 0L, lastStatus = null),
        )
        assertEquals(
            ReceiverJoinUi.CONNECTING,
            ReceiverJoinUi.connectingCopy(stable = true, elapsedMs = 1_999L, lastStatus = null),
        )
        assertEquals(
            "Still connecting · 2s. Waiting for the other phone.",
            ReceiverJoinUi.connectingCopy(stable = true, elapsedMs = 2_000L, lastStatus = null),
        )
        assertEquals(
            "Requesting North Wind… · 5s",
            ReceiverJoinUi.connectingCopy(stable = true, elapsedMs = 5_400L, lastStatus = "Requesting North Wind…"),
        )
        assertEquals(
            "Still connecting · 15s. Waiting for the other phone.",
            ReceiverJoinUi.connectingCopy(
                stable = true,
                elapsedMs = 15_000L,
                lastStatus = "BENCHMARK le_link_policy event=join",
            ),
        )
    }
}
