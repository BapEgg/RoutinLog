package com.bapegg.routinlog.features

data class Evidence(val id:String,val title:String,val authors:String,val url:String,val scope:String,val limitation:String)
data class ProgramMove(val key:String,val sets:Int,val reps:Int,val restSeconds:Int=120)
data class ProgramSession(val name:String,val moves:List<ProgramMove>)
data class TrainingProgram(val id:String,val name:String,val audience:String,val purpose:String,val sessions:List<ProgramSession>,
    val evidenceIds:List<String>,val author:String="루틴로그 구성",val limitation:String="연구의 일반 원칙을 참고해 구성한 시작용 템플릿이에요. 이 운동 조합 자체의 효과나 개인 회복 시간을 검증한 연구는 아니에요.")
data class ProgramCatalogDto(val revision:String,val programs:List<TrainingProgram>,val evidence:List<Evidence>)

object ProgramCatalog {
    val evidence=listOf(
        Evidence("currier2023","Resistance training prescription for muscle strength and hypertrophy in healthy adults", "Currier 등 · 2023",
            "https://doi.org/10.1136/bjsports-2023-106807","건강한 성인의 저항운동 처방을 비교한 체계적 문헌고찰·네트워크 메타분석. 다양한 구성이 비운동보다 도움이 되었어요.",
            "집단 평균 결과예요. 개인의 시작 중량·회복일·특정 종목 조합을 결정하지 않아요."),
        Evidence("who2020","WHO guidelines on physical activity and sedentary behaviour","WHO · 2020",
            "https://www.who.int/publications/i/item/9789240015128","성인의 유산소·근력 활동에 관한 공중보건 가이드라인이에요.",
            "감량 속도나 개인에게 맞는 식사량을 보장하는 프로그램은 아니에요."),
        Evidence("compendium2024","2024 Adult Compendium of Physical Activities","Herrmann 등 · 2024",
            "https://pacompendium.com/","활동 종류에 따른 표준 MET 값을 제공해요. 성인판의 대상은 19~59세예요.",
            "개인의 정밀한 소비 열량을 측정한 값이 아니며 회복·식사 목표에 자동 합산하지 않아요.")
    )
    private fun move(key:String)=ProgramMove(key,2,10)
    private fun session(name:String,vararg keys:String)=ProgramSession(name,keys.map(::move))
    private val a=session("전신 A","goblet-squat","dumbbell-bench","seated-row","glute-bridge")
    private val b=session("전신 B","dumbbell-rdl","chest-press","face-pull","bodyweight-squat")
    private val upper=session("상체","dumbbell-bench","seated-row","face-pull","barbell-curl")
    private val lower=session("하체","goblet-squat","dumbbell-rdl","seated-leg-curl","glute-bridge")
    val programs=listOf(
        TrainingProgram("full-body-2","주 2회 전신 A/B","주 2회부터 시작하는 성인","유지·근육 증가·감량 중 근력 기록",listOf(a,b),listOf("currier2023")),
        TrainingProgram("full-body-3","주 3회 전신","주 3회 운동 시간이 있는 성인","전신 운동을 여러 날로 나눠 기록",listOf(a,b,a.copy(name="전신 C")),listOf("currier2023")),
        TrainingProgram("upper-lower-4","주 4회 상체/하체","기본 동작에 익숙하고 주 4회 가능한 성인","상·하체별 수행과 회복 비교",listOf(upper,lower,upper.copy(name="상체 B"),lower.copy(name="하체 B")),listOf("currier2023")),
        TrainingProgram("machine-2","주 2회 머신 중심","머신을 이용하는 성인","기구 설정을 유지하며 기록 습관 만들기",
            List(2){session("머신 ${if(it==0)"A"else"B"}","leg-press","seated-leg-curl","chest-press","seated-row")},listOf("currier2023")),
        TrainingProgram("home-2","주 2회 맨몸 시작","집에서 시작하는 성인","기구 없이 수행 횟수 기록",
            List(2){session("맨몸 ${if(it==0)"A"else"B"}","bodyweight-squat","push-up","glute-bridge")},listOf("currier2023")),
        TrainingProgram("strength-cardio-2","근력 2회 + 자율 유산소","유산소와 근력을 함께 기록하는 성인","근력 루틴과 유산소 기록을 함께 유지",listOf(a,b),listOf("currier2023","who2020"))
    )
    fun dto()=ProgramCatalogDto("2026-10-04.1",programs,evidence)
}
