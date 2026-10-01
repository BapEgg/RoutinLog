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
class ConditionViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val source=Fake()
    private lateinit var model:ConditionViewModel
    private val today=LocalDate.now(ZoneOffset.UTC).toString()
    @Before fun setup(){Dispatchers.setMain(dispatcher);model=ConditionViewModel(source)}
    @After fun cleanup(){model.viewModelScope.cancel();Dispatchers.resetMain()}
    private suspend fun TestScope.load(){model.bind("a","UTC");advanceUntilIdle()}
    @Test fun `unrecorded sleep is null and explicit zero remains zero`()=runTest(dispatcher) {
        load();model.edit { it.copy(values=ConditionValues(soreness="NONE")) };model.save();advanceUntilIdle()
        assertNull(source.values.getValue(today).values.sleepMinutes)
        model.edit { it.copy(hours="0",minutes="0") };model.save();advanceUntilIdle()
        assertEquals(0,source.values.getValue(today).values.sleepMinutes)
        assertNull(source.values.getValue(today).values.fatigue)
    }
    @Test fun `sleep input validation does not send empty or out of range data`()=runTest(dispatcher) {
        load();model.save();advanceUntilIdle();assertTrue(source.values.isEmpty())
        model.edit { it.copy(hours="24",minutes="1") };model.save();advanceUntilIdle();assertTrue(source.values.isEmpty())
        model.edit { it.copy(hours="6",minutes="35") };model.save();advanceUntilIdle()
        assertEquals(395,source.values.getValue(today).values.sleepMinutes)
    }
    @Test fun `drafts survive date switches refresh and network save failure`()=runTest(dispatcher) {
        load();model.edit { it.copy(hours="6",minutes="15") }
        val yesterday=LocalDate.parse(today).minusDays(1).toString()
        model.selectDate(yesterday);advanceUntilIdle();model.edit { it.copy(values=ConditionValues(memo="past draft")) }
        model.selectDate(today);advanceUntilIdle();model.refresh();advanceUntilIdle()
        assertEquals("15",model.state.value.draft?.minutes)
        source.failure=AccountException(AccountErrorKind.NETWORK,"offline");var callback=false
        model.save { callback=true };advanceUntilIdle()
        assertFalse(callback);assertEquals("6",model.state.value.draft?.hours);assertTrue(source.values.isEmpty())
        source.failure=null;model.save();advanceUntilIdle();assertEquals(375,model.state.value.record?.values?.sleepMinutes)
        assertEquals("past draft",model.state.value.drafts[yesterday]?.values?.memo)
    }
    @Test fun `reload only discards draft after explicit request and uses new version`()=runTest(dispatcher) {
        source.values[today]=ConditionDto(today,ConditionValues(memo="old"),0);load()
        model.edit { it.copy(values=it.values.copy(memo="my draft")) }
        source.values[today]=ConditionDto(today,ConditionValues(memo="other device"),1)
        model.refresh();advanceUntilIdle();assertEquals(0L,model.state.value.draft?.version)
        model.save();advanceUntilIdle();assertEquals("my draft",model.state.value.draft?.values?.memo)
        assertEquals("other device",source.values[today]?.values?.memo)
        model.refresh(true);advanceUntilIdle();assertEquals(1L,model.state.value.draft?.version)
        assertEquals("other device",model.state.value.draft?.values?.memo)
    }
    @Test fun `failed delete preserves data and confirmed delete clears only selected date`()=runTest(dispatcher) {
        source.values[today]=ConditionDto(today,ConditionValues(sleepMinutes=400),0);load()
        source.failure=AccountException(AccountErrorKind.NETWORK,"offline");model.delete();advanceUntilIdle();assertNotNull(model.state.value.record)
        source.failure=null;model.delete();advanceUntilIdle();assertNull(model.state.value.record);assertNull(model.state.value.draft?.version)
    }
    @Test fun `sign out cancels late write and removes health drafts`()=runTest(dispatcher) {
        load();model.edit { it.copy(hours="7") };source.gate=CompletableDeferred()
        var callback=false;model.save { callback=true };runCurrent();assertTrue(model.state.value.busy)
        model.bind(null,null);source.gate!!.complete(Unit);advanceUntilIdle()
        assertNull(model.state.value.userId);assertTrue(model.state.value.records.isEmpty());assertTrue(model.state.value.drafts.isEmpty());assertFalse(callback)
    }
    @Test fun `future date leaves selected date and draft unchanged`()=runTest(dispatcher) {
        load();model.edit { it.copy(hours="8") };model.selectDate(LocalDate.parse(today).plusDays(1).toString())
        assertEquals(today,model.state.value.date);assertEquals("8",model.state.value.draft?.hours);assertNotNull(model.state.value.error)
    }
    private class Fake:ConditionDataSource {
        val values=mutableMapOf<String,ConditionDto>();var failure:AccountException?=null;var gate:CompletableDeferred<Unit>?=null
        override suspend fun listConditions(from:String,to:String)=values.values.filter { it.date in from..to }.sortedByDescending { it.date }
        override suspend fun saveCondition(date:String,write:ConditionWrite):ConditionDto {
            gate?.let { withContext(NonCancellable){it.await()} };failure?.let { throw it }
            if(write.version!=values[date]?.version)throw AccountException(AccountErrorKind.CONFLICT,"conflict")
            return ConditionDto(date,write.values,(write.version ?: -1)+1).also { values[date]=it }
        }
        override suspend fun deleteCondition(date:String,version:Long){failure?.let {throw it};check(values[date]?.version==version);values.remove(date)}
    }
}
