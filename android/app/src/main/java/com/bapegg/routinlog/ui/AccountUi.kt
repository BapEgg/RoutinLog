package com.bapegg.routinlog.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.bapegg.routinlog.data.*
import java.time.LocalDate
import java.time.ZoneId
import java.math.BigDecimal
import java.math.RoundingMode

data class AccountActions(
    val state: AccountUiState = AccountUiState(initializing = false),
    val login: () -> Unit = {},
    val refresh: () -> Unit = {},
    val resume: () -> Unit = {},
    val saveProfile: () -> Unit = {},
    val saveBody: () -> Unit = {},
    val loadDate: (String) -> Unit = {},
    val loadRange: (String, String) -> Unit = { _, _ -> },
    val deleteBody: () -> Unit = {},
    val logout: () -> Unit = {},
    val deleteAccount: () -> Unit = {},
)
val LocalAccount = staticCompositionLocalOf { AccountActions() }
const val CONSENT_VERSION = "2026-09-30"

/** Mapping keeps sample defaults out of a new account and preserves canonical kg/cm. */
object AccountDrafts {
    fun begin(ui: PreviewSession, profile: ProfileDto?) {
        ui.reset()
        ui.accountMode = true
        if (profile == null) {
            listOf("age", "heightCm", "heightFt", "heightIn", "weightKg", "weightLb", "waistCm").forEach { ui.set("onb.$it", "") }
            ui.set("onb.sex", "응답하지 않음")
            ui.set("onb.exerciseDays", "0일 · 아직 안 해요")
            ui.set("onb.exerciseType","근력 + 유산소");ui.set("onb.exerciseDuration","30–60분");ui.set("onb.exerciseIntensity","보통 · 숨이 조금 참")
            ui.set("onb.experience", "입문")
            ui.go("A02")
        } else {
            apply(ui, profile)
            ui.go("H01")
        }
    }

    fun apply(ui: PreviewSession, p: ProfileDto) {
        ui.loadedProfile=p
        ui.set("onb.age", p.age.toString()); ui.set("onb.heightCm", p.heightCm.toString())
        ui.set("onb.weightKg", p.initialWeightKg.toString()); ui.set("onb.waistCm", p.initialWaistCm?.toString().orEmpty())
        ui.set("onb.targetWeightKg", p.targetWeightKg?.toString().orEmpty())
        ui.set("onb.weightLb",format(p.initialWeightKg*2.2046226218))
        val inches=kotlin.math.round(p.heightCm/2.54*10)/10
        ui.set("onb.heightFt",kotlin.math.floor(inches/12).toInt().toString());ui.set("onb.heightIn",format(inches%12))
        ui.set("onb.waistIn",p.initialWaistCm?.let{format(it/2.54)}.orEmpty())
        ui.set("onb.targetWeightLb",p.targetWeightKg?.let{format(it*2.2046226218)}.orEmpty())
        ui.set("onb.sex", when(p.sex){"MALE"->"남성";"FEMALE"->"여성";else->"응답하지 않음"})
        ui.set("onb.goal", when(p.goal){"GAIN"->"근육 증가";"LOSE"->"체중 감량";else->"근육 유지"})
        ui.set("onb.units", if(p.units=="IMPERIAL")"imperial" else "metric")
        ui.set("onb.activity", when(p.activityLevel){"LIGHT","MODERATE"->"이동이 잦은 편";"HIGH"->"몸을 많이 쓰는 편";else->"앉아 있는 편"})
        ui.set("onb.schedule", if(p.scheduleFlexible)"매주 달라요" else "고정 요일")
        val days = listOf("월","화","수","목","금","토","일")
        ui.set("onb.availableDays", p.exerciseDays.mapNotNull { days.getOrNull(it-1) }.joinToString("|"))
        ui.set("onb.availableCount", "주 ${p.weeklyFrequency}일")
        ui.set("onb.availableDuration", durationLabel(p.exerciseMinutes))
        ui.set("onb.exerciseDays", p.recentExerciseDays?.let{if(it==0)"0일 · 아직 안 해요"else "${it}일"}?:"잘 모르겠어요")
        ui.set("onb.exerciseDuration", p.recentExerciseMinutes?.let(::durationLabel)?:"잘 모르겠어요")
        ui.set("onb.exerciseType", when(p.recentExerciseType){"STRENGTH"->"근력 중심";"CARDIO"->"유산소 중심";"MIXED"->"근력 + 유산소";else->"잘 모르겠어요"})
        ui.set("onb.exerciseIntensity", when(p.recentExerciseIntensity){"LOW"->"가벼움 · 대화가 편함";"MODERATE"->"보통 · 숨이 조금 참";"HIGH"->"높음 · 대화가 어려움";else->"잘 모르겠어요"})
        ui.set("onb.experience", when(p.experience){"ADVANCED"->"꾸준히 운동 중";"INTERMEDIATE"->"기초 동작 경험";else->"입문"})
        ui.set("onb.targetMode", if(p.nutritionMode=="MANUAL")"직접 설정"else"자동으로 정하기")
        mapOf("kcal" to p.dailyCalories,"carbs" to p.carbohydrateG,"protein" to p.proteinG,"fat" to p.fatG,"fiber" to p.fiberG).forEach { (key,value)->
            ui.set("target.$key", value?.toString().orEmpty());ui.set("onb.manual.$key",value?.toString().orEmpty())
        }
        ui.set("target.method", when(p.nutritionMode){"NONE"->"none";"MANUAL"->"manual";else->p.targetFormulaVersion?:"prototype-start-0.1"})
        listOf("terms","privacy","health").forEach {ui.set("onb.$it","true")}
        ui.set("s.goalApplyDate", LocalDate.now(ZoneId.of(p.timeZone)).toString())
        ui.set("profile.version", p.version?.toString().orEmpty())
    }

    fun profile(ui: PreviewSession): ProfileDto {
        require(listOf("terms","privacy","health").all{ui.flag("onb.$it")}) {"필수 동의를 확인해주세요."}
        fun number(key:String,scale:Int=2)=ui.get(key).toBigDecimalOrNull()?.setScale(scale,RoundingMode.HALF_UP)?.toDouble()?.takeIf{it.isFinite()}
        val original=ui.loadedProfile
        val age = ui.get("onb.age").toIntOrNull()
        require(age != null && age in 19..120) {"현재 가입은 만 19세 이상을 지원해요."}
        val height=number("onb.heightCm");val weight=number("onb.weightKg",3)
        require(height!=null&&height>0&&height<=300&&weight!=null&&weight>0&&weight<=1000){"키와 현재 체중을 확인해주세요."}
        val days=listOf("월","화","수","목","금","토","일")
        val flexible=ui.get("onb.schedule","고정 요일")=="매주 달라요"
        val selected=ui.get("onb.availableDays","월|화|목|금").split('|').mapNotNull{days.indexOf(it).takeIf{v->v>=0}?.plus(1)}
        val method=ui.get("target.method")
        require(method.isNotBlank()){ "시작 목표를 먼저 확인해주세요." }
        val none=method=="none"
        val kcal=ui.get("target.kcal").toBigDecimalOrNull()
        require(none||(kcal!=null&&kcal>BigDecimal.ZERO&&kcal.stripTrailingZeros().scale()<=0)){"하루 열량은 0보다 큰 정수로 입력해주세요."}
        val recentDays=ui.get("onb.exerciseDays","0일").takeWhile{it.isDigit()}.toIntOrNull()
        val timeZone=original?.timeZone?:ZoneId.systemDefault().id
        val today=LocalDate.now(ZoneId.of(timeZone))
        val effectiveDate=runCatching{LocalDate.parse(ui.get("s.goalApplyDate",today.toString()))}.getOrNull()
        require(effectiveDate!=null&&effectiveDate in LocalDate.of(1900,1,1)..today){"변경 적용일은 1900년부터 오늘까지 선택해주세요."}
        val activityLabel=ui.get("onb.activity","앉아 있는 편")
        val activity=when(activityLabel){"이동이 잦은 편"->if(original?.activityLevel=="MODERATE")"MODERATE"else"LIGHT";"몸을 많이 쓰는 편"->"HIGH";else->"SEDENTARY"}
        val plannedLabel=ui.get("onb.availableDuration","30–60분")
        val recentLabel=ui.get("onb.exerciseDuration","30–60분")
        return ProfileDto(
            age=age, sex=when(ui.get("onb.sex")){"남성"->"MALE";"여성"->"FEMALE";else->"UNSPECIFIED"},
            heightCm=height,initialWeightKg=weight,initialWaistCm=number("onb.waistCm"),
            goal=when(ui.get("onb.goal","근육 증가")){"근육 증가"->"GAIN";"체중 감량"->"LOSE";else->"MAINTAIN"},
            targetWeightKg=number("onb.targetWeightKg",3),activityLevel=activity,
            exerciseDays=if(flexible)emptyList()else selected,exerciseMinutes=original?.exerciseMinutes?.takeIf{durationLabel(it)==plannedLabel}?:durationMinutes(plannedLabel)?:45,
            experience=when(ui.get("onb.experience")){"꾸준히 운동 중"->"ADVANCED";"기초 동작 경험"->"INTERMEDIATE";else->"BEGINNER"},
            units=if(ui.get("onb.units")=="imperial")"IMPERIAL"else"METRIC",
            nutritionMode=if(none)"NONE"else if(method=="manual")"MANUAL"else if(original?.nutritionMode=="BODY_WEIGHT")"BODY_WEIGHT"else"AUTO",
            dailyCalories=if(none)null else number("target.kcal")?.toInt(),carbohydrateG=if(none)null else number("target.carbs"),
            proteinG=if(none)null else number("target.protein"),fatG=if(none)null else number("target.fat"),fiberG=if(none)null else number("target.fiber"),
            termsVersion=CONSENT_VERSION,privacyVersion=CONSENT_VERSION,healthConsentVersion=CONSENT_VERSION,
            timeZone=timeZone,effectiveFrom=effectiveDate.toString(),version=ui.get("profile.version").toLongOrNull(),
            recentExerciseDays=recentDays,
            recentExerciseMinutes=if(recentDays==0)0 else original?.recentExerciseMinutes?.takeIf{durationLabel(it)==recentLabel}?:durationMinutes(recentLabel),
            recentExerciseType=if(recentDays==0)"UNKNOWN"else when(ui.get("onb.exerciseType","근력 + 유산소")){"근력 중심"->"STRENGTH";"유산소 중심"->"CARDIO";"근력 + 유산소"->"MIXED";else->"UNKNOWN"},
            recentExerciseIntensity=if(recentDays==0)"UNKNOWN"else when(ui.get("onb.exerciseIntensity","보통 · 숨이 조금 참").substringBefore(" · ")){"가벼움"->"LOW";"보통"->"MODERATE";"높음"->"HIGH";else->"UNKNOWN"},
            scheduleFlexible=flexible,weeklyFrequency=if(flexible)ui.get("onb.availableCount","주 4일").filter{it.isDigit()}.toIntOrNull()?:4 else selected.size,
            targetFormulaVersion=if(none||method=="manual")null else method,
        )
    }

    fun records(ui: PreviewSession, records: List<BodyMeasurementDto>) {
        ui.values.keys.filter{it.matches(Regex("body\\.\\d{4}-\\d{2}-\\d{2}\\..+"))}.toList().forEach(ui.values::remove)
        records.forEach {r->
            ui.set("body.${r.date}.weight",r.weightKg?.toString().orEmpty());ui.set("body.${r.date}.waist",r.waistCm?.toString().orEmpty())
            ui.set("body.${r.date}.memo",r.memo.orEmpty());ui.set("body.${r.date}.version",r.version.toString())
        }
    }

    fun bodyDraft(ui: PreviewSession, record: BodyMeasurementDto?) {
        ui.set("body.draftWeight",record?.weightKg?.toString().orEmpty());ui.set("body.draftWaist",record?.waistCm?.toString().orEmpty())
        ui.set("body.draftWeightLb",record?.weightKg?.let{format(it*2.2046226218)}.orEmpty())
        ui.set("body.draftWaistIn",record?.waistCm?.let{format(it/2.54)}.orEmpty())
        ui.set("body.draftMemo",record?.memo.orEmpty());ui.set("body.draftVersion",record?.version?.toString().orEmpty())
    }
    private fun durationLabel(minutes:Int)=if(minutes<30)"30분 미만"else if(minutes<60)"30–60분"else"60분 이상"
    private fun durationMinutes(label:String)=when(label){"30분 미만"->20;"30–60분"->45;"60분 이상"->75;else->null}
    private fun format(value:Double)=String.format(java.util.Locale.US,"%.1f",value)
}
