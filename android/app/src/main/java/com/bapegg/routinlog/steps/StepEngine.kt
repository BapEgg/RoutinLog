package com.bapegg.routinlog.steps

import com.bapegg.routinlog.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import java.util.UUID

enum class StepAvailability { READY, PERMISSION_REQUIRED, SERVICES_REQUIRED, UNSUPPORTED }
interface PhoneSteps {
    fun availability():StepAvailability
    suspend fun start()
    suspend fun stop()
    /** null means no returned observation, not zero steps. */
    suspend fun read(from:Instant,through:Instant):Long?
}
data class StepBinding(val owner:String,val connectionId:String)
interface StepBindingStore { fun read():StepBinding?;fun write(binding:StepBinding? ) }
interface StepSchedule { fun start();fun cancel() }
data class StepWindow(val date:LocalDate,val from:Instant,val through:Instant)

/** Calendar boundaries use the connection's frozen zone, including 23/25-hour DST days.
 * Re-read today plus eight days; avoid the provider's sliding ten-day retention boundary. */
fun stepWindows(connection:StepConnection,serverTime:Instant):List<StepWindow> {
    val zone=ZoneId.of(connection.timeZone);val started=Instant.parse(connection.startedAt)
    val today=serverTime.atZone(zone).toLocalDate()
    return (8 downTo 0).mapNotNull { ago->
        val day=today.minusDays(ago.toLong())
        val start=maxOf(started,day.atStartOfDay(zone).toInstant())
        val end=minOf(serverTime,day.plusDays(1).atStartOfDay(zone).toInstant())
        if(start<end)StepWindow(day,start,end)else null
    }
}

/** Shared by the foreground and WorkManager in the same process. No raw health data is cached.
 * A binding is an explicit per-account opt-in; changing account never inherits it. */
class StepEngine(private val api:StepDataSource,private val phone:PhoneSteps,private val store:StepBindingStore,
    private val schedule:StepSchedule,private val identity:()->String?) {
    private val mutex=Mutex()
    fun availability()=phone.availability()
    fun connected(owner:String)=store.read()?.owner==owner
    /** A cold-start worker must stop capture on confirmed session expiry, but retain its
     * binding on a transient network error so it can retry without asking for consent again. */
    suspend fun syncRestoredAccount(restore:suspend()->String?):Boolean = try {
        val owner=restore();bind(owner);if(owner!=null)sync(owner);true
    }catch(cancelled:CancellationException){throw cancelled}
    catch(error:Exception) {
        if(error is AccountException && error.kind==AccountErrorKind.EXPIRED) {bind(identity());true}
        else false
    }
    suspend fun bind(owner:String?)=mutex.withLock {
        if(store.read()?.owner!=owner)clearLocal()
        else if(owner!=null)schedule.start()
    }
    suspend fun connect(owner:String)=mutex.withLock {
        checkOwner(owner)
        check(phone.availability()==StepAvailability.READY){"휴대폰의 신체 활동 권한과 Google Play 서비스를 확인해주세요."}
        var installed=false
        try {
            withContext(NonCancellable){phone.start()};currentCoroutineContext().ensureActive();checkOwner(owner)
            val id=UUID.randomUUID().toString()
            val result=api.connectSteps(owner,id)
            currentCoroutineContext().ensureActive();checkOwner(owner)
            check(result.active?.id==id){"걸음 연결을 확인하지 못했어요. 다시 시도해주세요."}
            store.write(StepBinding(owner,id));installed=true;schedule.start()
        }finally { if(!installed)withContext(NonCancellable){clearLocal(forceStop=true)} }
    }
    suspend fun disconnect(owner:String)=mutex.withLock {
        val binding=store.read()?.takeIf { it.owner==owner }
        // Local opt-out takes effect even when the network fails.
        clearLocal()
        if(binding!=null){checkOwner(owner);api.disconnectSteps(owner,binding.connectionId)}
    }
    suspend fun sync(owner:String)=mutex.withLock {
        checkOwner(owner)
        val binding=store.read()?.takeIf { it.owner==owner } ?: return@withLock
        if(phone.availability()==StepAvailability.PERMISSION_REQUIRED) {
            clearLocal();api.disconnectSteps(owner,binding.connectionId)
            throw IllegalStateException("신체 활동 권한이 꺼졌어요. 다시 연결하면 이후 걸음부터 가져와요.")
        }
        check(phone.availability()==StepAvailability.READY){"이 휴대폰의 걸음 수집 기능을 사용할 수 없어요. Google Play 서비스를 확인해주세요."}
        val remote=api.stepConnection(owner)
        val connection=remote.active?.takeIf { it.id==binding.connectionId }
        if(connection==null){clearLocal();throw IllegalStateException("걸음 연결이 바뀌었어요. 이 휴대폰에서 다시 연결해주세요.")}
        val records=stepWindows(connection,Instant.parse(remote.serverTime)).mapNotNull { window->
            currentCoroutineContext().ensureActive();checkOwner(owner)
            phone.read(window.from,window.through)?.let { count->
                check(count in 0..300000){"걸음 기록을 확인하지 못했어요. 잠시 후 다시 시도해주세요."}
                StepObservation(window.date.toString(),window.from.toString(),window.through.toString(),count)
            }
        }
        currentCoroutineContext().ensureActive();checkOwner(owner)
        if(records.isNotEmpty())api.saveSteps(owner,binding.connectionId,StepBatch(records))
    }
    private suspend fun clearLocal(forceStop:Boolean=false) {
        val hadBinding=store.read()!=null
        try { store.write(null) }finally {
            schedule.cancel()
            // Cancelling the periodic work may cancel this very worker. Complete unsubscribe anyway,
            // also when local preference persistence failed.
            if(hadBinding||forceStop)withContext(NonCancellable) {
                try { phone.stop() }catch(_:Exception){ /* Permission loss can already remove the provider subscription. */ }
            }
        }
    }
    private fun checkOwner(owner:String) { if(identity()!=owner)throw AccountException(AccountErrorKind.CANCELLED,"로그인 상태가 바뀌어 걸음 연결을 중단했어요.") }
}
