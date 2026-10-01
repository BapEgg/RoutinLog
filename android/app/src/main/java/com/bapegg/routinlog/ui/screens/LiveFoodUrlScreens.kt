package com.bapegg.routinlog.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.bapegg.routinlog.data.FoodUrlDraft
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.DeepBlue

@Composable
internal fun LiveFoodUrlInput(ui: PreviewSession, model: MealViewModel, state: MealUiState) {
    var address by rememberSaveable { mutableStateOf(state.foodUrl) }
    UiCard {
        SectionTitle("상품 주소를 붙여넣어주세요")
        BodyText("공개된 상품 페이지의 영양정보를 읽어요. 사진으로만 표시되거나 로그인이 필요한 정보는 가져오지 못할 수 있어요.")
        OutlinedTextField(address, { if (it.length <= 2048) { address = it; model.clearFoodUrl() } }, label = { Text("상품 URL") },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "상품 URL" },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), enabled = !state.urlReading, maxLines = 4)
        UiButton("상품 정보 가져오기", { model.previewFoodUrl(address) }, enabled = !state.urlReading)
        MutedText("서버가 입력한 주소에 접속해 확인해요. 읽은 내용은 저장 전에 수정할 수 있어요.")
    }
    if (state.urlReading) UiCard {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = DeepBlue)
        BodyText("상품 페이지를 확인하고 있어요")
        UiButton("가져오기 취소", model::clearFoodUrl, primary = false)
    }
    state.urlError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    val result = state.urlResult
    if (result?.draft != null) UiCard {
        Badge("확인 후 저장")
        SectionTitle(result.draft.name.ifBlank { "상품 영양정보를 찾았어요" })
        MutedText(result.draft.sourceHost)
        UiButton("상품 영양정보 확인", { ui.set("liveFood.foodReturn", "F06"); ui.go("F12") })
    } else if (result != null) UiCard {
        SectionTitle("상품 정보를 가져오지 못했어요")
        BodyText(result.message ?: "영양정보를 확인하지 못했어요. 사진으로 등록하거나 직접 입력해주세요.")
    }
    UiButton("영양성분표 사진으로 등록", { model.clearFoodUrl(); model.clearLabel(); ui.go("F09") }, primary = false)
    UiButton("직접 입력하기", { model.clearFoodUrl(); ui.set("liveFood.foodId", ""); ui.set("liveFood.foodReturn", "F06"); ui.go("F13") }, primary = false)
}

@Composable
internal fun FoodUrlReviewSource(draft: FoodUrlDraft) {
    val browser = LocalUriHandler.current
    var openError by remember { mutableStateOf(false) }
    var excerptOpen by remember { mutableStateOf(false) }
    UiCard {
        Badge("상품 페이지에서 읽은 내용 · 확인 필요", true)
        BodyText(draft.sourceHost)
        MutedText("확인 시각 · ${draft.checkedAt.take(10)}")
        draft.warnings.forEach { BodyText(it) }
        UiButton("원본 상품 페이지 열기", {
            openError = !draft.sourceUrl.startsWith("https://") || runCatching { browser.openUri(draft.sourceUrl) }.isFailure
        }, primary = false)
        if (openError) Text("브라우저를 열지 못했어요. 상품 주소를 직접 확인해주세요.", color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { excerptOpen = !excerptOpen }) { Text(if (excerptOpen) "읽은 원문 접기" else "읽은 원문 보기") }
        if (excerptOpen) BodyText(draft.excerpt)
    }
}
