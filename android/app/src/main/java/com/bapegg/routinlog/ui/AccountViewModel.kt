package com.bapegg.routinlog.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class AccountUiState(
    val initializing: Boolean = true,
    val busy: Boolean = false,
    val userId: String? = null,
    val profile: ProfileDto? = null,
    val records: List<BodyMeasurementDto> = emptyList(),
    val ready: Boolean = false,
    val routingVersion: Int = 0,
    val error: String? = null,
    val notice: String? = null,
)

/** Only server-confirmed data enters this state. Unsent form drafts stay in the UI. */
class AccountViewModel(private val repository: AccountDataSource) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountUiState())
    val state = mutableState.asStateFlow()

    init { restore() }

    fun restore() = operation {
        val identity = repository.restoreSession()
        if (identity == null) mutableState.value = AccountUiState(initializing = false,
            routingVersion = state.value.routingVersion + 1)
        else loadAccount(identity.userId.toString(), navigate = true)
    }

    fun login(activity: Activity) = operation {
        val identity = repository.login(activity)
        loadAccount(identity.userId.toString(), navigate = true)
    }

    fun refresh(navigate: Boolean = false, onRefreshed: (ProfileDto?) -> Unit = {}) = operation {
        val identity = repository.identity.value ?: repository.restoreSession()
        if (identity == null) {
            mutableState.value = AccountUiState(initializing = false,
                routingVersion = state.value.routingVersion + 1)
        } else {
            loadAccount(identity.userId.toString(), navigate)
            onRefreshed(state.value.profile)
        }
    }

    private suspend fun loadAccount(userId: String, navigate: Boolean) {
        val changedIdentity = state.value.userId != userId
        mutableState.update { if(it.userId!=userId)AccountUiState(initializing=false,busy=true,userId=userId,routingVersion=it.routingVersion+1)
            else it.copy(userId = userId) }
        val profile = repository.getProfile()
        val today = LocalDate.now(profile?.timeZone?.let(ZoneId::of)?:ZoneId.systemDefault())
        val records = if (profile != null) repository.listBody(today.minusDays(365).toString(), today.toString()) else emptyList()
        mutableState.update { it.copy(initializing = false, userId = userId, profile = profile,
            records = records.sortedByDescending { row -> row.date }, ready = true, error = null,
            routingVersion = it.routingVersion + if (navigate || changedIdentity) 1 else 0) }
    }

    fun saveProfile(profile: ProfileDto, onSaved: (ProfileDto) -> Unit) = operation {
        val saved = repository.saveProfile(profile)
        mutableState.update { it.copy(profile = saved, ready = true, notice = "시작 설정을 저장했어요.") }
        onSaved(saved)
    }

    fun loadDate(date: String, onLoaded: (BodyMeasurementDto?) -> Unit) = operation {
        val records = repository.listBody(date, date)
        mutableState.update { current -> current.copy(records =
            (current.records.filterNot { it.date == date } + records).sortedByDescending { it.date }) }
        onLoaded(records.firstOrNull())
    }

    fun loadRange(from: String, to: String) = operation {
        val records = repository.listBody(from, to)
        mutableState.update { current -> current.copy(records =
            (current.records.filterNot { it.date in from..to } + records).sortedByDescending { it.date }) }
    }

    fun saveBody(date: String, draft: BodyMeasurementWriteDto, onSaved: () -> Unit) = operation {
        val saved = repository.saveBody(date, draft)
        mutableState.update { current -> current.copy(records =
            (current.records.filterNot { it.date == date } + saved).sortedByDescending { it.date },
            notice = "측정값을 저장했어요.") }
        onSaved()
    }

    fun deleteBody(date: String, version: Long, onDeleted: () -> Unit) = operation {
        repository.deleteBody(date, version)
        mutableState.update { current -> current.copy(records = current.records.filterNot { it.date == date }, notice = "측정 기록을 삭제했어요.") }
        onDeleted()
    }

    fun logout(onLoggedOut: () -> Unit) = operation {
        repository.logout()
        mutableState.value = AccountUiState(initializing = false, routingVersion = state.value.routingVersion + 1)
        onLoggedOut()
    }

    fun deleteAccount(activity: Activity, onDeleted: () -> Unit) = operation {
        repository.deleteAccount(activity)
        mutableState.value = AccountUiState(initializing = false, routingVersion = state.value.routingVersion + 1)
        onDeleted()
    }

    fun clearNotice() = mutableState.update { it.copy(notice = null) }
    fun clearError() = mutableState.update { it.copy(error = null) }

    private fun operation(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val message = (error as? AccountException)?.userMessage ?: "요청을 완료하지 못했어요. 잠시 후 다시 시도해주세요."
                mutableState.update { current ->
                    if (current.userId != null && repository.identity.value == null)
                        AccountUiState(initializing = false, error = message, routingVersion = current.routingVersion + 1)
                    else current.copy(initializing = false, error = message)
                }
            } finally { mutableState.update { it.copy(initializing = false, busy = false) } }
        }
    }
}
