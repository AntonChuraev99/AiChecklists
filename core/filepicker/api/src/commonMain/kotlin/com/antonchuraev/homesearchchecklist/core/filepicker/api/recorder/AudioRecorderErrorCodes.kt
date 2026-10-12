package com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder

/** Machine-readable codes passed to `onError` of [rememberAudioRecorderLauncher]. Any other string is a recorder failure message. */
object AudioRecorderErrorCodes {
    const val PERMISSION_DENIED = "PERMISSION_DENIED"
    const val PERMISSION_DENIED_PERMANENTLY = "PERMISSION_DENIED_PERMANENTLY"
    /** Permission was just granted, but the press that asked for it is already over — the user must press and hold again. */
    const val PERMISSION_GRANTED_PRESS_AGAIN = "PERMISSION_GRANTED_PRESS_AGAIN"
    /** `stop()` came before any valid audio was captured. */
    const val RECORDING_TOO_SHORT = "RECORDING_TOO_SHORT"
}
