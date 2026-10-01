package com.bapegg.routinlog.steps

import com.bapegg.routinlog.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class StepEngineTest {
    private val api=FakeApi();private val phone=FakePhone();private val store=Memory();private val schedule=Schedule()
    private var owner:String?="a"
    private val engine=StepEngine(api,phone,store,schedule){owner}
    @Test fun `unsubscribe still runs when clearing local preferences fails`()=runTest {
        engine.connect("a");store.failClear=true
        expectFailure { engine.disconnect("a") }
        assertEquals(1,phone.stops);assertFalse(schedule.active)
    }
    @Test fun `cold worker session expiry clears binding and stops collection`()=runTest {
        engine.connect("a");owner=null
        assertTrue(engine.syncRestoredAccount { throw AccountException(AccountErrorKind.EXPIRED,"expired") })
        assertNull(store.value);assertFalse(schedule.active);assertEquals(1,phone.stops)
    }
    @Test fun `cold worker network failure retains consent for a later retry`()=runTest {
        engine.connect("a")
        assertFalse(engine.syncRestoredAccount { throw AccountException(AccountErrorKind.NETWORK,"offline") })
        assertNotNull(store.value);assertTrue(schedule.active);assertEquals(0,phone.stops)
    }
    @Test fun `daily windows honor DST rather than assuming 24 hours`() {
        val c=StepConnection("id","2026-03-01T00:00:00Z","America/New_York")
        val windows=stepWindows(c,Instant.parse("2026-03-10T17:00:00Z"))
        val changed=windows.single { it.date==LocalDate.parse("2026-03-08") }
        assertEquals(23,Duration.between(changed.from,changed.through).toHours())
        val autumn=stepWindows(c.copy(startedAt="2026-10-28T00:00:00Z"),Instant.parse("2026-11-03T17:00:00Z"))
        val long=autumn.single { it.date==LocalDate.parse("2026-11-01") }
        assertEquals(25,Duration.between(long.from,long.through).toHours())
        assertEquals(9,windows.size)
    }
    @Test fun `a new opt in never reads steps before its server start`() {
        val start="2026-10-01T05:32:14.421Z"
        val windows=stepWindows(StepConnection("id",start,"Asia/Seoul"),Instant.parse("2026-10-01T07:00:00Z"))
        assertEquals(1,windows.size);assertEquals(Instant.parse(start),windows.single().from)
    }
    @Test fun `empty provider response is not uploaded as zero`()=runTest {
        engine.connect("a");engine.sync("a")
        assertTrue(api.saved.isEmpty());assertTrue(schedule.active)
        phone.count=0;engine.sync("a")
        assertTrue(api.saved.isNotEmpty());assertEquals(0L,api.saved.last().items.last().steps)
    }
    @Test fun `background sync does not restart a removed provider subscription`()=runTest {
        engine.connect("a");repeat(2){engine.sync("a")}
        assertEquals(1,phone.starts)
    }
    @Test fun `permission revocation disables further collection and upload`()=runTest {
        engine.connect("a");phone.status=StepAvailability.PERMISSION_REQUIRED
        expectFailure { engine.sync("a") }
        assertNull(store.value);assertFalse(schedule.active);assertEquals(1,phone.stops);assertTrue(api.saved.isEmpty())
    }
    @Test fun `another devices connection invalidates this local binding`()=runTest {
        engine.connect("a");api.active=api.active?.copy(id="other")
        expectFailure { engine.sync("a") }
        assertNull(store.value);assertFalse(schedule.active);assertTrue(api.saved.isEmpty())
    }
    @Test fun `account switch during sensor read never uploads old owners data`()=runTest {
        engine.connect("a");phone.count=100;phone.onRead={owner="b"}
        expectFailure { engine.sync("a") }
        assertTrue(api.saved.isEmpty());engine.bind("b");assertNull(store.value)
    }
    @Test fun `network error during disconnect still turns off local collection`()=runTest {
        engine.connect("a");api.failStop=true
        expectFailure { engine.disconnect("a") }
        assertNull(store.value);assertFalse(schedule.active);assertEquals(1,phone.stops)
    }
    @Test fun `failed connection never schedules unmanaged background capture`()=runTest {
        api.failConnect=true;expectFailure { engine.connect("a") }
        assertNull(store.value);assertFalse(schedule.active);assertEquals(1,phone.stops)
    }
    @Test fun `unsupported device cannot opt in`()=runTest {
        phone.status=StepAvailability.UNSUPPORTED;expectFailure { engine.connect("a") }
        assertEquals(0,phone.starts);assertNull(api.active)
    }
    @Test fun `cancelled read cannot submit a late snapshot`()=runTest {
        engine.connect("a");phone.count=100;val gate=CompletableDeferred<Unit>()
        phone.onRead={withContext(NonCancellable){gate.await()}}
        val job=launch { engine.sync("a") };runCurrent();job.cancel();gate.complete(Unit);job.join()
        assertTrue(api.saved.isEmpty())
    }
    private suspend fun expectFailure(block:suspend()->Unit) { try{block();fail("Expected failure")}catch(_:Exception){} }
    private class Memory:StepBindingStore {var value:StepBinding?=null;var failClear=false;override fun read()=value;override fun write(binding:StepBinding?){if(binding==null&&failClear)error("disk");value=binding}}
    private class Schedule:StepSchedule {var active=false;override fun start(){active=true};override fun cancel(){active=false}}
    private class FakePhone:PhoneSteps {
        var status=StepAvailability.READY;var starts=0;var stops=0;var count:Long?=null;var onRead:suspend()->Unit={}
        override fun availability()=status
        override suspend fun start(){starts++}
        override suspend fun stop(){stops++}
        override suspend fun read(from:Instant,through:Instant):Long?{onRead();return count}
    }
    private class FakeApi:StepDataSource {
        var active:StepConnection?=null;var failStop=false;var failConnect=false;val saved=mutableListOf<StepBatch>()
        override suspend fun stepConnection(owner:String)=StepConnectionState(active,"2026-10-01T07:00:00Z")
        override suspend fun connectSteps(owner:String,id:String):StepConnectionState {if(failConnect)error("network");active=StepConnection(id,"2026-09-29T04:00:00Z","Asia/Seoul");return stepConnection(owner)}
        override suspend fun disconnectSteps(owner:String,id:String){if(failStop)error("network");active=null}
        override suspend fun saveSteps(owner:String,id:String,batch:StepBatch){saved+=batch}
        override suspend fun listSteps(owner:String,from:String,to:String)=emptyList<StepDay>()
    }
}
