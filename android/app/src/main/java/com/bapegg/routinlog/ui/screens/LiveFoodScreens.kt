package com.bapegg.routinlog.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.domain.NutritionMath
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

/** Authenticated food screens. Original food labels and historical meal snapshots stay separate. */
@Composable
internal fun LiveFoodScreens(id: String, ui: PreviewSession, model: MealViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    if (state.error != null) UiCard {
        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
        UiButton("다시 불러오기", model::refresh, primary = false, enabled = !state.busy && !state.loading)
    }
    if (!state.loaded) {
        UiCard {
            SectionTitle(if (state.loading) "내 식단을 불러오고 있어요" else "내 식단을 확인해주세요")
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DeepBlue)
            else UiButton("식단 불러오기", model::refresh, enabled = !state.busy)
        }
    } else when (id) {
        "F01" -> LiveMealDay(ui, model, state)
        "F02" -> LiveMealPlan(ui, model, state)
        "F03" -> LiveMealEditor(ui, model, state)
        "F04" -> LiveMealAmount(ui, model, state)
        "F06" -> LiveFoodLibrary(ui, model, state)
        "F07" -> LiveFoodDetail(ui, state)
        "F08" -> {
            UiCard {
                SectionTitle("내가 확인한 영양정보로 등록해요")
                BodyText("제품 영양성분표의 기준량과 영양정보를 그대로 입력해주세요.")
                MutedText("표시되지 않은 영양소는 빈칸으로 남겨요. 0으로 계산하지 않아요.")
                UiButton("음식 직접 등록", { openFoodForm(ui, null, "F06") })
            }
            MutedText("식품 검색·사진 인식·상품 주소 등록은 아직 제공하지 않아요.")
        }
        "F13" -> LiveFoodForm(ui, model, state)
        "F14" -> LiveTemplateForm(ui, model, state)
        else -> UiCard { SectionTitle("내 음식부터 준비해볼까요?"); UiButton("내 음식 보기", { ui.go("F06") }) }
    }
    if (state.busy || (state.loading && state.loaded)) Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        UiCard { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(Modifier.size(26.dp), color = DeepBlue, strokeWidth = 3.dp)
            Text(if (state.busy) "저장 내용을 확인하고 있어요" else "식단을 불러오고 있어요")
        } }
    }
}

@Composable
private fun LiveMealDay(ui: PreviewSession, model: MealViewModel, state: MealUiState) {
    var dateOpen by remember { mutableStateOf(false) }
    var dateText by remember(state.date) { mutableStateOf(state.date) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var pendingDate by remember { mutableStateOf<String?>(null) }
    var pendingMeal by remember { mutableStateOf<MealSlot?>(null) }
    var discard by remember { mutableStateOf(false) }
    var skip by remember { mutableStateOf<MealSlot?>(null) }
    var remove by remember { mutableStateOf<MealDto?>(null) }
    var cancelPlan by remember { mutableStateOf<PlannedMeal?>(null) }
    val day = state.day?.takeIf { it.date == state.date }
    val future=state.date>ui.today().toString()
    fun changeDate(date: String) {
        if (date == state.date) { dateOpen = false; return }
        if (state.draft != null) pendingDate = date else model.loadDate(date)
        dateOpen = false
    }
    fun begin(slot: MealSlot, directly: Boolean = false) {
        if (state.draft != null) {
            if (state.draft.slotId == slot.id && state.draft.date == state.date) ui.go("F03")
            else pendingMeal = slot
        } else {
            model.beginMeal(slot.id, slot.label)
            if (directly) model.saveMeal { ui.go("F01") } else ui.go("F03")
        }
    }
    UiCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { MutedText(if(future)"식사 계획일" else "식사 기록일"); Text(state.date, style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = { dateOpen = !dateOpen }) { Text(if (dateOpen) "접기" else "날짜 변경") }
        }
        if (dateOpen) {
            Chips(listOf("오늘", "어제"), when (state.date) { ui.today().toString() -> "오늘"; ui.today().minusDays(1).toString() -> "어제"; else -> "" }) {
                changeDate(if (it == "오늘") ui.today().toString() else ui.today().minusDays(1).toString())
            }
            FoodField("기록 날짜", dateText, { dateText = it; dateError = null }, hint = "연도-월-일")
            FoodError(dateError)
            UiButton("이 날짜 보기", {
                val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
                if (date == null || date !in LocalDate.of(1900, 1, 1)..ui.today().plusDays(14)) dateError = "1900년부터 오늘의 14일 뒤까지 확인할 수 있어요."
                else changeDate(date.toString())
            }, primary = false)
        }
    }
    if (day == null) {
        UiCard { SectionTitle("이 날짜의 기록을 불러오지 못했어요"); UiButton("다시 불러오기", model::refresh) }
        return
    }
    LiveNutritionPanel("기록한 섭취량", day.totals, day.target,
        emptyLabel = if (day.items.any { it.status == "SKIPPED" }) "먹은 식사 기록 없음" else "아직 기록 전")
    if (state.draft != null) UiCard {
        Badge("작성 중")
        Text("${state.draft.date} · ${state.draft.slotLabel}", fontWeight = FontWeight.SemiBold)
        UiButton("작성하던 식사 이어서", { ui.go("F03") })
        TextButton(onClick = { discard = true }) { Text("작성 내용 버리기") }
    }
    if (state.foods.isEmpty()) UiCard {
        SectionTitle("자주 먹는 음식부터 하나씩")
        BodyText("음식을 등록하고 식단으로 묶어두면, 다음부터 먹은 양만 바꿀 수 있어요.")
        UiButton("첫 음식 등록", { openFoodForm(ui, null, "F01") })
    }
    SectionTitle("나의 끼니", "기본 식단 설정", { ui.go("F02") })
    val slots = state.plan?.slots.orEmpty().toMutableList()
    day.items.filter { record -> slots.none { it.id == record.slotId } }.forEach { slots += MealSlot(it.slotId, it.slotLabel) }
    day.plannedMeals.orEmpty().filter { planned->slots.none { it.id==planned.slotId } }.forEach { slots+=MealSlot(it.slotId,it.slotLabel) }
    if(future)MutedText("미리 정한 식사 계획이에요. 실제 섭취 기록은 해당 날짜부터 남길 수 있어요.")
    slots.forEach { slot ->
        val record = day.items.firstOrNull { it.slotId == slot.id }
        val template = state.templates.firstOrNull { it.id == slot.templateId }
        val planned=day.plannedMeals.orEmpty().firstOrNull { it.slotId==slot.id }
        UiCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(record?.slotLabel ?: slot.label, style = MaterialTheme.typography.titleMedium)
                if (record?.status == "EATEN") Badge("기록 완료") else Surface(
                    color = Silver, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                ) {
                    Text(if (record?.status == "SKIPPED") "먹지 않음" else "확인 전",
                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Muted,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            when (record?.status) {
                "EATEN" -> {
                    BodyText(record.items.joinToString(" · ") { it.name })
                    MutedText(nutrientText(record.totals.kcal, "kcal", "먹은 양 정보 없음"))
                    if (record.items.any { it.grams == null }) MutedText("양을 모르는 음식이 있어요. 전체 섭취량은 아직 알 수 없어요.")
                }
                "SKIPPED" -> MutedText("이 끼니는 먹지 않은 것으로 남겼어요.")
                else -> {
                    MutedText(planned?.name ?: template?.name ?: "먹은 음식을 선택해 기록해주세요.")
                    if(planned!=null){ Badge("이날 정한 식사");planned.items.forEach { MutedText("${it.name} · ${it.grams?.stripTrailingZeros()?.toPlainString()} g") } }
                }
            }
            if(planned!=null&&record==null&&state.date>=ui.today().toString())UiButton("이날 식사 계획 해제",{cancelPlan=planned},false)
            if (!future && record == null && (template != null||planned!=null)) {
                UiButton("이대로 먹었어요", { begin(slot, directly = true) })
                UiButton("음식·양 바꿔 기록", { begin(slot) }, primary = false)
            } else if(!future)UiButton(if (record?.status == "EATEN") "구성·양 수정" else "먹은 음식 기록", { begin(slot) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (!future && record?.status != "SKIPPED") TextButton(onClick = {
                    if (record?.status == "EATEN" || state.draft?.slotId == slot.id) skip = slot else model.skipMeal(slot.id, slot.label)
                }) { Text("먹지 않았어요") }
                if (record != null) TextButton(onClick = { remove = record }) { Text("기록 지우기", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    UiButton("내 음식 관리", { ui.go("F06") }, primary = false)
    UiButton("서버에서 새로 불러오기", model::refresh, primary = false)
    MutedText("확인 전인 끼니는 섭취량에 포함하지 않아요. 식단 설정을 바꿔도 지난 기록은 그대로 남아요.")
    if (discard) FoodConfirm("작성 내용을 버릴까요?", "아직 저장하지 않은 식사의 수정 내용이 사라져요.", "버리기", { discard = false }) { model.discardDraft(); discard = false }
    cancelPlan?.let { plan->FoodConfirm("이날 식사 계획을 해제할까요?","${state.date} ${plan.slotLabel}에 적용한 계획을 해제해요. 저장한 기본 식단을 다시 불러오고 작성 중인 해당 끼니는 비워요.","계획 해제",{cancelPlan=null}) {cancelPlan=null;model.removeDayPlan(plan)} }
    pendingDate?.let { date -> FoodConfirm("다른 날짜로 이동할까요?", "작성 중인 식사를 저장하지 않고 이동해요.", "이동", { pendingDate = null }) { pendingDate = null; model.loadDate(date) } }
    pendingMeal?.let { slot -> FoodConfirm("다른 끼니를 기록할까요?", "작성 중인 식사의 저장하지 않은 변경은 사라져요.", "새로 작성", { pendingMeal = null }) {
        pendingMeal = null; model.discardDraft(); model.beginMeal(slot.id, slot.label); ui.go("F03")
    } }
    skip?.let { slot -> FoodConfirm("먹지 않은 것으로 바꿀까요?",
        "${slot.label}를 ‘먹지 않음’으로 기록해요. 이미 기록한 음식은 지워져요." + if (state.draft != null) " 작성 중인 식사의 저장하지 않은 변경도 사라져요." else "",
        "바꾸기", { skip = null }) { skip = null; model.skipMeal(slot.id, slot.label) } }
    remove?.let { record -> FoodConfirm("이 식사 기록을 지울까요?",
        "${record.date} ${record.slotLabel} 기록만 삭제해요. 저장 식단은 유지돼요." + if (state.draft != null) " 작성 중인 식사의 저장하지 않은 변경도 사라져요." else "",
        "기록 삭제", { remove = null }) { remove = null; model.deleteMeal(record) } }
}

@Composable
private fun LiveMealPlan(ui: PreviewSession, model: MealViewModel, state: MealUiState) {
    var slots by remember { mutableStateOf(state.plan?.slots.orEmpty()) }
    val version = remember { state.plan?.version }
    var choosing by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    SectionTitle("내 생활에 맞는 끼니")
    MutedText("아침·간식·운동 후 식사처럼 이름과 개수를 자유롭게 정해요. 최대 10끼까지 만들 수 있어요.")
    slots.forEachIndexed { index, slot -> key(slot.id) {
        UiCard {
            FoodField("${index + 1}번째 끼니 이름", slot.label, { text -> slots = slots.map { if (it.id == slot.id) it.copy(label = text) else it }; error = null })
            val template = state.templates.firstOrNull { it.id == slot.templateId }
            UiRow("기본 식단", template?.name ?: "정해두지 않음", onClick = { choosing = slot.id })
            if (slots.size > 1) TextButton(onClick = { slots = slots.filterNot { it.id == slot.id } }) { Text("이 끼니 빼기", color = MaterialTheme.colorScheme.error) }
        }
    } }
    UiButton("끼니 추가", { slots = slots + MealSlot(UUID.randomUUID().toString(), "") }, primary = false, enabled = slots.size < 10)
    FoodError(error)
    UiButton("기본 식단 저장", {
        val cleaned = slots.map { it.copy(label = it.label.trim()) }
        if (cleaned.isEmpty() || cleaned.any { !validText(it.label, 80) } || cleaned.map { it.label }.distinct().size != cleaned.size)
            error = "끼니 이름을 1~80자로 입력하고, 서로 다른 이름을 사용해주세요."
        else model.savePlan(MealPlanWrite(cleaned, version)) { ui.go("F01") }
    })
    MutedText("끼니를 없애거나 식단을 바꿔도 이미 기록한 식사는 바뀌지 않아요.")
    SectionTitle("저장한 식단")
    if (state.templates.isEmpty()) UiCard { BodyText("자주 먹는 음식과 양을 식단으로 묶어두세요.") }
    state.templates.forEach { template -> UiCard {
        UiRow(template.name, "음식 ${template.items.size}개", icon = "Utensils", onClick = { openTemplate(ui, template.id) })
    } }
    UiButton(if (state.foods.isEmpty()) "먼저 음식 등록하기" else "새 식단 만들기", {
        if (state.foods.isEmpty()) openFoodForm(ui, null, "F02") else openTemplate(ui, null)
    }, primary = false)
    choosing?.let { slotId -> FoodSheet("기본으로 보여줄 식단", { choosing = null }) {
        Choice("정해두지 않음", "먹은 음식을 매번 직접 선택해요.", slots.firstOrNull { it.id == slotId }?.templateId == null) {
            slots = slots.map { if (it.id == slotId) it.copy(templateId = null) else it }; choosing = null
        }
        state.templates.forEach { template -> Choice(template.name, "음식 ${template.items.size}개", slots.firstOrNull { it.id == slotId }?.templateId == template.id) {
            slots = slots.map { if (it.id == slotId) it.copy(templateId = template.id) else it }; choosing = null
        } }
        if (state.templates.isEmpty()) MutedText("아직 저장한 식단이 없어요. 음식 등록 후 식단을 만들어주세요.")
    } }
}

@Composable
private fun LiveMealEditor(ui: PreviewSession, model: MealViewModel, state: MealUiState) {
    val draft = state.draft
    if (draft == null) { UiCard { BodyText("기록할 끼니를 먼저 선택해주세요."); UiButton("오늘 식단으로", { ui.go("F01") }) }; return }
    var removeItem by remember { mutableStateOf<String?>(null) }
    var removeRecord by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    Badge("${draft.date} · ${draft.slotLabel}")
    LiveNutritionPanel("이 식사의 섭취량", model.draftTotals(), emptyLabel = "음식 추가 전")
    SectionTitle("먹은 음식", "음식 추가", { ui.go("F06") })
    if (draft.items.isEmpty()) UiCard { BodyText("내 음식에서 오늘 먹은 음식을 골라주세요."); UiButton("음식 선택하기", { ui.go("F06") }) }
    draft.items.forEach { item -> UiCard {
        FoodIdentity(item.snapshot.name, item.snapshot.brand, item.snapshot.preparation)
        UiRow("먹은 양", if (item.grams.isBlank()) "양 정보 없음" else "${item.grams} g", onClick = { ui.set("liveFood.itemId", item.id); ui.go("F04") })
        TextButton(onClick = { removeItem = item.id }) { Text("이 식사에서 빼기", color = MaterialTheme.colorScheme.error) }
    } }
    FoodField("식사 메모 · 선택", draft.note, model::setDraftNote, multiline = true)
    MutedText("이 날짜의 식사만 바뀌어요. 저장 식단과 이전 식사 기록은 그대로 유지돼요.")
    if (draft.version != null) MutedText("기록 당시의 음식 영양정보를 기준으로 계산해요. 내 음식 정보를 수정해도 이 기록의 기준은 바뀌지 않아요.")
    UiButton("이 식사 기록 저장", { model.saveMeal { ui.go("F01") } }, enabled = draft.items.isNotEmpty())
    UiButton("작성 취소", { discard = true }, primary = false)
    val record = state.day?.items?.firstOrNull { it.id == draft.id }
    if (record != null) TextButton(onClick = { removeRecord = true }) { Text("저장된 식사 기록 삭제", color = MaterialTheme.colorScheme.error) }
    if (state.error != null) UiButton("변경 버리고 최신 기록 확인", { discard = true }, primary = false)
    removeItem?.let { itemId -> FoodConfirm("이 음식을 뺄까요?", "이 식사의 구성에서만 빼요. 내 음식 목록에는 남아 있어요.", "빼기", { removeItem = null }) { model.removeDraftItem(itemId); removeItem = null } }
    if (discard) FoodConfirm("작성 내용을 버릴까요?", "저장하지 않은 변경을 버리고 식사 목록으로 돌아가요.", "버리고 돌아가기", { discard = false }) {
        model.discardDraft(); ui.go("F01"); model.refresh(); discard = false
    }
    if (removeRecord && record != null) FoodConfirm("이 식사 기록을 삭제할까요?", "${record.date} ${record.slotLabel}의 실제 섭취 기록을 삭제해요.", "삭제", { removeRecord = false }) {
        removeRecord = false; model.deleteMeal(record) { model.discardDraft(); ui.go("F01") }
    }
}

@Composable
private fun LiveMealAmount(ui: PreviewSession, model: MealViewModel, state: MealUiState) {
    val item = state.draft?.items?.firstOrNull { it.id == ui.get("liveFood.itemId") }
    if (item == null) { UiCard { BodyText("수정할 음식을 먼저 선택해주세요."); UiButton("식사 구성으로", { ui.go("F03") }) }; return }
    var grams by remember(item.id) { mutableStateOf(item.grams) }
    var error by remember { mutableStateOf<String?>(null) }
    val amount = positiveAmount(grams)
    UiCard {
        FoodIdentity(item.snapshot.name, item.snapshot.brand, item.snapshot.preparation)
        MutedText("원래 영양정보 · ${foodNumber(item.snapshot.basisGrams)} g 기준")
        FoodField("실제 먹은 양", grams, { grams = it; error = null }, suffix = "g", numeric = true)
        Choice("먹은 양을 모르겠어요", "음식은 남기고 영양 합계는 정보 없음으로 표시해요.", grams.isBlank()) {
            grams = if (grams.isBlank()) foodNumber(item.snapshot.basisGrams) else ""; error = null
        }
    }
    val preview = NutritionMath.atGrams(item.snapshot.nutrition, item.snapshot.basisGrams, amount)
    LiveNutritionPanel("입력한 양의 영양정보", NutritionMath.totals(listOf(preview)))
    FoodError(error)
    UiButton("이 양으로 적용", {
        if (grams.isNotBlank() && amount == null) error = "먹은 양은 0보다 크고 100,000g 이하인 수로, 소수점 두 자리까지 입력해주세요."
        else { model.setDraftGrams(item.id, amount?.toPlainString().orEmpty()); ui.go("F03") }
    })
    MutedText("식사 구성에만 적용돼요. 다음 화면에서 기록을 저장해주세요.")
}

@Composable
private fun LiveFoodLibrary(ui: PreviewSession, model: MealViewModel, state: MealUiState) {
    var query by remember { mutableStateOf("") }
    val adding = state.draft != null
    if (adding) Badge("${state.draft!!.slotLabel}에 음식 추가")
    FoodField("내 음식 검색", query, { query = it }, hint = "음식명 또는 브랜드")
    UiButton("새 음식 등록", { openFoodForm(ui, null, "F06") }, primary = false)
    val results = state.foods.filter { query.isBlank() || it.name.contains(query, true) || it.brand.orEmpty().contains(query, true) }
    if (results.isEmpty()) UiCard {
        SectionTitle(if (state.foods.isEmpty()) "아직 등록한 음식이 없어요" else "찾는 음식이 없어요")
        BodyText("제품의 영양정보를 확인해 직접 등록할 수 있어요.")
    }
    results.forEach { food -> UiCard {
        FoodIdentity(food.name, food.brand, food.preparation)
        MutedText("${foodNumber(food.basisGrams)} g당 ${valueText(food.nutrition.kcal, "kcal")}")
        UiButton(if (adding) "이 음식 추가" else "음식 정보 보기", {
            if (adding) { model.addDraftFood(food.id); ui.go("F03") }
            else { ui.set("liveFood.foodId", food.id); ui.go("F07") }
        }, primary = adding)
        if (adding) TextButton(onClick = { openFoodForm(ui, food.id, "F06") }) { Text("원본 정보 수정") }
    } }
    MutedText("내가 직접 입력한 음식이에요. 같은 음식이라도 조리 전·후와 제품 기준량을 확인해주세요.")
}

@Composable
private fun LiveFoodDetail(ui: PreviewSession, state: MealUiState) {
    val food = state.foods.firstOrNull { it.id == ui.get("liveFood.foodId") }
    if (food == null) { UiCard { BodyText("이 음식 정보를 찾을 수 없어요."); UiButton("내 음식으로", { ui.go("F06") }) }; return }
    UiCard { FoodIdentity(food.name, food.brand, food.preparation); Badge("직접 입력"); MutedText("${foodNumber(food.basisGrams)} g 기준") }
    LiveNutritionPanel("원본 영양정보", NutritionMath.totals(listOf(food.nutrition)))
    food.sourceNote?.takeIf { it.isNotBlank() }?.let { UiCard { SectionTitle("출처 메모"); BodyText(it) } }
    UiButton("음식 정보 수정", { openFoodForm(ui, food.id, "F06") })
}

@Composable
private fun LiveFoodForm(ui: PreviewSession, model: MealViewModel, state: MealUiState) {
    val selectedId = ui.get("liveFood.foodId")
    val existing = state.foods.firstOrNull { it.id == selectedId }
    if (selectedId.isNotBlank() && existing == null) { UiCard { BodyText("수정할 음식을 찾을 수 없어요."); UiButton("내 음식으로", { ui.go("F06") }) }; return }
    val id = remember(selectedId) { selectedId.ifBlank { UUID.randomUUID().toString() } }
    val version = remember(id) { existing?.version }
    var name by remember(id) { mutableStateOf(existing?.name.orEmpty()) }
    var brand by remember(id) { mutableStateOf(existing?.brand.orEmpty()) }
    var basis by remember(id) { mutableStateOf(existing?.basisGrams?.let(::foodNumber).orEmpty()) }
    var preparation by remember(id) { mutableStateOf(existing?.preparation ?: "UNKNOWN") }
    var kcal by remember(id) { mutableStateOf(existing?.nutrition?.kcal?.let(::foodNumber).orEmpty()) }
    var carbs by remember(id) { mutableStateOf(existing?.nutrition?.carbsG?.let(::foodNumber).orEmpty()) }
    var protein by remember(id) { mutableStateOf(existing?.nutrition?.proteinG?.let(::foodNumber).orEmpty()) }
    var fat by remember(id) { mutableStateOf(existing?.nutrition?.fatG?.let(::foodNumber).orEmpty()) }
    var fiber by remember(id) { mutableStateOf(existing?.nutrition?.fiberG?.let(::foodNumber).orEmpty()) }
    var source by remember(id) { mutableStateOf(existing?.sourceNote.orEmpty()) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    Badge(if (existing == null) "새 음식 · 직접 입력" else "내 음식 수정")
    FoodField("브랜드 · 선택", brand, { brand = it })
    FoodField("음식 이름", name, { name = it })
    UiCard {
        SectionTitle("영양정보의 원래 기준")
        FoodField("기준량", basis, { basis = it }, suffix = "g", numeric = true, hint = "예: 영양성분표가 80g 기준이면 80")
        Chips(preparationLabels.values.toList(), preparationLabels[preparation] ?: "조리 상태 모름") { label -> preparation = preparationLabels.entries.first { it.value == label }.key }
    }
    UiCard {
        SectionTitle("위 기준량에 들어 있는 영양정보")
        MutedText("확인한 숫자만 입력해요. 빈칸은 정보 없음이며, 실제 0인 항목만 0으로 입력해주세요.")
        FoodField("열량", kcal, { kcal = it }, "kcal", numeric = true)
        FoodField("탄수화물", carbs, { carbs = it }, "g", numeric = true)
        FoodField("단백질", protein, { protein = it }, "g", numeric = true)
        FoodField("지방", fat, { fat = it }, "g", numeric = true)
        FoodField("식이섬유 · 선택", fiber, { fiber = it }, "g", numeric = true)
    }
    FoodField("출처 메모 · 선택", source, { source = it }, multiline = true, hint = "예: 제품 뒷면 영양성분표")
    FoodError(error)
    UiButton(if (existing == null) "내 음식에 저장" else "음식 정보 변경 저장", {
        val base = positiveAmount(basis)
        val values = listOf(kcal, carbs, protein, fat, fiber)
        error = when {
            !validText(name.trim(), 80) -> "음식 이름을 1~80자로 입력해주세요."
            !validOptionalText(brand, 80) -> "브랜드는 80자 이내로 입력해주세요."
            base == null -> "기준량은 0보다 크고 100,000g 이하인 수로, 소수점 두 자리까지 입력해주세요."
            values.any { it.isNotBlank() && nutritionAmount(it) == null } -> "영양정보는 0~100,000 범위에서 소수점 두 자리까지 입력하거나 빈칸으로 남겨주세요."
            !validOptionalText(source, 500) -> "출처 메모는 500자 이내로 입력해주세요."
            else -> null
        }
        if (error == null && base != null) model.saveFood(id, FoodWrite(name.trim(), brand.trim().ifBlank { null }, base,
            NutritionValues(nutritionAmount(kcal), nutritionAmount(carbs), nutritionAmount(protein), nutritionAmount(fat), nutritionAmount(fiber)),
            preparation, source.trim().ifBlank { null }, version)) { ui.go(ui.get("liveFood.foodReturn", "F06")) }
    })
    MutedText("원래 기준량을 보관하고, 실제 먹은 g에 맞춰 계산해요.")
    if (existing != null) {
        MutedText("정보를 바꿔도 이미 기록한 식사의 영양정보는 바뀌지 않아요.")
        TextButton(onClick = { deleting = true }) { Text("내 음식에서 삭제", color = MaterialTheme.colorScheme.error) }
        if (deleting) FoodConfirm("이 음식을 삭제할까요?", "지난 식사 기록은 남아요. 저장 식단에서 사용 중이면 먼저 식단에서 빼주세요.", "삭제", { deleting = false }) {
            deleting = false; model.deleteFood(existing) { ui.go("F06") }
        }
    }
}

private data class TemplateLine(val foodId: String, val grams: String)

@Composable
private fun LiveTemplateForm(ui: PreviewSession, model: MealViewModel, state: MealUiState) {
    val selectedId = ui.get("liveFood.templateId")
    val existing = state.templates.firstOrNull { it.id == selectedId }
    if (selectedId.isNotBlank() && existing == null) { UiCard { BodyText("수정할 식단을 찾을 수 없어요."); UiButton("기본 식단으로", { ui.go("F02") }) }; return }
    val id = remember(selectedId) { selectedId.ifBlank { UUID.randomUUID().toString() } }
    val version = remember(id) { existing?.version }
    var name by remember(id) { mutableStateOf(existing?.name.orEmpty()) }
    var memo by remember(id) { mutableStateOf(existing?.memo.orEmpty()) }
    var lines by remember(id) { mutableStateOf(existing?.items?.map { TemplateLine(it.foodId, foodNumber(it.grams)) }.orEmpty()) }
    var selecting by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Badge(if (existing == null) "새로 저장할 식단" else "저장 식단 수정")
    FoodField("식단 이름", name, { name = it }, hint = "예: 운동 후 자주 먹는 식사")
    val preview = lines.map { line ->
        val food = state.foods.firstOrNull { it.id == line.foodId }
        if (food == null) NutritionValues() else NutritionMath.atGrams(food.nutrition, food.basisGrams, positiveAmount(line.grams))
    }
    LiveNutritionPanel("이 식단의 영양정보", NutritionMath.totals(preview), emptyLabel = "음식 추가 전")
    lines.forEach { line -> key(line.foodId) {
        val food = state.foods.firstOrNull { it.id == line.foodId }
        UiCard {
            if (food != null) FoodIdentity(food.name, food.brand, food.preparation) else Text("음식 정보를 다시 확인해주세요.", color = MaterialTheme.colorScheme.error)
            FoodField("기본으로 먹을 양", line.grams, { text -> lines = lines.map { if (it.foodId == line.foodId) it.copy(grams = text) else it } }, "g", numeric = true)
            TextButton(onClick = { lines = lines.filterNot { it.foodId == line.foodId } }) { Text("식단에서 빼기", color = MaterialTheme.colorScheme.error) }
        }
    } }
    UiButton("식단에 음식 추가", { selecting = true }, primary = false, enabled = lines.size < 50)
    FoodField("식단 메모 · 선택", memo, { memo = it }, multiline = true)
    FoodError(error)
    UiButton("식단 저장", {
        error = when {
            !validText(name.trim(), 80) -> "식단 이름을 1~80자로 입력해주세요."
            lines.isEmpty() || lines.size > 50 -> "식단에 음식을 1~50개 넣어주세요."
            lines.any { positiveAmount(it.grams) == null || state.foods.none { food -> food.id == it.foodId } } -> "각 음식의 양과 등록 정보를 확인해주세요. 양은 소수점 두 자리까지 입력할 수 있어요."
            !validOptionalText(memo, 1000) -> "식단 메모는 1,000자 이내로 입력해주세요."
            else -> null
        }
        if (error == null) model.saveTemplate(id, MealTemplateWrite(name.trim(), lines.map { TemplateItem(it.foodId, positiveAmount(it.grams)!!) }, memo.trim().ifBlank { null }, version)) { ui.go("F02") }
    })
    MutedText("기본 식단을 바꿔도 지난 식사 기록은 바뀌지 않아요. 앞으로 기록할 때 새 구성을 불러와요.")
    if (existing != null) {
        TextButton(onClick = { deleting = true }) { Text("저장 식단 삭제", color = MaterialTheme.colorScheme.error) }
        if (deleting) FoodConfirm("이 저장 식단을 삭제할까요?", "기본 끼니에 연결되어 있다면 먼저 연결을 해제해주세요. 지난 식사 기록은 남아요.", "삭제", { deleting = false }) {
            deleting = false; model.deleteTemplate(existing) { ui.go("F02") }
        }
    }
    if (selecting) FoodPicker(state.foods, lines.map { it.foodId }.toSet(), { selecting = false }) { food ->
        if (lines.none { it.foodId == food.id } && lines.size < 50) lines = lines + TemplateLine(food.id, foodNumber(food.basisGrams))
        selecting = false
    }
}

@Composable
private fun LiveNutritionPanel(title: String, totals: NutritionTotals, target: NutritionValues? = null, emptyLabel: String = "음식 정보 없음") {
    UiCard {
        SectionTitle(title)
        Text(nutrientText(totals.kcal, "kcal", emptyLabel), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        target?.kcal?.let { goal ->
            MutedText("이 날짜의 목표 ${foodNumber(goal)} kcal")
            if (goal.signum() > 0 && totals.kcal.knownItems > 0) ProgressLine(totals.kcal.knownAmount.divide(goal, 6, RoundingMode.HALF_UP).toFloat())
            if (totals.kcal.missingItems > 0) MutedText("열량을 모르는 음식이 있어 전체 목표와 비교하기 어려워요.")
        }
        listOf(Triple("탄수화물", totals.carbsG, target?.carbsG), Triple("단백질", totals.proteinG, target?.proteinG), Triple("지방", totals.fatG, target?.fatG), Triple("식이섬유", totals.fiberG, target?.fiberG)).forEach { (label, total, goal) ->
            KeyValue(label, nutrientText(total, "g", emptyLabel) + (goal?.let { "\n목표 ${foodNumber(it)} g" } ?: ""))
        }
        if (listOf(totals.kcal, totals.carbsG, totals.proteinG, totals.fatG, totals.fiberG).any { it.missingItems > 0 })
            MutedText("일부 합계는 확인된 값만 더한 수치예요. 모르는 영양정보를 0으로 채우지 않아요.")
    }
}

@Composable
private fun FoodIdentity(name: String, brand: String?, preparation: String) {
    if (!brand.isNullOrBlank()) MutedText(brand)
    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    MutedText(preparationLabels[preparation] ?: "조리 상태 모름")
}

@Composable
private fun FoodField(label: String, value: String, change: (String) -> Unit, suffix: String = "", numeric: Boolean = false, multiline: Boolean = false, hint: String? = null) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }, singleLine = !multiline, minLines = if (multiline) 3 else 1,
        supportingText = if (hint == null) null else ({ Text(hint) }), suffix = if (suffix.isBlank()) null else ({ Text(suffix) }),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, unfocusedBorderColor = Border, focusedBorderColor = DeepBlue))
}

@Composable private fun FoodError(error: String?) { error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }

@Composable
private fun FoodConfirm(title: String, message: String, action: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = confirm) { Text(action, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("취소") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodSheet(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Silver) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = dismiss) { Text("닫기") }
            }
            content()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FoodPicker(foods: List<FoodDto>, selected: Set<String>, dismiss: () -> Unit, pick: (FoodDto) -> Unit) {
    var query by remember { mutableStateOf("") }
    FoodSheet("식단에 넣을 음식", dismiss) {
        FoodField("내 음식 검색", query, { query = it })
        val results = foods.filter { query.isBlank() || it.name.contains(query, true) || it.brand.orEmpty().contains(query, true) }
        if (results.isEmpty()) MutedText(if (foods.isEmpty()) "내 음식에 먼저 음식을 등록해주세요." else "일치하는 음식이 없어요.")
        results.forEach { food -> Choice(food.name, listOfNotNull(food.brand, "${foodNumber(food.basisGrams)} g 기준", preparationLabels[food.preparation]).joinToString(" · "), food.id in selected) { pick(food) } }
    }
}

private val preparationLabels = linkedMapOf("RAW" to "조리 전", "COOKED" to "조리 후", "AS_SOLD" to "제품 그대로", "UNKNOWN" to "조리 상태 모름")
private fun foodNumber(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
private fun valueText(value: BigDecimal?, unit: String) = value?.let { "${foodNumber(it)} $unit" } ?: "정보 없음"
private fun nutrientText(total: NutrientTotal, unit: String, empty: String): String = when {
    total.knownItems == 0 && total.missingItems == 0 -> empty
    total.knownItems == 0 -> "정보 없음"
    total.missingItems > 0 -> "${foodNumber(total.knownAmount)} $unit · 일부 합계"
    else -> "${foodNumber(total.knownAmount)} $unit"
}
private fun nutritionAmount(text: String): BigDecimal? = text.trim().takeIf { Regex("^[0-9]{1,6}([.,][0-9]{1,2})?$").matches(it) }
    ?.replace(',', '.')?.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 && it <= BigDecimal("100000") }
private fun positiveAmount(text: String): BigDecimal? = nutritionAmount(text)?.takeIf { it.signum() > 0 }
private fun validText(text: String, max: Int) = text.isNotBlank() && text.length <= max && '\u0000' !in text
private fun validOptionalText(text: String, max: Int) = text.length <= max && '\u0000' !in text
private fun openFoodForm(ui: PreviewSession, id: String?, returnRoute: String) { ui.set("liveFood.foodId", id.orEmpty()); ui.set("liveFood.foodReturn", returnRoute); ui.go("F13") }
private fun openTemplate(ui: PreviewSession, id: String?) { ui.set("liveFood.templateId", id.orEmpty()); ui.go("F14") }
