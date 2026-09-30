package com.bapegg.routinlog.ui.screens

internal data class WorkoutProgram(val id: String, val name: String, val subtitle: String, val frequency: String, val minutes: String, val place: String, val context: String, val advantage: String, val constraint: String, val icon: String, val priorities: List<Int>, val sources: List<String>, val sessions: List<WorkoutProgramDay>)
internal data class WorkoutProgramDay(val name: String, val exercises: List<String>)

// Existing design-reference program catalog; these are service-authored templates, not trial-validated prescriptions.
internal val workoutPrograms = listOf(
    WorkoutProgram("P01", "전신 A/B", "바쁜 주에도 전신을 차곡차곡", "근력 주 2일", "35–55분", "헬스장", "방문 가능한 날이 적거나 주 2회부터 시작하는 분", "방문 횟수가 적어도 전신 동작을 주간 계획에 넣기 쉬워요.", "한 세션에 여러 부위를 다뤄요. 시간이 짧으면 우선 종목부터 정해요.", "CalendarDays",
        listOf(1, 3, 2), listOf("currier2023", "acsm2026"), listOf(
            WorkoutProgramDay("전신 A", listOf("고블릿 스쿼트", "체스트 프레스", "시티드 로우", "덤벨 루마니안 데드리프트", "플랭크")),
            WorkoutProgramDay("전신 B", listOf("레그프레스", "랫풀다운", "인클라인 덤벨 프레스", "시티드 레그컬", "덤벨 숄더 프레스")),
        )),
    WorkoutProgram("P02", "전신 A/B/C", "주 3번, 전신 수행을 이어가기", "근력 주 3일", "40–60분", "헬스장", "주 3회 방문하며 전신 동작을 나눠 연습하고 싶은 분", "주간 반복 기회를 확보하며 세션마다 종목을 조금씩 바꿀 수 있어요.", "전신 운동이 반복돼요. 전날 수행·피로·불편함을 함께 확인해요.", "Dumbbell",
        listOf(4, 1, 4), listOf("currier2023", "acsm2026"), listOf(
            WorkoutProgramDay("전신 A", listOf("고블릿 스쿼트", "덤벨 벤치프레스", "시티드 로우", "시티드 레그컬")),
            WorkoutProgramDay("전신 B", listOf("덤벨 루마니안 데드리프트", "랫풀다운", "덤벨 숄더 프레스", "스플릿 스쿼트")),
            WorkoutProgramDay("전신 C", listOf("레그프레스", "체스트 프레스", "시티드 로우", "힙 스러스트")),
        )),
    WorkoutProgram("P03", "상체 · 하체 4일", "부위별 운동을 나눠 집중하기", "근력 주 4일", "40–60분", "헬스장", "기본 동작에 익숙하고 주 4회 방문할 수 있는 분", "상체와 하체의 세트를 서로 다른 날에 나눠 기록하기 좋아요.", "주 4일 일정이 필요해요. 방문이 줄면 주 2~3일 전신 구성도 비교해요.", "Repeat2",
        listOf(6, 2, 6), listOf("ramos2024", "acsm2026"), listOf(
            WorkoutProgramDay("상체 A", listOf("덤벨 벤치프레스", "시티드 로우", "덤벨 숄더 프레스", "랫풀다운")),
            WorkoutProgramDay("하체 A", listOf("고블릿 스쿼트", "덤벨 루마니안 데드리프트", "시티드 레그컬", "카프레이즈")),
            WorkoutProgramDay("상체 B", listOf("인클라인 덤벨 프레스", "랫풀다운", "덤벨 레터럴 레이즈", "시티드 로우")),
            WorkoutProgramDay("하체 B", listOf("레그프레스", "힙 스러스트", "레그익스텐션", "시티드 레그컬")),
        )),
    WorkoutProgram("P04", "머신 중심 전신", "기구 설정과 기록을 간단하게", "근력 주 2~3일", "35–55분", "헬스장 · 머신", "머신을 선호하거나 기구별 기준을 정해 기록하고 싶은 분", "기구와 좌석 설정을 저장해 다음 수행을 비교하기 쉬워요.", "헬스장마다 기구가 달라요. 기구 교체 시 중량을 그대로 환산하지 않아요.", "Dumbbell",
        listOf(2, 4, 3), listOf("acsm2026", "currier2023"), listOf(
            WorkoutProgramDay("머신 전신 A", listOf("레그프레스", "체스트 프레스", "시티드 로우", "시티드 레그컬", "머신 숄더 프레스")),
            WorkoutProgramDay("머신 전신 B", listOf("레그프레스", "랫풀다운", "체스트 프레스", "시티드 레그컬", "카프레이즈")),
        )),
    WorkoutProgram("P05", "덤벨 · 밴드 전신", "집에서도 같은 기준으로 기록", "근력 주 2~3일", "30–45분", "집 · 덤벨·밴드", "집에서 덤벨과 밴드로 운동하는 분", "이동 시간을 줄이고 자주 사용하는 장비로 반복할 수 있어요.", "덤벨 무게와 밴드 강도를 확인해요. 부하가 부족하면 반복·동작을 함께 검토해요.", "House",
        listOf(3, 5, 5), listOf("acsm2026", "currier2023"), listOf(
            WorkoutProgramDay("홈 전신 A", listOf("고블릿 스쿼트", "덤벨 플로어 프레스", "원암 덤벨 로우", "덤벨 루마니안 데드리프트")),
            WorkoutProgramDay("홈 전신 B", listOf("스플릿 스쿼트", "덤벨 숄더 프레스", "밴드 로우", "글루트 브리지")),
        )),
    WorkoutProgram("P06", "근력 + 지속 유산소", "근력 기록과 활동 시간을 함께", "근력 주 2~3일 + 유산소", "근력 35–50분", "헬스장 · 걷기·자전거", "근력운동을 이어가며 유산소 시간을 확보하고 싶은 분", "감량 중에도 근력 기록을 유지하고 활동 시간을 따로 볼 수 있어요.", "유산소가 더해지므로 실제 시간·난도·피로에 맞춰 조절해요.", "Footprints",
        listOf(5, 6, 1), listOf("binmahfoz2025", "acsm2026"), listOf(
            WorkoutProgramDay("근력 전신 A", listOf("레그프레스", "체스트 프레스", "시티드 로우", "시티드 레그컬")),
            WorkoutProgramDay("근력 전신 B", listOf("고블릿 스쿼트", "랫풀다운", "덤벨 프레스", "힙 스러스트")),
        )),
)
