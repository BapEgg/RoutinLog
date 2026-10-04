package com.bapegg.routinlog.cardio

import java.math.BigDecimal
import java.math.RoundingMode

data class MetActivity(val code:String,val label:String,val met:BigDecimal,val sourceUrl:String)
data class CardioEstimate(val code:String,val label:String,val met:BigDecimal,val weightKg:BigDecimal,val weightSource:String,
    val totalKcal:BigDecimal,val activeKcal:BigDecimal,val method:String="COMPENDIUM_2024_MET_KG_HOUR",val sourceUrl:String)
object CardioEstimation {
    val activities=listOf(
        MetActivity("17355","트레드밀 걷기 · 4.8~5.5 km/h · 경사 0%",BigDecimal("3.8"),"https://pacompendium.com/walking/"),
        MetActivity("17358","트레드밀 걷기 · 5.6~6.3 km/h · 경사 0%",BigDecimal("4.8"),"https://pacompendium.com/walking/"),
        MetActivity("01214","실내 자전거 · 50 W",BigDecimal("4.0"),"https://pacompendium.com/bicycling/"),
        MetActivity("01220","실내 자전거 · 90~100 W",BigDecimal("6.0"),"https://pacompendium.com/bicycling/"),
        MetActivity("02048","일립티컬 · 보통 강도",BigDecimal("5.0"),"https://pacompendium.com/conditioning-exercise/")
    )
    fun calculate(code:String,weight:BigDecimal,minutes:Int,weightSource:String):CardioEstimate {
        val activity=activities.single { it.code==code }
        require(weight>BigDecimal.ZERO&&weight<=BigDecimal("1000")&&minutes in 1..1440)
        fun kcal(met:BigDecimal)=(met*weight*minutes.toBigDecimal()).divide(BigDecimal("60"),0,RoundingMode.HALF_UP)
        return CardioEstimate(code,activity.label,activity.met,weight,weightSource,kcal(activity.met),kcal(activity.met-BigDecimal.ONE),sourceUrl=activity.sourceUrl)
    }
}
