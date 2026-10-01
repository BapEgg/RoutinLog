package com.bapegg.routinlog.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.bapegg.routinlog.food.LabelImageReader
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.DeepBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun LiveFoodLabelPhoto(ui: PreviewSession, model: MealViewModel, state: MealUiState) {
    val context = LocalContext.current.applicationContext
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var launchError by remember { mutableStateOf<String?>(null) }
    fun read(uri: Uri, owner: String?, file: File? = null) {
        if (owner == null || model.state.value.userId != owner || ui.route != "F09") { file?.delete(); return }
        launchError = null
        model.readLabel(owner, uri.toString(), cleanup = { file?.delete(); Unit }) { LabelImageReader.read(context, uri) }
    }
    val album = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) read(uri, pendingOwner)
        pendingOwner = null
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraPath?.let(::File)
        val owner = pendingOwner
        pendingCameraPath = null; pendingOwner = null
        if (success && file != null) read(FileProvider.getUriForFile(context, "${context.packageName}.label-photos", file), owner, file)
        else file?.delete()
    }
    UiCard {
        SectionTitle("영양성분표를 보여주세요")
        BodyText("기준량과 탄수화물·단백질·지방이 함께 나오게 찍어주세요. 반사광 없이 표를 크게 담으면 잘 읽혀요.")
        UiButton("사진 촬영", {
            runCatching {
                val directory = File(context.cacheDir, "nutrition-labels").apply { mkdirs() }
                directory.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 86_400_000 }?.forEach { it.delete() }
                val file = File.createTempFile("label-", ".jpg", directory)
                pendingCameraPath = file.absolutePath; pendingOwner = state.userId
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.label-photos", file))
            }.onFailure {
                pendingCameraPath?.let { File(it).delete() }; pendingCameraPath = null; pendingOwner = null
                launchError = "카메라를 열지 못했어요. 앨범에서 사진을 선택해주세요."
            }
        }, enabled = !state.labelReading)
        UiButton("앨범에서 선택", {
            pendingOwner = state.userId
            runCatching { album.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                .onFailure { pendingOwner = null; launchError = "앨범을 열지 못했어요. 다시 시도하거나 직접 입력해주세요." }
        }, primary = false, enabled = !state.labelReading)
    }
    state.labelImage?.let { LabelPhotoPreview(it) }
    if (state.labelReading) UiCard {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = DeepBlue)
        BodyText("사진 속 글자를 읽고 있어요")
        UiButton("읽기 취소", model::clearLabel, primary = false)
    }
    (launchError ?: state.labelError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (state.labelDraft != null) UiCard {
        SectionTitle("읽은 내용을 확인해주세요")
        BodyText("아직 저장하지 않았어요. 숫자가 맞는지 원본과 비교한 뒤 수정할 수 있어요.")
        UiButton("읽은 내용 확인", { ui.set("liveFood.foodId", ""); ui.set("liveFood.foodReturn", "F06"); ui.go("F10") })
    }
    MutedText("사진은 서버로 보내지 않아요. 확인하고 저장한 음식 이름과 영양정보만 내 계정에 보관해요.")
    UiButton("직접 입력하기", { model.clearLabel(); ui.set("liveFood.foodId", ""); ui.set("liveFood.foodReturn", "F06"); ui.go("F13") }, primary = false)
    if (state.labelImage != null && !state.labelReading) UiButton("사진 지우기", model::clearLabel, primary = false)
}

@Composable
internal fun LabelPhotoPreview(image: String) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<Bitmap?>(null, image) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { LabelImageReader.decode(context, image.toUri(), 1600) }.getOrNull() }
    }
    var expanded by remember(image) { mutableStateOf(false) }
    UiCard {
        SectionTitle("선택한 원본 사진")
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), "확인할 영양성분표 원본", Modifier.fillMaxWidth().height(if (expanded) 520.dp else 200.dp), contentScale = ContentScale.Fit)
            UiButton(if (expanded) "사진 작게 보기" else "사진 크게 보기", { expanded = !expanded }, primary = false)
        } else MutedText("사진을 표시할 수 없다면 다시 선택해주세요.")
    }
}

@Composable
internal fun LabelReviewSource(state: MealUiState) {
    val draft = state.labelDraft ?: return
    var rawOpen by remember(draft) { mutableStateOf(false) }
    state.labelImage?.let { LabelPhotoPreview(it) }
    UiCard {
        Badge("사진에서 읽은 내용 · 확인 필요", true)
        draft.warnings.forEach { BodyText(it) }
        TextButton(onClick = { rawOpen = !rawOpen }) { Text(if (rawOpen) "읽은 글자 접기" else "읽은 글자 전체 보기") }
        if (rawOpen) Text(draft.rawText)
    }
}
