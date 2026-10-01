package com.bapegg.routinlog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.domain.NutritionMath
import com.bapegg.routinlog.domain.NutritionLabelDraft
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class MealUiState(
    val userId: String? = null, val loading: Boolean = false, val busy: Boolean = false,
    val loaded: Boolean = false, val date: String = LocalDate.now().toString(),
    val foods: List<FoodDto> = emptyList(), val templates: List<MealTemplateDto> = emptyList(),
    val plan: MealPlanDto? = null, val day: MealDayDto? = null, val draft: MealDraft? = null,
    val error: String? = null, val notice: String? = null,
    val catalog:CatalogSearch?=null,val catalogQuery:String="",val catalogLoading:Boolean=false,
    val catalogError:String?=null,val catalogSelected:CatalogFood?=null,
    val labelImage: String? = null, val labelDraft: NutritionLabelDraft? = null,
    val labelReading: Boolean = false, val labelError: String? = null,
    val foodUrl: String = "", val urlReading: Boolean = false, val urlResult: FoodUrlResult? = null, val urlError: String? = null,
)
data class MealDraft(val id: String, val date: String, val slotId: String, val slotLabel: String,
    val items: List<MealDraftItem>, val note: String, val version: Long?)
data class MealDraftItem(val id: String, val foodId: String, val grams: String, val snapshot: LoggedMealItem)

/** Unsent drafts remain separate from server-confirmed records, scoped to one signed-in account. */
class MealViewModel(private val repository: MealDataSource) : ViewModel() {
    private val mutableState = MutableStateFlow(MealUiState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var readJob: Job? = null
    private var writeJob: Job? = null
    private var zone = ZoneId.systemDefault()
    private var profileRevision: Long? = null
    private var refreshAfterWrite = false
    private var catalogJob:Job?=null
    private var catalogEpoch=0L
    private var labelJob: Job? = null
    private var labelEpoch = 0L
    private var labelCleanup: (() -> Unit)? = null
    private var urlJob: Job? = null
    private var urlEpoch = 0L

    fun clearFoodUrl() {
        urlEpoch++; urlJob?.cancel(); urlJob = null
        mutableState.update { it.copy(foodUrl = "", urlReading = false, urlResult = null, urlError = null) }
    }
    fun previewFoodUrl(url: String) {
        val owner = state.value.userId ?: return
        if (state.value.busy || state.value.urlReading) return
        val value = url.trim()
        if (value.length !in 1..2048 || !value.startsWith("https://", true)) {
            mutableState.update { it.copy(urlResult = null, urlError = "https://로 시작하는 상품 주소를 입력해주세요.") }; return
        }
        clearFoodUrl(); val epoch = urlEpoch
        mutableState.update { it.copy(foodUrl = value, urlReading = true) }
        urlJob = viewModelScope.launch {
            try {
                val result = repository.previewFoodUrl(owner, value); currentCoroutineContext().ensureActive()
                if (epoch == urlEpoch && state.value.userId == owner) mutableState.update { it.copy(urlResult = result) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch == urlEpoch) mutableState.update { it.copy(urlError = (e as? AccountException)?.userMessage ?: "상품 정보를 가져오지 못했어요. 연결을 확인하고 다시 시도해주세요.") }
            } finally { if (epoch == urlEpoch) mutableState.update { it.copy(urlReading = false) } }
        }
    }

    fun clearLabel() {
        labelEpoch++; labelJob?.cancel(); labelJob = null
        labelCleanup?.invoke(); labelCleanup = null
        mutableState.update { it.copy(labelImage = null, labelDraft = null, labelReading = false, labelError = null) }
    }

    fun readLabel(owner: String, image: String, cleanup: () -> Unit = {}, read: suspend () -> NutritionLabelDraft) {
        if (state.value.userId != owner || state.value.busy) { cleanup(); return }
        clearLabel(); labelCleanup = cleanup
        val epoch = labelEpoch
        mutableState.update { it.copy(labelImage = image, labelReading = true) }
        labelJob = viewModelScope.launch {
            try {
                val draft = read(); currentCoroutineContext().ensureActive()
                if (epoch == labelEpoch && state.value.userId == owner) mutableState.update { it.copy(labelDraft = draft) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch == labelEpoch) mutableState.update { it.copy(labelError = "사진의 영양정보를 읽지 못했어요. 글씨가 선명한 사진으로 다시 선택하거나 직접 입력해주세요.") }
            } finally { if (epoch == labelEpoch) mutableState.update { it.copy(labelReading = false) } }
        }
    }

    override fun onCleared() { labelCleanup?.invoke(); labelCleanup = null; super.onCleared() }

    fun bind(userId: String?, timeZone: String? = null, profileVersion: Long? = null) {
        val nextZone = timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val changedProfile = profileRevision != profileVersion || zone != nextZone
        zone = nextZone
        profileRevision = profileVersion
        if (state.value.userId == userId) {
            if (userId != null && changedProfile) {
                if (state.value.busy) refreshAfterWrite = true else refresh()
            }
            return
        }
        refreshAfterWrite = false
        generation++
        clearFoodUrl()
        clearLabel()
        catalogEpoch++;catalogJob?.cancel()
        readJob?.cancel(); writeJob?.cancel()
        mutableState.value = MealUiState(userId = userId, date = LocalDate.now(zone).toString())
        if (userId != null) refresh()
    }

    fun refresh() {
        if (state.value.userId == null || state.value.busy) return
        readJob?.cancel()
        val epoch = ++generation
        val date = state.value.date
        mutableState.update { it.copy(loading = true, error = null) }
        readJob = viewModelScope.launch {
            try {
                coroutineScope {
                    val foods = async { repository.listFoods() }
                    val templates = async { repository.listMealTemplates() }
                    val plan = async { repository.getMealPlan() }
                    val day = async { repository.getMealDay(date) }
                    val values = MealUiState(foods = foods.await(), templates = templates.await(), plan = plan.await(), day = day.await())
                    if (epoch == generation) mutableState.update { it.copy(foods = values.foods,
                        templates = values.templates, plan = values.plan, day = values.day, loaded = true) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (epoch == generation) showError(error) }
            finally { if (epoch == generation) mutableState.update { it.copy(loading = false) } }
        }
    }

    fun loadDate(date: String) {
        if (state.value.busy) return
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
        if (parsed == null || parsed !in LocalDate.of(1900, 1, 1)..LocalDate.now(zone).plusDays(14)) {
            fail("오늘부터 14일 뒤까지 계획을 확인할 수 있어요."); return
        }
        mutableState.update { it.copy(date = date, day = null, draft = null, loaded = false) }
        refresh()
    }

    fun beginMeal(slotId: String, label: String) {
        if (!state.value.loaded || state.value.busy || state.value.loading) return
        val current = state.value
        if(LocalDate.parse(current.date)>LocalDate.now(zone)){fail("아직 오지 않은 날짜에는 섭취 기록을 남길 수 없어요.");return}
        val record = current.day?.items?.firstOrNull { it.slotId == slotId }
        val planned=current.day?.plannedMeals.orEmpty().firstOrNull { it.slotId==slotId }
        val templateId = current.plan?.slots?.firstOrNull { it.id == slotId }?.templateId
        val template = current.templates.firstOrNull { it.id == templateId }
        if (record?.status != "EATEN" && planned==null && templateId != null &&
            (template == null || template.items.any { item -> current.foods.none { it.id == item.foodId } })) {
            fail("저장 식단의 음식 정보가 변경됐어요. 다시 불러온 뒤 기록해주세요.")
            return
        }
        val items = if (record?.status == "EATEN") record.items.map { it.toDraft() }
        else if(planned!=null)planned.items.map { it.toDraft() }
        else template?.items.orEmpty().map { item ->
            current.foods.first { it.id == item.foodId }.snapshot(item.grams).toDraft()
        }
        mutableState.update { it.copy(draft = MealDraft(record?.id ?: newId(), current.date, slotId,
            record?.slotLabel ?: label, items, record?.note.orEmpty(), record?.version), error = null) }
    }

    fun setDraftGrams(itemId: String, value: String) = editDraft { draft -> draft.copy(items = draft.items.map { if (it.id == itemId) it.copy(grams = value) else it }) }
    fun setDraftNote(value: String) = editDraft { it.copy(note = value) }
    fun removeDraftItem(itemId: String) = editDraft { it.copy(items = it.items.filterNot { item -> item.id == itemId }) }
    fun addDraftFood(foodId: String) {
        val food = state.value.foods.firstOrNull { it.id == foodId } ?: return
        editDraft { draft -> if (draft.items.any { it.foodId == foodId }) draft
            else draft.copy(items = draft.items + food.snapshot(food.basisGrams).toDraft()) }
    }
    fun discardDraft() { if (!state.value.busy) mutableState.update { it.copy(draft = null, error = null) } }
    private fun editDraft(change: (MealDraft) -> MealDraft) {
        if (!state.value.busy) mutableState.update { it.copy(draft = it.draft?.let(change)) }
    }
    fun draftTotals(): NutritionTotals = NutritionMath.totals(state.value.draft?.items.orEmpty().map { item ->
        NutritionMath.atGrams(item.snapshot.nutrition, item.snapshot.basisGrams, validAmount(item.grams))
    })

    fun saveMeal(onSaved: () -> Unit) {
        val draft = state.value.draft ?: return
        if (draft.items.isEmpty()) { fail("먹은 음식을 한 가지 이상 추가해주세요."); return }
        if (draft.items.any { it.grams.isNotBlank() && validAmount(it.grams) == null }) {
            fail("양은 0보다 큰 숫자로 소수 둘째 자리까지 입력해주세요. 모르는 양은 비워두세요."); return
        }
        if (draft.note.length > 1000) { fail("메모는 1,000자까지 입력해주세요."); return }
        mutate {
            val saved = repository.saveMeal(draft.id, MealWrite(draft.date, draft.slotId, draft.slotLabel, "EATEN",
                draft.items.map { MealItemWrite(it.id, it.foodId, validAmount(it.grams)) }, draft.note.takeIf(String::isNotBlank), draft.version))
            currentCoroutineContext().ensureActive()
            confirmMeal(saved)
            mutableState.update { it.copy(draft = null, notice = "먹은 식사를 저장했어요.") }
            onSaved()
        }
    }
    fun skipMeal(slotId: String, label: String, onSaved: () -> Unit = {}) {
        val current = state.value
        if(LocalDate.parse(current.date)>LocalDate.now(zone)){fail("아직 오지 않은 날짜에는 섭취 기록을 남길 수 없어요.");return}
        val record = current.day?.items?.firstOrNull { it.slotId == slotId }
        mutate {
            val saved = repository.saveMeal(record?.id ?: newId(), MealWrite(current.date, slotId,
                record?.slotLabel ?: label, "SKIPPED", emptyList(), version = record?.version))
            currentCoroutineContext().ensureActive()
            confirmMeal(saved)
            mutableState.update { it.copy(draft = it.draft?.takeUnless { draft -> draft.slotId == slotId && draft.date == current.date }, notice = "먹지 않은 끼니로 기록했어요.") }
            onSaved()
        }
    }
    fun deleteMeal(record: MealDto, onSaved: () -> Unit = {}) = mutate {
        repository.deleteMeal(record.id, record.version)
        currentCoroutineContext().ensureActive()
        mutableState.update { current -> current.copy(day = current.day?.let { day ->
            withItems(day, day.items.filterNot { it.id == record.id }) }, draft = current.draft?.takeUnless { it.id == record.id }, notice = "식사 기록을 삭제했어요.") }
        onSaved()
    }
    fun saveFood(id: String, write: FoodWrite, onSaved: () -> Unit) = mutate {
        val saved = repository.saveFood(id, write)
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(foods = it.foods.filterNot { food -> food.id == id } + saved, notice = "음식을 저장했어요.") }
        onSaved()
    }
    fun searchCatalog(query:String,more:Boolean=false) {
        val owner=state.value.userId ?: return
        if(state.value.busy||(more&&state.value.catalogLoading))return
        val q=query.trim()
        if(q.length !in 1..80){mutableState.update { it.copy(catalogError="검색어를 1~80자로 입력해주세요.") };return}
        val previous=state.value.catalog.takeIf { more&&q==state.value.catalogQuery }
        if(more&&previous?.hasMore!=true)return
        val page=if(previous==null)0 else previous.page+1
        catalogJob?.cancel();val epoch=++catalogEpoch
        mutableState.update { it.copy(catalogLoading=true,catalogQuery=q,catalogError=null,catalogSelected=null,catalog=previous) }
        catalogJob=viewModelScope.launch {
            try {
                val found=repository.searchCatalog(owner,q,page);currentCoroutineContext().ensureActive()
                if(epoch==catalogEpoch&&state.value.userId==owner)mutableState.update { it.copy(catalog=found.copy(items=(previous?.items.orEmpty()+found.items).distinctBy { item->item.id })) }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(epoch==catalogEpoch)mutableState.update { it.copy(catalogError=(e as? AccountException)?.userMessage ?: "식품 검색을 완료하지 못했어요. 다시 시도해주세요.") }}
            finally { if(epoch==catalogEpoch)mutableState.update { it.copy(catalogLoading=false) } }
        }
    }
    fun selectCatalog(food:CatalogFood?) { if(!state.value.busy)mutableState.update { it.copy(catalogSelected=food) } }
    fun saveCatalog(preparation:String,onSaved:()->Unit) {
        val selected=state.value.catalogSelected ?: return;val owner=state.value.userId ?: return
        if(selected.importBlockReason!=null||selected.basisUnit!="g"||selected.basisAmount==null){fail(selected.importBlockReason ?: "g 기준 영양정보를 확인해주세요.");return}
        if(preparation !in setOf("RAW","COOKED","AS_SOLD","UNKNOWN"))return
        mutate {
            val saved=repository.saveCatalogFood(owner,selected.id,CatalogSave(selected.revision,preparation))
            currentCoroutineContext().ensureActive()
            mutableState.update { it.copy(foods=it.foods.filterNot { f->f.id==saved.id }+saved,catalogSelected=null,notice="내 음식에 추가했어요. 이미 추가한 식품은 중복 저장하지 않아요.") }
            onSaved()
        }
    }
    fun deleteFood(food: FoodDto, onDeleted: () -> Unit = {}) = mutate {
        repository.deleteFood(food.id, food.version)
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(foods = it.foods.filterNot { row -> row.id == food.id }, notice = "음식을 삭제했어요. 지난 식사 기록은 유지돼요.") }
        onDeleted()
    }
    fun saveTemplate(id: String, write: MealTemplateWrite, onSaved: () -> Unit) = mutate {
        val saved = repository.saveMealTemplate(id, write)
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(templates = it.templates.filterNot { row -> row.id == id } + saved, notice = "자주 먹는 식사를 저장했어요.") }
        onSaved()
    }
    fun deleteTemplate(template: MealTemplateDto, onDeleted: () -> Unit = {}) = mutate {
        repository.deleteMealTemplate(template.id, template.version)
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(templates = it.templates.filterNot { row -> row.id == template.id }, notice = "저장한 식사를 삭제했어요.") }
        onDeleted()
    }
    fun savePlan(write: MealPlanWrite, onSaved: () -> Unit) = mutate {
        val saved = repository.saveMealPlan(write)
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(plan = saved, notice = "기본 식단을 저장했어요.") }
        onSaved()
    }
    fun clearError() = mutableState.update { it.copy(error = null) }
    fun removeDayPlan(plan:PlannedMeal) {
        val date=state.value.date
        mutate {
            repository.deleteMealDayPlan(date,plan.slotId,plan.version);currentCoroutineContext().ensureActive()
            mutableState.update { s->s.copy(day=s.day?.copy(plannedMeals=s.day.plannedMeals.orEmpty().filterNot { it.slotId==plan.slotId }),draft=s.draft?.takeUnless { it.slotId==plan.slotId&&it.date==date },notice="이날 식사 계획을 해제했어요.") }
        }
    }
    fun clearNotice() = mutableState.update { it.copy(notice = null) }

    private fun confirmMeal(saved: MealDto) {
        mutableState.update { current -> current.copy(day = current.day?.let { day ->
            withItems(day, day.items.filterNot { it.id == saved.id } + saved) }) }
    }
    private fun withItems(day: MealDayDto, items: List<MealDto>) = day.copy(items = items,
        totals = NutritionMath.totals(NutritionMath.loggedItems(items.filter { it.status == "EATEN" }.flatMap { it.items })))

    private fun mutate(block: suspend () -> Unit) {
        if (state.value.userId == null || !state.value.loaded || state.value.busy || state.value.loading) return
        val epoch = generation
        mutableState.update { it.copy(busy = true, error = null) }
        writeJob = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (epoch == generation) showError(error) }
            finally {
                if (epoch == generation) {
                    mutableState.update { it.copy(busy = false) }
                    if (refreshAfterWrite) { refreshAfterWrite = false; refresh() }
                }
            }
        }
    }
    private fun showError(error: Exception) = fail((error as? AccountException)?.userMessage ?: "요청을 완료하지 못했어요. 연결을 확인한 뒤 다시 시도해주세요.")
    private fun fail(message: String) = mutableState.update { it.copy(error = message) }
    private fun FoodDto.snapshot(grams: BigDecimal) = LoggedMealItem(newId(), id, name, brand, basisGrams, nutrition, preparation, sourceNote, grams,source)
    private fun LoggedMealItem.toDraft() = MealDraftItem(id, foodId, grams?.stripTrailingZeros()?.toPlainString().orEmpty(), this)
    private fun validAmount(text: String): BigDecimal? = text.trim().takeIf { it.length <= 20 }?.toBigDecimalOrNull()?.takeIf {
        it.signum() > 0 && it <= BigDecimal("100000") && it.scale() <= 2
    }
    private fun newId() = UUID.randomUUID().toString()
}
