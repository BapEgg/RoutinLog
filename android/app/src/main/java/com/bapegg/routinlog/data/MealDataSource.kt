package com.bapegg.routinlog.data

/** Authenticated online storage. The UI retains drafts; server responses confirm actual saves. */
interface MealDataSource {
    suspend fun listFoods(): List<FoodDto>
    suspend fun saveFood(id: String, food: FoodWrite): FoodDto
    suspend fun deleteFood(id: String, version: Long)
    suspend fun listMealTemplates(): List<MealTemplateDto>
    suspend fun saveMealTemplate(id: String, template: MealTemplateWrite): MealTemplateDto
    suspend fun deleteMealTemplate(id: String, version: Long)
    suspend fun getMealPlan(): MealPlanDto
    suspend fun saveMealPlan(plan: MealPlanWrite): MealPlanDto
    suspend fun getMealDay(date: String): MealDayDto
    suspend fun saveMeal(id: String, meal: MealWrite): MealDto
    suspend fun deleteMeal(id: String, version: Long)
    suspend fun deleteMealDayPlan(date:String,slotId:String,version:Long):Unit = throw UnsupportedOperationException("Dated meal plans are not supported by this data source")
}
