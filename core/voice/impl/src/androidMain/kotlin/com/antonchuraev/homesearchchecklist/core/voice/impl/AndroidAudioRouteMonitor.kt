package com.antonchuraev.homesearchchecklist.core.voice.impl

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.os.Handler
import android.os.Looper
import com.antonchuraev.homesearchchecklist.core.voice.api.AudioRoute
import com.antonchuraev.homesearchchecklist.core.voice.api.AudioRouteMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the agent's voice goes, from the connected output devices: headphones of any kind win over the speaker
 * (voice barge-in is safe there). The device callback lives as long as the process (Koin `single`); it costs
 * nothing idle.
 */
internal class AndroidAudioRouteMonitor(context: Context) : AudioRouteMonitor {

    private val manager = context.getSystemService(AudioManager::class.java)

    private val _route = MutableStateFlow(currentRoute())
    override val route: StateFlow<AudioRoute> = _route.asStateFlow()

    override val echoCancelerAvailable: Boolean by lazy { AcousticEchoCanceler.isAvailable() }

    init {
        manager?.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    _route.value = currentRoute()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    _route.value = currentRoute()
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    private fun currentRoute(): AudioRoute {
        val types = manager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().map { it.type }.toSet()
        return when {
            types.any { it in BLUETOOTH } -> AudioRoute.Bluetooth
            types.any { it in WIRED } -> AudioRoute.WiredHeadset
            else -> AudioRoute.Speaker
        }
    }

    private companion object {
        /** `TYPE_BLE_HEADSET` (26) and `TYPE_BLE_SPEAKER` (27) are API 31 constants, inlined (minSdk 24). */
        val BLUETOOTH = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 26, 27)

        /** `TYPE_USB_HEADSET` (22) is an API 26 constant, inlined (minSdk 24). */
        val WIRED = setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, 22)
    }
}
