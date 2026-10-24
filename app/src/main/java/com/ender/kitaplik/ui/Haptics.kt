package com.ender.kitaplik.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Kisa haptik geri bildirim: barkod okunusu, kitabin rafdan kalkisi, kritik onaylar.
 *
 * Tek seferlik, dusuk genlikli "tik". Uzun/kuvvetli titreme bilerek kullanilmiyor;
 * uygulamanin "pahalı" hissi kisa ve net geri bildirimden geliyor.
 */
fun Context.tickHaptic(millis: Long = 22, amplitude: Int = 80) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return

    if (!vibrator.hasVibrator()) return

    val effect = VibrationEffect.createOneShot(
        millis,
        amplitude.coerceIn(1, 255)
    )
    vibrator.vibrate(effect)
}