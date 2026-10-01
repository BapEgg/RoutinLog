package com.bapegg.routinlog.steps

import android.content.Context
import androidx.work.*
import com.bapegg.routinlog.BuildConfig
import com.bapegg.routinlog.data.AccountRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.dropWhile
import java.util.concurrent.TimeUnit

/** One repository per process avoids competing refresh-token rotations between UI and worker. */
class RoutineLogServices private constructor(context:Context) {
    val accounts=AccountRepository.create(context,BuildConfig.API_BASE_URL,BuildConfig.DEBUG,BuildConfig.GOOGLE_WEB_CLIENT_ID)
    val steps=StepEngine(accounts,AndroidPhoneSteps(context),AndroidStepBindingStore(context),AndroidStepSchedule(context)){accounts.identity.value?.userId}
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    init {
        // Ignore the initial unrestored null; react to logout/expiry even without a visible Activity.
        scope.launch { accounts.identity.dropWhile { it==null }.collect { identity->
            try { steps.bind(identity?.userId) }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){ /* Retried by the UI/worker; do not log account details. */ }
        } }
    }
    companion object {
        @Volatile private var instance:RoutineLogServices?=null
        fun get(context:Context)=instance ?: synchronized(this){instance ?: RoutineLogServices(context.applicationContext).also { instance=it }}
    }
}
class AndroidStepSchedule(context:Context):StepSchedule {
    private val work=WorkManager.getInstance(context.applicationContext)
    override fun start() {
        val request=PeriodicWorkRequestBuilder<StepSyncWorker>(6,TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.MINUTES).build()
        work.enqueueUniquePeriodicWork("phone-step-sync",ExistingPeriodicWorkPolicy.KEEP,request)
    }
    override fun cancel() { work.cancelUniqueWork("phone-step-sync") }
}
class StepSyncWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {
        val services=RoutineLogServices.get(applicationContext)
        val done=services.steps.syncRestoredAccount {
            (services.accounts.identity.value ?: services.accounts.restoreSession())?.userId
        }
        return if(done)Result.success()else Result.retry()
    }
}
