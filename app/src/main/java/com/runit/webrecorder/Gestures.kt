package com.runit.webrecorder

/** Things a gesture can do. */
enum class GAction(val label: String) {
    NONE("Do nothing"),
    BACK("Back"),
    FORWARD("Forward"),
    RELOAD("Reload page"),
    NEXT_TAB("Next tab"),
    PREV_TAB("Previous tab"),
    NEW_TAB("New tab"),
    CLOSE_TAB("Close this tab"),
    TOP("Scroll to top"),
    BOTTOM("Scroll to bottom"),
    TABS("Open tab list"),
    HOME("Go to start page"),
    DESKTOP("Desktop site on / off"),
    FULLSCREEN("Full screen on / off"),
    SCAN("Scan QR code")
}

/** Where a gesture happens. */
enum class GSlot(val key: String, val label: String, val def: GAction) {
    EDGE_LEFT("edge_l", "Swipe in from the left edge", GAction.BACK),
    EDGE_RIGHT("edge_r", "Swipe in from the right edge", GAction.FORWARD),
    BAR_LEFT("bar_l", "Swipe left on the address bar", GAction.NEXT_TAB),
    BAR_RIGHT("bar_r", "Swipe right on the address bar", GAction.PREV_TAB),
    BAR_UP("bar_u", "Swipe up on the address bar", GAction.TABS),
    BAR_DOWN("bar_d", "Swipe down on the address bar", GAction.NONE),
    TABS_HOLD("tabs_hold", "Hold the tab button", GAction.NEW_TAB)
}

object Gestures {
    fun get(slot: GSlot): GAction {
        val saved = Settings.gesture(slot.key) ?: return slot.def
        return GAction.values().firstOrNull { it.name == saved } ?: slot.def
    }

    fun set(slot: GSlot, action: GAction) {
        Settings.setGesture(slot.key, if (action == slot.def) null else action.name)
    }

    fun resetAll() {
        for (s in GSlot.values()) Settings.setGesture(s.key, null)
    }
}
