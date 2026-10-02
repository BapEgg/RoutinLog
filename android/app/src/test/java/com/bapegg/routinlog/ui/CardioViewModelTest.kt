package com.bapegg.routinlog.ui

import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class CardioViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val source=Fake()
    private lateinit var model:CardioViewModel
    private val today=LocalDate.now(ZoneOffset.UTC).toString()
    @Before fun setup(){Dispatchers.setMain(dispatcher);model=CardioViewModel(source)}
    @After fun cleanup(){model.viewModelScope.cancel();Dispatchers.resetMain()}
    private suspend fun TestScope.start(){model.bind("a","UTC");model.open(today);advanceUntilIdle();model.begin();model.edit { it.copy(activity="걷기",minutes="30") }}
    @Test fun `time only does not invent calories and device zero keeps its meaning`()=runTest(dispatcher) {
        start();model.save();advanceUntilIdle();assertNull(model.state.value.records.single().values.deviceKcal)
        model.begin();model.edit { it.copy(activity="실내 자전거",minutes="10",device=true,kcal="0",deviceName="bike") };model.save();advanceUntilIdle()
        assertEquals(2,source.records.size);assertEquals(0,source.records.values.last().values.deviceKcal!!.signum())
        assertEquals("UNKNOWN",source.records.values.last().values.energyKind)
        model.begin(source.records.values.last());model.edit { it.copy(device=false) };model.save();advanceUntilIdle()
        assertNull(source.records.values.last().values.deviceName);assertNull(source.records.values.last().values.deviceKcal)
    }
    @Test fun `validation rejects missing device provenance and impossible duration`()=runTest(dispatcher) {
        start();model.edit { it.copy(device=true,kcal="200") };model.save();advanceUntilIdle();assertTrue(source.records.isEmpty())
        model.edit { it.copy(device=false,minutes="0") };model.save();advanceUntilIdle();assertTrue(source.records.isEmpty())
        model.edit { it.copy(minutes="1441") };model.save();advanceUntilIdle();assertTrue(source.records.isEmpty())
        model.edit { it.copy(minutes="25",speed="-1") };model.save();advanceUntilIdle();assertTrue(source.records.isEmpty())
        assertNotNull(model.state.value.draft);assertNotNull(model.state.value.error)
    }
    @Test fun `failed save keeps draft and stable id for retry`()=runTest(dispatcher) {
        start();val id=model.state.value.draft!!.id
        source.failure=AccountException(AccountErrorKind.NETWORK,"offline");model.save();advanceUntilIdle()
        assertEquals(id,model.state.value.draft?.id);assertTrue(source.records.isEmpty());assertFalse(model.state.value.busy)
        source.failure=null;model.save();advanceUntilIdle();assertEquals(id,model.state.value.records.single().id);assertNull(model.state.value.draft)
    }
    @Test fun `date switches retain drafts but stale refresh failure never presents previous date records`()=runTest(dispatcher) {
        start();val yesterday=LocalDate.parse(today).minusDays(1).toString()
        model.open(yesterday);advanceUntilIdle();model.begin();model.edit { it.copy(activity="달리기",minutes="10") }
        model.open(today);advanceUntilIdle();assertEquals("걷기",model.state.value.draft?.activity)
        source.failure=AccountException(AccountErrorKind.NETWORK,"offline");model.open(yesterday);advanceUntilIdle()
        assertFalse(model.state.value.loaded);assertTrue(model.state.value.records.isEmpty());assertEquals("달리기",model.state.value.draft?.activity)
        model.open(LocalDate.parse(today).plusDays(1).toString());assertEquals(yesterday,model.state.value.date)
    }
    @Test fun `conflict preserves input until explicit discard and reload`()=runTest(dispatcher) {
        start();model.save();advanceUntilIdle();val r=source.records.values.single();model.begin(r);model.edit { it.copy(minutes="45") }
        source.records[r.id]=r.copy(version=1,values=r.values.copy(minutes=60))
        model.save();advanceUntilIdle();assertEquals("45",model.state.value.draft?.minutes)
        model.refresh();advanceUntilIdle();assertEquals(0L,model.state.value.draft?.version)
        model.discard();model.begin(model.state.value.records.single());assertEquals("60",model.state.value.draft?.minutes);assertEquals(1L,model.state.value.draft?.version)
    }
    @Test fun `signout cancels late writes and clears every health draft`()=runTest(dispatcher) {
        start();source.gate=CompletableDeferred();model.save();runCurrent();assertTrue(model.state.value.busy)
        model.bind("b","UTC");source.gate!!.complete(Unit);advanceUntilIdle()
        assertEquals("b",model.state.value.owner);assertTrue(model.state.value.records.isEmpty());assertTrue(model.state.value.drafts.isEmpty());assertNull(model.state.value.notice)
    }
    @Test fun `failed delete preserves selected record and successful delete removes it`()=runTest(dispatcher) {
        start();model.save();advanceUntilIdle();val record=model.state.value.records.single()
        source.failure=AccountException(AccountErrorKind.NETWORK,"offline");model.delete(record);advanceUntilIdle();assertEquals(1,model.state.value.records.size)
        source.failure=null;model.delete(record);advanceUntilIdle();assertTrue(model.state.value.records.isEmpty())
    }
    private class Fake:CardioDataSource {
        val records=linkedMapOf<String,CardioDto>();var failure:AccountException?=null;var gate:CompletableDeferred<Unit>?=null
        override suspend fun listCardio(owner:String,from:String,to:String):List<CardioDto>{failure?.let { throw it };return records.values.filter { it.date in from..to }}
        override suspend fun saveCardio(owner:String,id:String,write:CardioWrite):CardioDto {
            gate?.let { withContext(NonCancellable){it.await()} };failure?.let { throw it }
            if(records[id]?.version!=write.version)throw AccountException(AccountErrorKind.CONFLICT,"changed")
            return CardioDto(id,write.date,write.values,(write.version ?: -1)+1).also { records[id]=it }
        }
        override suspend fun deleteCardio(owner:String,id:String,version:Long){failure?.let { throw it };records.remove(id)}
    }
}
