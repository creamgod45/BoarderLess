@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import platform.AVFAudio.*
import platform.Foundation.*
import platform.darwin.NSObject
import kotlin.test.*

/** Real Foundation observer delivery, injected activation; not a hardware / audio test. */
class SystemIosVideoAudioSessionTest {
    @Test fun interruptionBeginPausesButEndedAndShouldResumeDoNotReactivate() {
        val audio = AVAudioSession.sharedInstance(); val activations = mutableListOf<Boolean>(); var losses = 0
        val policy = IosVideoAudioSession(SystemIosVideoAudioSessionPort(audio, { true }, { activations += it; true }))
        val owner = Any()
        try {
            assertTrue(policy.acquire(owner) { losses++ })
            NSNotificationCenter.defaultCenter.postNotificationName(checkNotNull(AVAudioSessionInterruptionNotification), audio, mapOf(
                AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLongLong = AVAudioSessionInterruptionTypeEnded),
                AVAudioSessionInterruptionOptionKey to NSNumber(unsignedLongLong = AVAudioSessionInterruptionOptionShouldResume),
            ))
            assertEquals(0, losses)
            post(AVAudioSessionInterruptionNotification, audio, AVAudioSessionInterruptionTypeKey, AVAudioSessionInterruptionTypeBegan)
            assertEquals(1, losses); assertEquals(listOf(true, false), activations)
            NSNotificationCenter.defaultCenter.postNotificationName(checkNotNull(AVAudioSessionInterruptionNotification), audio, mapOf(
                AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLongLong = AVAudioSessionInterruptionTypeEnded),
                AVAudioSessionInterruptionOptionKey to NSNumber(unsignedLongLong = AVAudioSessionInterruptionOptionShouldResume),
            ))
            assertEquals(listOf(true, false), activations)
        } finally { policy.release(owner) }
    }

    @Test fun oldDeviceUnavailablePausesWhileConnectOverrideMalformedAndForeignNotificationsDoNot() {
        val audio = AVAudioSession.sharedInstance(); var losses = 0
        val policy = IosVideoAudioSession(SystemIosVideoAudioSessionPort(audio, { true }, { true }))
        val owner = Any()
        try {
            policy.acquire(owner) { losses++ }
            post(AVAudioSessionRouteChangeNotification, audio, AVAudioSessionRouteChangeReasonKey, AVAudioSessionRouteChangeReasonNewDeviceAvailable)
            post(AVAudioSessionRouteChangeNotification, audio, AVAudioSessionRouteChangeReasonKey, AVAudioSessionRouteChangeReasonOverride)
            NSNotificationCenter.defaultCenter.postNotificationName(AVAudioSessionRouteChangeNotification, audio, mapOf(AVAudioSessionRouteChangeReasonKey to "invalid"))
            post(AVAudioSessionRouteChangeNotification, NSObject(), AVAudioSessionRouteChangeReasonKey, AVAudioSessionRouteChangeReasonOldDeviceUnavailable)
            assertEquals(0, losses)
            post(AVAudioSessionRouteChangeNotification, audio, AVAudioSessionRouteChangeReasonKey, AVAudioSessionRouteChangeReasonOldDeviceUnavailable)
            assertEquals(1, losses)
            assertTrue(policy.acquire(owner) { losses++ })
            post(AVAudioSessionRouteChangeNotification, audio, AVAudioSessionRouteChangeReasonKey, AVAudioSessionRouteChangeReasonOldDeviceUnavailable)
            assertEquals(2, losses) // Previous receiver was removed, not accumulated.
        } finally { policy.release(owner) }
    }

    @Test fun resetInterruptsAndDeniedActivationRemovesObservers() {
        val audio = AVAudioSession.sharedInstance(); var losses = 0; var granted = false
        val activations = mutableListOf<Boolean>()
        val policy = IosVideoAudioSession(SystemIosVideoAudioSessionPort(audio, { true }, { active -> activations += active; !active || granted }))
        val owner = Any()
        try {
            assertFalse(policy.acquire(owner) { losses++ })
            NSNotificationCenter.defaultCenter.postNotificationName(AVAudioSessionMediaServicesWereResetNotification, null)
            assertEquals(0, losses); assertEquals(listOf(true, false), activations)
            granted = true
            assertTrue(policy.acquire(owner) { losses++ })
            NSNotificationCenter.defaultCenter.postNotificationName(AVAudioSessionMediaServicesWereResetNotification, null)
            assertEquals(1, losses); assertEquals(listOf(true, false, true, false), activations)
        } finally { policy.release(owner) }
    }

    private fun post(name: String?, audio: Any, key: String?, value: ULong) {
        assertTrue(NSThread.isMainThread)
        NSNotificationCenter.defaultCenter.postNotificationName(checkNotNull(name), audio, mapOf(checkNotNull(key) to NSNumber(unsignedLongLong = value)))
    }
}
