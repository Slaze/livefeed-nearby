package com.example.bluetoothtest

/**
 * How LiveFeed opens this Nearby radio without mixing LiveFeed accounts into radio identity.
 * LiveFeed starts this activity with [ACTION_NEARBY] and an optional [EXTRA_OPEN_SCREEN].
 *
 * Host extras are not automation: they must not skip first-run onboarding.
 */
internal object LiveFeedHostLaunch {
    const val ACTION_NEARBY = "com.livefeed.action.NEARBY"
    const val EXTRA_OPEN_SCREEN = "com.livefeed.extra.OPEN_SCREEN"
    const val EXTRA_CALLEE_NODE_ID = "com.livefeed.extra.CALLEE_NODE_ID"

    enum class Screen { LIVE, CALLS, CONTACTS }

    fun isHostExtra(key: String?): Boolean =
        key == EXTRA_OPEN_SCREEN || key == EXTRA_CALLEE_NODE_ID

    fun calleeNodeId(extra: String?): String? {
        val id = extra?.trim()?.lowercase() ?: return null
        return if (id.matches(Regex("[0-9a-f]{32}"))) id else null
    }

    fun launchHasNonHostExtras(extraKeys: Collection<String>?): Boolean =
        extraKeys?.any { !isHostExtra(it) } == true

    fun screenFor(action: String?, extra: String?): Screen? {
        val fromExtra = when (extra?.trim()?.lowercase()) {
            "live", "watch", "stream" -> Screen.LIVE
            "calls", "call" -> Screen.CALLS
            "contacts", "people" -> Screen.CONTACTS
            null, "" -> null
            else -> null
        }
        if (fromExtra != null) return fromExtra
        return if (action == ACTION_NEARBY) Screen.LIVE else null
    }
}
