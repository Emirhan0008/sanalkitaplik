package com.ender.kitaplik.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ender.kitaplik.KitaplikApp
import java.util.concurrent.TimeUnit

/**
 * Cevrimdisi eklenen kitaplarin kunyesini ag gelince tamamlayan tek kuyruk.
 *
 * - Yalnizca ag baglantisi varken calisir (sarj/Wi-Fi zorunlu degil).
 * - Exponential backoff: 30 sn -> 1 sa, en fazla [MAX_ATTEMPTS] deneme.
 * - `enqueueUniqueWork(APPEND_OR_REPLACE)`: ayni is icin ikinci worker kuyruga girmez.
 */
class EnrichWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KitaplikApp ?: return Result.failure()
        val repo = app.container.repository

        val pending = repo.pendingIsbns()
        if (pending.isEmpty()) return Result.success()

        var remaining = 0
        pending.forEach { isbn ->
            if (!repo.enrich(isbn)) remaining++
        }

        return when {
            remaining == 0 -> Result.success()
            runAttemptCount + 1 < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        /** Toplam deneme siniri (ilk calisma dahil). */
        const val MAX_ATTEMPTS = 5

        private const val WORK_NAME = "enrich-pending"

        /** Uygulama acilisi, kitap ekleme ve "yeniden dene" buradan kuyruga girer. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<EnrichWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
