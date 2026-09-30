package com.bapegg.routinlog.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Explicit fictitious fixture data for the UI preview, never public food-database results.
private typealias Food = PreviewFood
private val foods=listOf(
    Food("등록한 닭가슴살",80.0,160.0,4.0,20.0,7.0,null,"직접 등록 · 제품 표기 기준"),
    Food("발아현미 + 백미밥",100.0,150.0,32.0,3.0,1.0,1.2,"일반 음식 · 조리 후"),
    Food("닭가슴살",100.0,120.0,1.0,20.0,4.0,null,"확인한 제품 · 제품 기준"),
    Food("김치",100.0,50.0,6.0,1.0,1.0,2.0,"일반 음식 · 조리 후"),
    Food("그릭요거트",100.0,90.0,6.0,10.0,3.0,null,"직접 확인 · 제품 기준"),
    Food("파스타 건면",100.0,350.0,70.0,12.0,2.0,3.0,"브랜드 확인 · 조리 전"),
    Food("올리브유",100.0,900.0,0.0,0.0,100.0,0.0,"직접 확인 · 제품 기준"),
    Food("곡물 시리얼",100.0,380.0,70.0,12.0,6.0,8.0,"체험용 예시 · 제품 기준"),
    Food("냉동 블루베리",100.0,50.0,12.0,0.5,0.3,2.0,"체험용 예시 · 제품 기준"),
)
private val mealNames=listOf("기본 아침","밥 · 닭가슴살 세트","닭가슴살 파스타")
private fun meal(ui:PreviewSession)=ui.get("food.activeMeal","점심")
private fun slots(ui:PreviewSession)=ui.get("food.slots","아침|점심|저녁").split('|').filter{it.isNotBlank()}
private fun mealName(ui:PreviewSession,slot:String=meal(ui))=ui.get("food.meal.$slot",ui.get("food.template.$slot",if(slot=="아침")mealNames[0] else if(slot in listOf("점심","저녁"))mealNames[1] else "아직 선택한 식사가 없어요"))
private fun food(ui:PreviewSession,name:String=ui.get("food.activeFood",foods[0].name)):Food {
    val fixture=foods.find{it.name==name}
    if(!ui.values.containsKey("food.custom.$name.base") && fixture!=null)return fixture
    return Food(name,ui.get("food.custom.$name.base","100").toDoubleOrNull()?.takeIf{it>0&&it.isFinite()}?:100.0,
        ui.get("food.custom.$name.kcal").toDoubleOrNull(),ui.get("food.custom.$name.carbs").toDoubleOrNull(),
        ui.get("food.custom.$name.protein").toDoubleOrNull(),ui.get("food.custom.$name.fat").toDoubleOrNull(),ui.get("food.custom.$name.fiber").toDoubleOrNull(),"직접 입력한 영양정보")
}
private fun format(n:Double?)=n?.takeIf{it.isFinite()}?.let {if(it%1.0==0.0)it.toLong().toString()else String.format(Locale.US,"%.1f",it)}?:"정보 없음"
private const val recipeContext="@recipe"
private fun fixtureMeal(name:String)=when(name){
    mealNames[0]->listOf(PreviewServing("곡물 시리얼",50.0),PreviewServing("그릭요거트",150.0),PreviewServing("냉동 블루베리",50.0))
    mealNames[1]->listOf(PreviewServing("발아현미 + 백미밥",300.0),PreviewServing("닭가슴살",300.0),PreviewServing("김치",100.0))
    mealNames[2]->listOf(PreviewServing("파스타 건면",200.0),PreviewServing("닭가슴살",300.0),PreviewServing("올리브유",20.0))
    else->emptyList()
}
private fun savedRecipe(ui:PreviewSession,name:String):List<PreviewServing>{
    val key="food.recipe.$name"
    if(!ui.values.containsKey("$key.items"))return fixtureMeal(name)
    return ui.get("$key.items").split('|').filter(String::isNotBlank).map{PreviewServing(it,ui.get("$key.grams.$it","100").toDouble())}
}
private fun servingList(ui:PreviewSession,context:String=meal(ui)):List<PreviewServing>{
    val key="food.content.$context"
    if(!ui.values.containsKey("$key.items"))return if(context==recipeContext)fixtureMeal(mealNames[2]) else savedRecipe(ui,mealName(ui,context))
    return ui.get("$key.items").split('|').filter(String::isNotBlank).distinct().map{PreviewServing(it,ui.get("$key.grams.$it","100").toDouble())}
}
private fun writeServings(ui:PreviewSession,context:String,items:List<PreviewServing>){
    ui.set("food.content.$context.items",items.joinToString("|"){it.foodName})
    items.forEach{ui.set("food.content.$context.grams.${it.foodName}",it.grams.toString())}
    if(context!=recipeContext)ui.set("food.unknown.$context","false")
}
private fun writeUserServings(ui:PreviewSession,context:String,items:List<PreviewServing>){
    writeServings(ui,context,items)
    if(context!=recipeContext){
        ui.set("food.done.$context","false");ui.set("food.skipped.$context","false")
        if(mealName(ui,context)=="아직 선택한 식사가 없어요")ui.set("food.meal.$context","$context 식사")
    }
}
private fun totals(ui:PreviewSession,items:List<PreviewServing>)=FoodPreviewMath.totals(items.map{food(ui,it.foodName).atGrams(it.grams)})
private fun nutrientText(sum:NutrientSum,unit:String="g")=if(sum.amount==null)"정보 없음" else "${format(sum.amount)} $unit${if(sum.partial)" · 부분 합계"else""}"
private fun context(ui:PreviewSession)=ui.get("food.editContext",meal(ui)).ifBlank{meal(ui)}
private fun chosen(ui:PreviewSession,food:String,grams:String="120") {
    ui.set("food.activeFood",food)
    ui.set("food.amount",if(food.startsWith("미확인: "))""else servingList(ui,context(ui)).firstOrNull{it.foodName==food}?.grams?.let(::format)?:grams)
    ui.go("F04")
}
private fun beginMealEdit(ui:PreviewSession,slot:String){
    ui.set("food.activeMeal",slot);ui.set("food.editContext",slot);ui.set("food.amountReturn","F03")
    if(ui.get("food.snapshot.slot")!=slot){
        ui.set("food.snapshot.slot",slot);ui.set("food.snapshot.name",mealName(ui,slot))
        writeServings(ui,"@snapshot",servingList(ui,slot))
        listOf("done","skipped","unknown").forEach{ui.set("food.snapshot.$it",ui.flag("food.$it.$slot",it=="done"&&slot=="아침").toString())}
    }
}
private fun assignMeal(ui:PreviewSession,slot:String,name:String){
    ui.set("food.meal.$slot",name);writeServings(ui,slot,savedRecipe(ui,name));ui.set("food.done.$slot","false");ui.set("food.skipped.$slot","false")
}
private fun recordMeal(ui:PreviewSession) {
    if(servingList(ui).isEmpty()){ui.notify("먹은 음식과 양을 먼저 추가해주세요.");return}
    ui.set("food.done.${meal(ui)}","true");ui.set("food.skipped.${meal(ui)}","false");ui.set("food.snapshot.slot","");ui.save("F01")
}
private fun beginRecipe(ui:PreviewSession,slot:String?=null,report:Boolean=false){
    if(!report)ui.set("foodReturnRoute","")
    ui.set("food.recipeName",if(report)ui.get("r.draftName","닭가슴살 파스타")else slot?.let{mealName(ui,it)}?:"나의 새 식사")
    val items=if(slot!=null)servingList(ui,slot)else if(report)savedRecipe(ui,ui.get("r.draftName","닭가슴살 파스타")).ifEmpty{fixtureMeal(mealNames[2])}else fixtureMeal(mealNames[2])
    writeServings(ui,recipeContext,items)
    val weight=items.sumOf{it.grams}.coerceAtLeast(1.0)
    ui.set("food.recipeTotal",weight.toString());ui.set("food.recipeEaten",weight.toString())
    ui.set("food.recipeSlot",slot?:"대체 식사로만 저장");ui.set("food.recipeOrigin",slot?:"")
    ui.set("food.editContext",recipeContext);ui.set("food.amountReturn","F14");ui.set("food.recipeInitialized","true")
}
internal fun beginReportMealEdit(ui:PreviewSession){beginRecipe(ui,report=true);ui.set("foodReturnRoute","R06");ui.go("F14")}

private fun recipePortion(ui:PreviewSession):List<PreviewServing>?{
    val total=ui.get("food.recipeTotal","520").toDoubleOrNull();val eaten=ui.get("food.recipeEaten","520").toDoubleOrNull()
    return if(total==null||eaten==null) null else runCatching{FoodPreviewMath.portion(servingList(ui,recipeContext),total,eaten)}.getOrNull()
}

@Composable fun FoodScreens(id:String,ui:PreviewSession) {
    LaunchedEffect(id){
        if(ui.flag("foodDiscardDraft")){
            val slot=ui.get("food.snapshot.slot")
            if(slot.isNotBlank()){
                writeServings(ui,slot,servingList(ui,"@snapshot"));ui.set("food.meal.$slot",ui.get("food.snapshot.name"))
                listOf("done","skipped","unknown").forEach{ui.set("food.$it.$slot",ui.get("food.snapshot.$it","false"))}
            }
            ui.set("food.snapshot.slot","");ui.set("foodDiscardDraft","false")
        }
        if(id=="F03")beginMealEdit(ui,meal(ui))
        if(id in listOf("F01","F02")){ui.set("food.editContext",meal(ui));ui.set("food.amountReturn","F03")}
        if(id=="F14"&&!ui.flag("food.recipeInitialized"))beginRecipe(ui)
    }
    when(id) {
        "F01"->FoodToday(ui)
        "F02"->SavedMeals(ui)
        "F03"->MealContent(ui)
        "F04"->FoodAmount(ui)
        "F05"->{
            UiCard{MutedText("오늘 ${meal(ui)} · 원래 계획");BodyText(mealName(ui));MutedText(servingList(ui).joinToString(" · "){it.foodName.removePrefix("미확인: ")})}
            SectionTitle("대신 먹은 식사")
            mealNames.forEach{Choice(it,nutrientText(totals(ui,savedRecipe(ui,it)).kcal,"kcal"),ui.get("food.replacement",mealNames[2])==it,{ui.set("food.replacement",it)})}
            MutedText("바꾼 이유 · 선택");Chips(listOf("재료 없음","외식","시간 부족","다른 맛","기타"),ui.get("food.reason"),{ui.set("food.reason",it)})
            UiButton("다른 음식 검색",{ui.go("F06")},false)
        }
        "F06"->FoodSearch(ui)
        "F07"->{
            val f=food(ui)
            UiCard{Badge("직접 확인한 영양정보");Text(f.name,style=MaterialTheme.typography.titleLarge);KeyValue("영양표 기준량","${format(f.base)} g");KeyValue("조리 상태",f.source);FoodNutrition(f,1.0)}
            SectionTitle("정보 출처");UiCard{BodyText("영양성분표 직접 확인");MutedText("체험용 예시 영양정보입니다.");UiButton("영양정보 수정",{ui.set("food.form.name",f.name);ui.set("food.form.base",f.base.toString());listOf("kcal" to f.kcal,"carbs" to f.carbs,"protein" to f.protein,"fat" to f.fat,"fiber" to f.fiber).forEach{(key,value)->ui.set("food.form.$key",value?.toString()?:"")};ui.go("F13")},false)}
            MutedText("‘정보 없음’은 0 g이 아니에요. 확인된 영양정보만 합계에 포함해요.")
        }
        "F08"->{
            BodyText("영양정보를 확인할 방법을 골라주세요.")
            listOf(Triple("식품 영양정보에서 찾기","일반 음식의 기준량·영양정보","F06"),Triple("영양성분표 사진","사진에서 읽은 내용을 확인하고 저장","F09"),Triple("상품 주소","상품 페이지의 영양정보 확인","F11"),Triple("직접 입력","제품의 영양표 기준량 그대로","F13")).forEach{(title,detail,to)->UiCard{UiRow(title,detail,icon=if(to=="F09")"Camera"else"Utensils",onClick={ui.go(to)})}}
            UiButton("지금은 이름만 기록",{ui.go("F16")},false)
        }
        "F09"->FoodPhoto(ui)
        "F10"->{
            BodyText("제품명과 영양정보를 확인해주세요.")
            Badge("사진 자동 인식은 아직 연결 전이에요",true)
            FoodForm(ui,true)
            UiCard{MutedText("영양성분표를 보면서 직접 확인해주세요. 비워둔 항목은 정보 없음으로 남겨요.")}
        }
        "F11"->{
            Input("상품 페이지 주소",ui.get("food.url"),{ui.set("food.url",it)})
            MutedText("상품 상세에 영양성분표가 있으면 항목을 읽어와요. 저장하기 전에 내용을 확인해요.")
            UiCard{SectionTitle("저장하기 전에");UiRow("제품과 기준량 확인",icon="Check");UiRow("읽은 영양정보 확인",icon="Check");UiRow("확인한 정보만 저장",icon="Check")}
            UiButton("사진으로 등록하기",{ui.go("F09")},false)
        }
        "F12"->{
            Badge("정보를 가져오지 않았어요",true)
            Text("상품 정보 연결을\n준비하고 있어요.",style=MaterialTheme.typography.headlineMedium)
            BodyText("지금은 사진을 보며 직접 입력하거나, 이름만 먼저 남길 수 있어요.")
            UiCard{SectionTitle("영양성분표 사진이 있나요?");MutedText("사진을 선택하고 직접 영양정보를 확인해주세요.");UiButton("사진으로 등록",{ui.go("F09")})}
            UiButton("직접 입력",{ui.go("F13")},false);UiButton("이름만 먼저 기록",{ui.go("F16")},false)
        }
        "F13"->{FoodForm(ui,false);FoodPhotoButton(ui)}
        "F14"->RecipeEditor(ui)
        "F15"->{
            MutedText("${LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일"))} · ${meal(ui)}")
            UiCard{SectionTitle(mealName(ui));KeyValue("먹은 양","확인한 구성 그대로");KeyValue("기록 상태","완료 전 확인");KeyValue("영양정보",nutrientText(totals(ui,servingList(ui)).kcal,"kcal"))}
            SectionTitle("기본 식단도 바꿀까요?")
            Choice("오늘만 기록","다른 끼니와 이후의 기본 식단은 그대로예요.",!ui.flag("food.updateTemplate"),{ui.set("food.updateTemplate","false")})
            Choice("다음 식단에도 반영","선택한 끼니의 기본 식사를 바꿔요.",ui.flag("food.updateTemplate"),{ui.set("food.updateTemplate","true")})
            MutedText("바꾼 이유 · 선택");Chips(listOf("재료 없음","외식","시간 부족","선호","생략"),ui.get("food.reason"),{ui.set("food.reason",it)})
        }
        "F16"->{
            Chips(slots(ui),meal(ui),{ui.set("food.activeMeal",it)})
            Input("먹은 음식",ui.get("food.nameOnly"),{ui.set("food.nameOnly",it)})
            Input("양 · 메모 (선택)",ui.get("food.nameOnlyNote"),{ui.set("food.nameOnlyNote",it)},multiline=true)
            UiCard{BodyText("먹은 기록은 남기고 영양정보는 미확인으로 표시해요.");KeyValue("식사 여부","먹음");KeyValue("열량 · 탄단지","정보 없음");KeyValue("주간 비교","영양정보 확인 필요")}
        }
    }
}

@Composable private fun FoodToday(ui:PreviewSession) {
    var edit by remember{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE",Locale.KOREAN)),Modifier.weight(1f),fontSize=13.sp,color=Muted);TextButton(onClick={ui.go("F02")}){Text("기본 식단")}}
    val done=slots(ui).filter{ui.flag("food.done.$it",it=="아침")&&!ui.flag("food.skipped.$it")}
    val sum=totals(ui,done.flatMap{servingList(ui,it)})
    val noTarget=ui.get("target.method")=="none"
    fun target(key:String,fallback:Double)=ui.get("target.$key").toDoubleOrNull()?.takeIf{it.isFinite()&&(if(key=="kcal")it>0 else it>=0)}?:fallback
    val calorieTarget=target("kcal",2400.0)
    UiCard(dark=true){
        Text("오늘 기록한 열량",color=Color(0xFFBAC5D2),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){HeroNumber(format(sum.kcal.amount),"kcal")};Badge("${done.size} / ${slots(ui).size}끼")}
        Text(if(noTarget)"영양 목표 없이 기록 중"else"하루 목표 ${format(calorieTarget)} kcal",fontSize=12.sp,color=Color(0xFFBAC5D2))
        if(!noTarget&&sum.kcal.amount!=null)ProgressLine((sum.kcal.known/calorieTarget).toFloat());Spacer(Modifier.height(4.dp))
        listOf(Triple("탄수화물",sum.carbs,target("carbs",280.0)),Triple("단백질",sum.protein,target("protein",170.0)),Triple("지방",sum.fat,target("fat",67.0))).forEachIndexed{i,(name,value,goal)->
            Row{Text(name,Modifier.weight(1f),fontSize=12.sp,color=Color(0xFFBAC5D2));Text(if(noTarget)nutrientText(value)else "${format(value.amount)} / ${format(goal)} g${if(value.partial)" *"else""}",fontSize=12.sp)}
            if(!noTarget&&value.amount!=null&&goal>0)ProgressLine((value.known/goal).toFloat(),if(i==0)Celery else if(i==1)Color(0xFFD9E1EB)else Color(0xFF879BB5))
        }
        if(sum.hasMissing)Text("일부 영양정보가 없어 확인된 값만 합산했어요.",fontSize=12.sp,color=Celery)
    }
    SectionTitle("오늘의 끼니","끼니 편집"){edit=true}
    slots(ui).forEach{slot->
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(slot,Modifier.weight(1f),fontWeight=FontWeight.SemiBold);if(slot in done)Badge("✓ 기록 완료")else MutedText(if(ui.flag("food.skipped.$slot"))"먹지 않음"else"기록 전")}
        UiCard{
            UiRow(mealName(ui,slot),servingList(ui,slot).joinToString(" · "){it.foodName.removePrefix("미확인: ")}.ifBlank{"음식을 추가해주세요"},icon="Utensils",onClick={beginMealEdit(ui,slot);ui.go("F03")})
            DividerLine()
            if(slot in done){KeyValue("기록한 열량",nutrientText(totals(ui,servingList(ui,slot)).kcal,"kcal"));UiButton("수정",{beginMealEdit(ui,slot);ui.go("F03")},false)}
            else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){UiButton("그대로 먹음",{ui.set("food.activeMeal",slot);recordMeal(ui)},modifier=Modifier.weight(1f),enabled=servingList(ui,slot).isNotEmpty());UiButton("음식 · 양 수정",{beginMealEdit(ui,slot);ui.go("F03")},false,modifier=Modifier.weight(1f))}
        }
    }
    UiButton("+ 끼니 · 간식 추가",{edit=true},false)
    if(edit)MealSlotsDialog(ui){edit=false}
}

@Composable private fun MealSlotsDialog(ui:PreviewSession,dismiss:()->Unit) {
    var name by remember{mutableStateOf("")}
    var draft by remember{mutableStateOf(slots(ui))}
    AlertDialog(onDismissRequest=dismiss,title={Text("나의 끼니")},text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
        MutedText("내 생활에 맞게 끼니를 추가하세요.")
        draft.forEach{slot->Row(verticalAlignment=Alignment.CenterVertically){Text(slot,Modifier.weight(1f));TextButton(onClick={if(draft.size>1)draft=draft.filter{it!=slot}else ui.notify("끼니를 한 개 이상 남겨주세요.")}){Text("제외")}}}
        Input("새 끼니 이름",name,{name=it});Chips(listOf("오전 간식","운동 전","운동 후","야식"),name,{name=it})
        UiButton("끼니 추가",{val clean=name.trim().replace('|',' ');if(clean.isNotEmpty()&&clean !in draft){draft=draft+clean;name=""}else ui.notify("겹치지 않는 끼니 이름을 입력해주세요.")},false)
    }},confirmButton={TextButton(onClick={ui.set("food.slots",draft.joinToString("|"));if(meal(ui) !in draft)ui.set("food.activeMeal",draft.first());dismiss()}){Text("완료")}},dismissButton={TextButton(onClick=dismiss){Text("취소")}})
}

@Composable private fun SavedMeals(ui:PreviewSession){
    MutedText("기본 식사를 정할 끼니");Chips(slots(ui),meal(ui),{ui.set("food.activeMeal",it)})
    BodyText("평소 먹는 식사를 골라두세요.\n먹은 날에는 ‘그대로 먹음’만 눌러주세요.")
    SectionTitle("끼니별 기본 식사")
    UiCard{slots(ui).forEach{UiRow(it,mealName(ui,it),onClick={ui.set("food.activeMeal",it)})}}
    SectionTitle("저장해 둔 식사")
    (mealNames+ui.get("food.recipes").split('|').filter{it.isNotBlank()}).distinct().forEach{name->
        Choice(name,nutrientText(totals(ui,savedRecipe(ui,name)).kcal,"kcal")+" · "+savedRecipe(ui,name).joinToString(" · "){it.foodName},mealName(ui)==name,{beginMealEdit(ui,meal(ui));ui.set("food.template.${meal(ui)}",name);assignMeal(ui,meal(ui),name);ui.notify("오늘 선택한 끼니에 구성을 반영했어요. 먹은 뒤 기록을 완료해주세요.")})
    }
    MutedText("먹는 제품과 양에 따라 영양정보가 달라질 수 있어요.")
    UiButton("자주 먹는 음식 검색",{beginMealEdit(ui,meal(ui));ui.go("F06")},false);UiButton("+ 새 식사 만들기",{beginRecipe(ui);ui.go("F14")})
}

@Composable private fun MealContent(ui:PreviewSession) {
    MutedText("오늘 ${meal(ui)} · ${mealName(ui)}")
    val sum=totals(ui,servingList(ui))
    UiCard{MutedText("식사 합계");HeroNumber(format(sum.kcal.amount),"kcal");FoodTotals(sum)}
    SectionTitle("음식과 양")
    UiCard{servingList(ui).forEach{item->
        Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){UiRow(item.foodName.removePrefix("미확인: "),if(item.foodName.startsWith("미확인: "))ui.get("food.nameOnlyNote.${meal(ui)}","양 미확인").ifBlank{"양 미확인"}else"${format(item.grams)} g",icon="Utensils",onClick={ui.set("food.editContext",meal(ui));ui.set("food.amountReturn","F03");chosen(ui,item.foodName,item.grams.toString())})};TextButton(onClick={writeUserServings(ui,meal(ui),servingList(ui).filter{it.foodName!=item.foodName})}){Text("제외")}}
        DividerLine()
    }
        if(servingList(ui).isEmpty())MutedText("이번 끼니에 먹을 음식을 추가해주세요.")
        UiButton("+ 음식 추가",{ui.set("food.editContext",meal(ui));ui.set("food.amountReturn","F03");ui.go("F06")},false)
    }
    UiButton("다른 식사로 교체",{ui.go("F05")},false);UiButton("레시피 · 영양정보",{beginRecipe(ui,meal(ui));ui.go("F14")},false)
    MutedText("실제로 먹은 음식과 양을 확인해주세요.")
}

@Composable private fun FoodAmount(ui:PreviewSession) {
    val f=food(ui);val grams=ui.get("food.amount","120");val amount=grams.toDoubleOrNull()?.takeIf{it.isFinite()&&it>0&&it<=10000}
    UiRow(f.name,f.source,icon="Utensils")
    Stepper("섭취량 · 기준량 ${format(f.base)} g",grams,"g",{ui.set("food.amount",it)},10.0)
    Chips(listOf("80 g","120 g","200 g"),"$grams g",{ui.set("food.amount",it.removeSuffix(" g"))})
    UiCard{if(amount!=null)FoodNutrition(f,amount/f.base)else MutedText("0보다 큰 섭취량을 입력해주세요.")}
    MutedText("제품 표기: ${format(f.base)} g당 ${format(f.kcal)} kcal · 단백질 ${format(f.protein)} g")
    if(f.fiber==null)MutedText("식이섬유는 제품에 표시되지 않았어요.")
    UiButton("제품 영양정보 보기",{ui.go("F07")},false)
}
@Composable private fun FoodNutrition(f:Food,ratio:Double){
    listOf(Triple("열량",f.kcal,"kcal"),Triple("단백질",f.protein,"g"),Triple("탄수화물",f.carbs,"g"),Triple("지방",f.fat,"g"),Triple("식이섬유",f.fiber,"g")).forEach{(n,v,u)->KeyValue(n,if(v==null)"정보 없음"else"${format(v*ratio)} $u")}
}

@Composable private fun FoodSearch(ui:PreviewSession) {
    Input("음식명 · 브랜드",ui.get("food.query"),{ui.set("food.query",it)})
    Chips(listOf("전체","최근","즐겨찾기"),ui.get("food.filter","전체"),{ui.set("food.filter",it)})
    val names=(foods.map{it.name}+ui.get("food.customNames").split('|').filter{it.isNotBlank()}).distinct().filter{it.contains(ui.get("food.query"),true)}.filter{ui.get("food.filter")=="전체"||ui.get("food.filter").isBlank()||if(ui.get("food.filter")=="즐겨찾기")ui.flag("food.favorite.$it")else ui.flag("food.recent.$it")}
    if(names.isEmpty())UiCard{BodyText("찾는 음식이 없어요.");MutedText("검색어를 바꾸거나 직접 등록해주세요.")}
    else UiCard{names.forEach{n->Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){UiRow(n,foods.find{it.name==n}?.source?:"직접 등록",icon="Utensils",onClick={ui.set("food.activeFood",n);ui.set("food.recent.$n","true");ui.go("F07")})};IconButton(onClick={ui.toggle("food.favorite.$n")}){Text(if(ui.flag("food.favorite.$n"))"★"else"☆",color=if(ui.flag("food.favorite.$n"))DeepBlue else Muted,fontSize=23.sp)}};DividerLine()}}
    MutedText("같은 음식도 제품과 조리 상태에 따라 달라요. 영양정보의 기준량을 확인해주세요.")
    UiButton("찾는 음식 직접 등록",{ui.go("F08")},false)
}

@Composable private fun FoodPhotoButton(ui:PreviewSession) {
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri:Uri?->if(uri!=null){ui.set("food.photo",uri.toString());ui.notify("사진을 선택했어요. 영양정보는 직접 확인해주세요.")}}
    UiButton(if(ui.get("food.photo").isEmpty())"앨범에서 선택"else"선택한 사진 변경",{picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},false)
    if(ui.get("food.photo").isNotBlank())Badge("사진 선택됨")
}
@Composable private fun FoodPhoto(ui:PreviewSession) {
    UiCard{Box(Modifier.fillMaxWidth().height(225.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)){UiIcon("Camera",Modifier.size(44.dp));BodyText("기준량과 영양값이\n잘 보이도록 선택해주세요.")}}}
    FoodPhotoButton(ui);UiButton("사진 촬영",{ui.notify("사진 촬영은 준비 중이에요. 앨범에서 선택할 수 있어요.")},false)
    MutedText("글씨가 흐리거나 표가 잘리면 다시 찍어주세요. 사진 자동 인식은 아직 연결 전이에요.")
}
@Composable private fun FoodForm(ui:PreviewSession,review:Boolean) {
    if(!review)Input("브랜드 (선택)",ui.get("food.form.brand"),{ui.set("food.form.brand",it)})
    Input("음식 · 제품명",ui.get("food.form.name"),{ui.set("food.form.name",it)})
    Input("기준량",ui.get("food.form.base","100"),{ui.set("food.form.base",it)},"g",true)
    if(!review){MutedText("상태");Chips(listOf("제품 표기","조리 전","조리 후"),ui.get("food.form.state","제품 표기"),{ui.set("food.form.state",it)})}
    listOf("kcal" to "열량 · kcal","protein" to "단백질 · g","carbs" to "탄수화물 · g","fat" to "지방 · g","fiber" to "식이섬유 · g (모르면 비우기)").forEach{(key,label)->Input(label,ui.get("food.form.$key"),{ui.set("food.form.$key",it)},numeric=true)}
    if(ui.get("food.form.error").isNotEmpty())Text(ui.get("food.form.error"),color=MaterialTheme.colorScheme.error)
}
private fun saveFood(ui:PreviewSession){
    val name=ui.get("food.form.name").trim();val base=ui.get("food.form.base","100").toDoubleOrNull()
    if(name.isBlank()||name.contains('|')||base==null||!base.isFinite()||base<0.001||base>10000){ui.set("food.form.error","이름과 기준량을 확인해주세요. 기준량은 0.001~10,000 g으로 입력할 수 있어요.");return}
    val keys=listOf("kcal","carbs","protein","fat","fiber")
    if(keys.any{val s=ui.get("food.form.$it");s.isNotBlank()&&(s.toDoubleOrNull()?.let{n->!n.isFinite()||n<0||n>10000}!=false)}){ui.set("food.form.error","영양값은 0 이상 숫자로 입력하거나 비워주세요.");return}
    (keys+"base").forEach{ui.set("food.custom.$name.$it",ui.get("food.form.$it",if(it=="base")"100"else""))}
    ui.set("food.customNames",(ui.get("food.customNames").split('|')+name).filter{it.isNotBlank()}.distinct().joinToString("|"))
    ui.set("food.activeFood",name);ui.set("food.form.error","");ui.save("F07")
}

@Composable private fun FoodTotals(sum:PreviewMealTotals){
    KeyValue("탄수화물",nutrientText(sum.carbs));KeyValue("단백질",nutrientText(sum.protein));KeyValue("지방",nutrientText(sum.fat));KeyValue("식이섬유",nutrientText(sum.fiber))
    if(sum.hasMissing)MutedText("정보가 없는 항목은 0으로 계산하지 않아요. 확인된 값만 부분 합계로 보여줘요.")
}

@Composable private fun RecipeEditor(ui:PreviewSession) {
    Input("저장 이름",ui.get("food.recipeName","닭가슴살 파스타"),{ui.set("food.recipeName",it)})
    UiCard{servingList(ui,recipeContext).forEach{item->
        Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){UiRow(item.foodName.removePrefix("미확인: "),"${format(item.grams)} g · ${if(item.foodName.contains("건면"))"조리 전"else"제품 기준"}",icon="Utensils",onClick={ui.set("food.editContext",recipeContext);ui.set("food.amountReturn","F14");chosen(ui,item.foodName,item.grams.toString())})};TextButton(onClick={writeServings(ui,recipeContext,servingList(ui,recipeContext).filter{it.foodName!=item.foodName})}){Text("제외")}}
    }
        UiButton("+ 재료 추가",{ui.set("food.editContext",recipeContext);ui.set("food.amountReturn","F14");ui.go("F06")},false)
    }
    Input("총 완성량",ui.get("food.recipeTotal","1000"),{ui.set("food.recipeTotal",it)},"g",true)
    Input("이번에 먹은 양",ui.get("food.recipeEaten","500"),{ui.set("food.recipeEaten",it)},"g",true)
    val total=ui.get("food.recipeTotal","1000").toDoubleOrNull()?:0.0;val eaten=ui.get("food.recipeEaten","500").toDoubleOrNull()?:0.0
    MutedText(if(total>0)"전체 요리의 ${format(eaten/total*100)}%를 먹은 양이에요. 영양정보가 없는 재료는 합계에서 제외해요."else"총 완성량을 입력해주세요.")
    val portion=recipePortion(ui)
    if(portion!=null){val sum=totals(ui,portion);UiCard{SectionTitle("이번에 먹은 양의 영양정보");KeyValue("열량",nutrientText(sum.kcal,"kcal"));FoodTotals(sum)}}
    if(ui.get("foodReturnRoute")=="R06")MutedText("다음 주 제안의 대체 식사로 저장해요. 오늘 먹은 기록은 바뀌지 않아요.")
    else {SectionTitle("기본 끼니로 지정");Chips(listOf("대체 식사로만 저장")+slots(ui),ui.get("food.recipeSlot","대체 식사로만 저장"),{ui.set("food.recipeSlot",it)})}
}

@Composable fun FoodFooter(id:String,ui:PreviewSession) {
    when(id){
        "F03"->{UiButton("이대로 먹었어요",{ui.go("F15")},enabled=servingList(ui).isNotEmpty());UiButton("먹지 않았어요",{ui.set("food.done.${meal(ui)}","false");ui.set("food.skipped.${meal(ui)}","true");ui.set("food.snapshot.slot","");ui.save("F01")},false)}
        "F04"->UiButton("이 양으로 반영",{
            val g=ui.get("food.amount","120").toDoubleOrNull();val f=food(ui)
            if(g==null||!g.isFinite()||g<=0||g>10000)ui.notify("먹은 양을 0보다 큰 숫자로 입력해주세요.")
            else{writeUserServings(ui,context(ui),FoodPreviewMath.upsert(servingList(ui,context(ui)),PreviewServing(f.name,g)))
                val to=ui.get("food.amountReturn",if(context(ui)==recipeContext)"F14"else"F03");ui.save(to.ifBlank{"F03"})}
        })
        "F05"->UiButton("선택한 식사로 교체",{assignMeal(ui,meal(ui),ui.get("food.replacement",mealNames[2]));ui.save("F03")})
        "F07"->UiButton("먹은 양 입력",{chosen(ui,food(ui).name)})
        "F09"->UiButton("영양정보 직접 확인",{ui.go("F10")})
        "F10","F13"->UiButton(if(id=="F10")"확인한 정보로 저장"else"음식 저장",{saveFood(ui)})
        "F11"->UiButton("영양정보 가져오기",{val url=ui.get("food.url");if(!url.startsWith("https://"))ui.notify("https://로 시작하는 상품 주소를 입력해주세요.")else ui.go("F12")})
        "F12"->UiButton("주소 수정 · 다시 시도",{ui.go("F11")},false)
        "F14"->UiButton("식사 저장",{
            val name=ui.get("food.recipeName","닭가슴살 파스타").trim();val total=ui.get("food.recipeTotal","1000").toDoubleOrNull();val eaten=ui.get("food.recipeEaten","500").toDoubleOrNull()
            val portion=recipePortion(ui)
            if(name.isBlank()||name.contains('|')||total==null||eaten==null||!total.isFinite()||!eaten.isFinite()||total<=0||eaten<=0||eaten>total||portion.isNullOrEmpty())ui.notify("이름과 재료·전체·섭취 분량을 확인해주세요.")else{
                // A saved recipe edit must not silently rewrite another meal already on today's plan.
                slots(ui).forEach{slot->if(!ui.values.containsKey("food.content.$slot.items"))writeServings(ui,slot,servingList(ui,slot))}
                ui.set("food.recipes",(ui.get("food.recipes").split('|')+name).filter{it.isNotBlank()}.distinct().joinToString("|"))
                ui.set("food.recipe.$name.items",portion.joinToString("|"){it.foodName});portion.forEach{ui.set("food.recipe.$name.grams.${it.foodName}",it.grams.toString())}
                val back=ui.get("foodReturnRoute").ifBlank{if(ui.get("food.recipeOrigin").isNotBlank())"F03"else"F02"}
                val slot=ui.get("food.recipeSlot");if(back!="R06"&&slot in slots(ui)){ui.set("food.template.$slot",name);assignMeal(ui,slot,name)}
                if(back=="R06"){
                    val sum=totals(ui,portion);ui.set("r.draftName",name)
                    listOf("Kcal" to sum.kcal,"Carbs" to sum.carbs,"Protein" to sum.protein,"Fat" to sum.fat,"Fiber" to sum.fiber).forEach{(key,value)->ui.set("r.draft$key",nutrientText(value,if(key=="Kcal")"kcal"else"g"))}
                    ui.set("r.draftNutritionEdited","true")
                }
                ui.set("food.recipeInitialized","false");ui.set("foodReturnRoute","");ui.set("food.editContext",meal(ui));ui.set("food.amountReturn","F03");ui.save(back.ifBlank{"F02"})
            }
        })
        "F15"->UiButton("식사 기록 완료",{
            if(ui.flag("food.updateTemplate")){
                val name="${meal(ui)} 기본 식사";val items=servingList(ui)
                ui.set("food.recipe.$name.items",items.joinToString("|"){it.foodName});items.forEach{ui.set("food.recipe.$name.grams.${it.foodName}",it.grams.toString())}
                ui.set("food.template.${meal(ui)}",name);ui.set("food.recipes",(ui.get("food.recipes").split('|')+name).filter(String::isNotBlank).distinct().joinToString("|"))
            }
            recordMeal(ui)
        })
        "F16"->UiButton("이름만 저장",{val name=ui.get("food.nameOnly").trim();if(name.isBlank()||name.contains('|'))ui.notify("먹은 음식 이름을 입력해주세요.")else{ui.set("food.meal.${meal(ui)}",name);ui.set("food.nameOnlyNote.${meal(ui)}",ui.get("food.nameOnlyNote"));writeServings(ui,meal(ui),listOf(PreviewServing("미확인: $name",100.0)));ui.set("food.unknown.${meal(ui)}","true");recordMeal(ui)}})
    }
}
