package com.bapegg.routinlog.workout

import java.nio.charset.StandardCharsets
import java.util.UUID

data class CatalogExercise(val key:String,val name:String,val equipment:String,val target:String,val group:String,
    val recordType:RecordType,val loadConvention:LoadConvention,val aliases:List<String>,val recordingHint:String)
data class ExerciseCatalogDto(val revision:String,val sourceName:String,val sourceUrl:String,val items:List<CatalogExercise>)

/** Service-authored recording defaults. Names/equipment/body regions checked against NASM's exercise library.
 * No copied media, form instructions, progression, starting weights or automatic substitute recommendations.
 * Stable keys must not be recycled; imported IDs preserve user edits on subsequent imports. */
object ExerciseCatalog {
    private fun weight(key:String,name:String,equipment:String,target:String,group:String,load:LoadConvention,vararg aliases:String)=
        CatalogExercise(key,name,equipment,target,group,RecordType.WEIGHT_REPS,load,aliases.toList(),when(load) {
            LoadConvention.PER_HAND->"덤벨 한 개의 무게를 적어요. 양손 합계를 적고 싶다면 가져온 뒤 기준을 바꿔주세요."
            LoadConvention.TOTAL->"봉과 원판을 합한 총중량을 적어요. 고블릿 스쿼트는 들고 있는 덤벨 한 개의 무게예요."
            else->"사용하는 기구의 표시 중량을 적어요. 기구가 바뀌면 같은 중량으로 비교하지 않아요."
        })
    val items=listOf(
        weight("barbell-squat","바벨 백 스쿼트","바벨","대퇴사두·둔근","하체",LoadConvention.TOTAL,"스쿼트","프리 스쿼트","back squat"),
        weight("goblet-squat","고블릿 스쿼트","덤벨","대퇴사두·둔근","하체",LoadConvention.TOTAL,"goblet squat"),
        weight("leg-press","레그프레스","머신","대퇴사두·둔근","하체",LoadConvention.MACHINE,"레그 프레스","leg press"),
        weight("seated-leg-curl","시티드 레그컬","머신","햄스트링","하체",LoadConvention.MACHINE,"레그 컬","seated leg curl"),
        weight("barbell-rdl","바벨 루마니안 데드리프트","바벨","햄스트링·둔근","하체",LoadConvention.TOTAL,"루마니안","RDL"),
        weight("dumbbell-rdl","덤벨 루마니안 데드리프트","덤벨","햄스트링·둔근","하체",LoadConvention.PER_HAND,"덤벨 RDL"),
        weight("barbell-deadlift","바벨 데드리프트","바벨","하체·등","하체",LoadConvention.TOTAL,"데드리프트","deadlift"),
        weight("split-squat","덤벨 불가리안 스플릿 스쿼트","덤벨·벤치","대퇴사두·둔근","하체",LoadConvention.PER_HAND,"불가리안","split squat")
            .let { it.copy(recordingHint=it.recordingHint+" 좌우 횟수가 다르면 세트를 나눠 메모에 방향을 적어요.") },
        weight("barbell-bench","바벨 벤치프레스","바벨·벤치","가슴·삼두","가슴",LoadConvention.TOTAL,"벤치 프레스","bench press"),
        weight("dumbbell-bench","덤벨 벤치프레스","덤벨·벤치","가슴·어깨","가슴",LoadConvention.PER_HAND,"덤벨 프레스","dumbbell chest press"),
        weight("incline-dumbbell-bench","인클라인 덤벨 프레스","덤벨·벤치","가슴·어깨","가슴",LoadConvention.PER_HAND,"인클라인","incline press"),
        weight("chest-press","체스트 프레스","머신","가슴·삼두","가슴",LoadConvention.MACHINE,"체스트프레스","chest press"),
        weight("seated-row","시티드 케이블 로우","케이블","등·팔","등",LoadConvention.MACHINE,"시티드 로우","로우","seated row"),
        weight("face-pull","케이블 페이스풀","케이블","어깨·등","어깨",LoadConvention.MACHINE,"페이스 풀","face pull"),
        weight("cable-fly","케이블 플라이","케이블","가슴·어깨","가슴",LoadConvention.MACHINE,"케이블 크로스오버","cable fly")
            .let { it.copy(recordingHint="한쪽 케이블에 표시된 중량을 적어요. 좌우 중량이 다르면 메모를 남겨주세요.") },
        weight("barbell-curl","바벨 컬","바벨","이두","팔",LoadConvention.TOTAL,"이두 컬","bicep curl"),
        CatalogExercise("push-up","푸시업","맨몸","가슴·삼두","가슴",RecordType.REPS,LoadConvention.BODYWEIGHT,listOf("팔굽혀펴기","push up"),"완료한 횟수를 적어요. 추가 중량을 쓰면 가져온 뒤 기록 방식을 바꿀 수 있어요."),
        CatalogExercise("pull-up","풀업","철봉","등·팔","등",RecordType.REPS,LoadConvention.BODYWEIGHT,listOf("턱걸이","pull up"),"완료한 횟수를 적어요. 보조 밴드나 추가 중량은 메모에 남겨 비교 조건을 구분해주세요."),
        CatalogExercise("plank","플랭크","맨몸","몸통","몸통",RecordType.DURATION,LoadConvention.BODYWEIGHT,listOf("plank"),"유지한 시간을 초 단위로 적어요."),
        CatalogExercise("side-plank","사이드 플랭크","맨몸","몸통","몸통",RecordType.DURATION,LoadConvention.BODYWEIGHT,listOf("side plank"),"유지한 시간을 초 단위로 적어요. 좌우는 세트를 나누고 메모에 방향을 남겨주세요."),
        CatalogExercise("glute-bridge","글루트 브리지","맨몸","둔근·몸통","하체",RecordType.REPS,LoadConvention.BODYWEIGHT,listOf("힙 브리지","glute bridge"),"완료한 횟수를 적어요. 추가 중량을 사용하면 기록 방식을 바꿔주세요."),
        CatalogExercise("bodyweight-squat","맨몸 스쿼트","맨몸","대퇴사두·둔근","하체",RecordType.REPS,LoadConvention.BODYWEIGHT,listOf("bodyweight squat"),"완료한 횟수를 적어요. 바벨·덤벨을 쓰는 스쿼트와 구분해서 기록해요."),
    )
    fun dto()=ExerciseCatalogDto("2026-10-02.1","NASM Exercise Library","https://www.nasm.org/resource-center/exercise-library",items)
    fun importedId(key:String):UUID=UUID.nameUUIDFromBytes("routinlog:exercise-catalog:$key".toByteArray(StandardCharsets.UTF_8))
}
