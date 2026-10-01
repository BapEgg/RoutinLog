package com.bapegg.routinlog.food

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate

data class CatalogFood(
    val id:String, val name:String, val brand:String?, val category:String,
    val basisLabel:String, val basisAmount:BigDecimal?, val basisUnit:String,
    val nutrition:NutritionValues, val sourceName:String, val sourceUpdatedAt:String,
    val sourceUrl:String="https://various.foodsafetykorea.go.kr/nutrient/general/down/historyList.do",
    val revision:String="", val nutrientNotes:Map<String,String> = emptyMap(),
) {
    @get:com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    val importBlockReason:String? get()=when {
        basisUnit!="g"||basisAmount==null -> "g 기준 영양정보가 아니에요. 제품의 g 기준 표기를 직접 등록해주세요."
        basisAmount.signum()<=0||basisAmount>BigDecimal("100000")||basisAmount.stripTrailingZeros().scale()>2 -> "기준량을 직접 확인해 등록해주세요."
        name.length>80||(brand?.length ?: 0)>80 -> "음식 이름과 브랜드를 확인해 직접 등록해주세요."
        listOf(nutrition.kcal,nutrition.carbsG,nutrition.proteinG,nutrition.fatG,nutrition.fiberG).filterNotNull().any { it.stripTrailingZeros().scale()>2||it>BigDecimal("100000") } -> "원본 영양정보의 정밀도를 지원하지 않아 직접 확인이 필요해요."
        else -> null
    }
}
data class CatalogSearch(val items:List<CatalogFood>,val hasMore:Boolean,val available:Boolean,val page:Int)
data class CatalogSave(val revision:String,val preparation:FoodPreparation=FoodPreparation.UNKNOWN)

@Service
class FoodCatalog(private val jdbc:JdbcTemplate,private val json:ObjectMapper) {
    fun search(query:String,page:Int):CatalogSearch {
        if(query.trim().length !in 1..80||query.contains('\u0000')||page !in 0..100)invalid("검색어는 1~80자로 입력해주세요.")
        val terms=query.trim().lowercase().split(Regex("\\s+")).take(6)
        val where=terms.joinToString(" AND "){"search_text LIKE ? ESCAPE '!'"}
        val args=terms.map { "%"+it.replace("!","!!").replace("%","!%").replace("_","!_")+"%" }.toMutableList<Any>()
        args.add(page*20)
        val rows=jdbc.query("SELECT payload FROM food_catalog WHERE $where ORDER BY id LIMIT 21 OFFSET ?",{rs,_->json.readValue(rs.getString(1),CatalogFood::class.java)},*args.toTypedArray())
        return CatalogSearch(rows.take(20),rows.size>20,jdbc.queryForObject("SELECT COUNT(*) FROM food_catalog",Long::class.java)!!>0,page)
    }
    fun get(id:String):CatalogFood {
        if(!Regex("[A-Za-z0-9_-]{1,80}").matches(id))invalid("식품 코드를 확인해주세요.")
        return jdbc.query("SELECT payload FROM food_catalog WHERE id=?",{rs,_->json.readValue(rs.getString(1),CatalogFood::class.java)},id).firstOrNull()
            ?: throw MealApiException(HttpStatus.NOT_FOUND,"CATALOG_NOT_FOUND","공공 식품 정보를 찾지 못했어요. 다시 검색해주세요.")
    }
    private fun invalid(message:String):Nothing=throw MealApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",message)
}

/** Trusted operator import only: no public upload endpoint and no outbound network from requests. */
@Service
class FoodCatalogImport(private val jdbc:JdbcTemplate,private val json:ObjectMapper) {
    @Transactional
    fun importFile(path:Path):Int {
        require(Files.size(path) in 1..500_000_000) { "Catalog file size invalid" }
        jdbc.queryForObject("SELECT id FROM food_catalog_lock WHERE id=1 FOR UPDATE",Int::class.java)
        val seen=HashSet<String>()
        val existing=jdbc.query("SELECT id,revision FROM food_catalog",{r,_->r.getString(1) to r.getString(2)}).toMap()
        val inserts=ArrayList<Array<Any>>(500);val updates=ArrayList<Array<Any>>(500)
        fun flush() {
            if(inserts.isNotEmpty())jdbc.batchUpdate("INSERT INTO food_catalog(id,search_text,revision,payload) VALUES(?,?,?,?)",inserts.toList())
            if(updates.isNotEmpty())jdbc.batchUpdate("UPDATE food_catalog SET search_text=?,revision=?,payload=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",updates.toList())
            inserts.clear();updates.clear()
        }
        Files.newBufferedReader(path).useLines { lines->lines.forEach { line->
            require(line.length<=50_000) { "Catalog row too large" }
            val food=json.readValue(line,CatalogFood::class.java).copy(revision="")
            validate(food);require(seen.add(food.id)) { "Duplicate catalog code" };require(seen.size<=500_000)
            val revision=java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(food)))
            if(existing[food.id]!=revision) {
                val payload=json.writeValueAsString(food.copy(revision=revision))
                val search="${food.name} ${food.brand.orEmpty()} ${food.category}".lowercase().replace(Regex("\\s+")," ")
                if(food.id in existing)updates.add(arrayOf(search,revision,payload,food.id)) else inserts.add(arrayOf(food.id,search,revision,payload))
                if(inserts.size+updates.size>=500)flush()
            }
        } }
        require(seen.isNotEmpty());flush();return seen.size
    }
    private fun validate(f:CatalogFood) {
        require(Regex("[A-Za-z0-9_-]{1,80}").matches(f.id))
        require(f.name.isNotBlank()&&f.name.length<=500&&(f.brand?.length ?: 0)<=500)
        require(f.category in setOf("음식","가공식품","원재료성식품"))
        require(f.basisLabel.length in 1..80&&f.basisUnit in setOf("g","ml","UNKNOWN"))
        require(f.basisAmount==null||f.basisAmount.signum()>0)
        require(f.sourceName.isNotBlank()&&f.sourceName.length<=200)
        require(f.nutrientNotes.size<=5&&f.nutrientNotes.keys.all { it in setOf("kcal","carbsG","proteinG","fatG","fiberG") }&&f.nutrientNotes.values.all { it.length<=100&&'\u0000' !in it })
        LocalDate.parse(f.sourceUpdatedAt)
        require(f.sourceUrl=="https://various.foodsafetykorea.go.kr/nutrient/general/down/historyList.do")
        require(listOf(f.name,f.brand.orEmpty(),f.basisLabel,f.sourceName).none { '\u0000' in it })
        listOf(f.nutrition.kcal,f.nutrition.carbsG,f.nutrition.proteinG,f.nutrition.fatG,f.nutrition.fiberG).filterNotNull().forEach { require(it.signum()>=0&&it<=BigDecimal("1000000")&&it.scale()<=10) }
    }
}

@Component
class FoodCatalogLoader(private val importer:FoodCatalogImport,@param:Value("\${routinlog.food-catalog.import-file:}")private val file:String):ApplicationRunner {
    override fun run(args:ApplicationArguments) { if(file.isNotBlank())importer.importFile(Path.of(file)) }
}
