package com.example.bluetoothtest

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LiveFeed opens this Nearby radio. The mapping must stay explicit: a host launch is not
 * a benchmark extra, and a missing action must not switch tabs.
 */
class LiveFeedHostLaunchTest {
    @Test
    fun nearbyActionOpensLiveWhenNoScreenExtra() {
        assertEquals(
            LiveFeedHostLaunch.Screen.LIVE,
            LiveFeedHostLaunch.screenFor(LiveFeedHostLaunch.ACTION_NEARBY, null),
        )
    }

    @Test
    fun screenExtraSelectsCallsAndContacts() {
        assertEquals(
            LiveFeedHostLaunch.Screen.CALLS,
            LiveFeedHostLaunch.screenFor(LiveFeedHostLaunch.ACTION_NEARBY, "calls"),
        )
        assertEquals(
            LiveFeedHostLaunch.Screen.CONTACTS,
            LiveFeedHostLaunch.screenFor(LiveFeedHostLaunch.ACTION_NEARBY, "contacts"),
        )
    }

    @Test
    fun unknownActionWithoutExtraDoesNotSwitchTabs() {
        assertNull(LiveFeedHostLaunch.screenFor(IntentAction.MAIN, null))
        assertNull(LiveFeedHostLaunch.screenFor(null, "nope"))
    }

    @Test
    fun hostExtrasDoNotCountAsAutomation() {
        assertFalse(
            LiveFeedHostLaunch.launchHasNonHostExtras(listOf(LiveFeedHostLaunch.EXTRA_OPEN_SCREEN)),
        )
        assertTrue(
            LiveFeedHostLaunch.launchHasNonHostExtras(
                listOf(LiveFeedHostLaunch.EXTRA_OPEN_SCREEN, "test_auto_broadcast"),
            ),
        )
        assertFalse(LiveFeedHostLaunch.launchHasNonHostExtras(emptyList()))
        assertFalse(LiveFeedHostLaunch.launchHasNonHostExtras(null))
    }

    @Test
    fun onboardingStillShowsWhenOnlyHostExtrasArePresent() {
        val hostOnly = LiveFeedHostLaunch.launchHasNonHostExtras(
            listOf(LiveFeedHostLaunch.EXTRA_OPEN_SCREEN),
        )
        assertTrue(shouldShowOnboarding(onboardingDone = false, launchHasExtras = hostOnly, recreated = false))
    }

    @Test
    fun calleeNodeIdAccepts32HexOnly() {
        assertEquals(
            "0123456789abcdef0123456789abcdef",
            LiveFeedHostLaunch.calleeNodeId("0123456789ABCDEF0123456789ABCDEF"),
        )
        assertNull(LiveFeedHostLaunch.calleeNodeId("user-2827"))
        assertNull(LiveFeedHostLaunch.calleeNodeId("abcd"))
        assertTrue(
            LiveFeedHostLaunch.launchHasNonHostExtras(
                listOf(LiveFeedHostLaunch.EXTRA_OPEN_SCREEN, LiveFeedHostLaunch.EXTRA_CALLEE_NODE_ID),
            ).not(),
        )
    }

    @Test
    fun manifestExportsTheNearbyHostAction() {
        val candidates = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
        val manifest = candidates.first { it.isFile }.readText()
        assertTrue(manifest.contains(LiveFeedHostLaunch.ACTION_NEARBY))
    }
}

/** Local stand-in so the test does not import Android Intent.ACTION_MAIN. */
private object IntentAction {
    const val MAIN = "android.intent.action.MAIN"
}
