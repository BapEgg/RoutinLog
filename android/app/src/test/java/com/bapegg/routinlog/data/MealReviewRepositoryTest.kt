package com.bapegg.routinlog.data

import com.bapegg.routinlog.auth.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class MealReviewRepositoryTest {
    private val server=MockWebServer();private lateinit var repo:AccountRepository
    @Before fun setup()=runBlocking {
        server.start(InetAddress.getByName("127.0.0.1"),0)
        repo=AccountRepository.forTesting(server.url("/").newBuilder().host("127.0.0.1").build().toString(),object:SessionStore {
            var current:StoredSession?=StoredSession("test-access","test-refresh",9000,"test-user")
            override fun read()=current
            override fun write(session:StoredSession){current=session}
            override fun clear(){current=null}
        }){1000}
        server.enqueue(json("""{"accessToken":"test-current","refreshToken":"test-rotated","expiresIn":3600,"userId":"test-user"}"""))
        repo.restoreSession();server.takeRequest();Unit
    }
    @After fun cleanup(){server.shutdown()}
    private val emptyReview="""{"week":"2026-09-21","targetDate":null,"slotId":null,"slotLabel":null,"target":null,"otherItems":[],"dayPlanComplete":false,"options":[],"suggestedOptionId":null,"nutrition":[],"observations":[],"reason":"no plan","limitations":[],"evidence":[],"policyVersion":"meal-review-1","status":"DRAFT","version":0}"""
    @Test fun `prepare and decision use owner authorization and exact grams without nutrition writes`()=runBlocking {
        server.enqueue(json(emptyReview));repo.prepareMealReview("test-user","2026-09-21",ReviewPrepare())
        val prepared=server.takeRequest();assertEquals("/api/v1/meal-reviews/2026-09-21/prepare",prepared.path);assertEquals("Bearer test-current",prepared.getHeader("Authorization"))
        server.enqueue(json(emptyReview));repo.decideMealReview("test-user","2026-09-21",MealReviewDecision(0,"APPLY","option",listOf(MealReviewAmount("food",BigDecimal("120.25"))),"내 선택"))
        val req=server.takeRequest();assertEquals("/api/v1/meal-reviews/2026-09-21/decision",req.path)
        val payload=JsonParser.parseString(req.body.readUtf8()).asJsonObject
        assertEquals("120.25",payload["amounts"].asJsonArray[0].asJsonObject["grams"].asString);assertFalse(payload.has("nutrition"));assertEquals("내 선택",payload["reason"].asString)
    }
    @Test fun `old owner cannot submit a meal choice under another account`()=runBlocking {
        try {repo.prepareMealReview("other","2026-09-21",ReviewPrepare());fail("Owner mismatch")}
        catch(e:AccountException){assertEquals(AccountErrorKind.CANCELLED,e.kind)}
        assertNull(server.takeRequest(100,TimeUnit.MILLISECONDS))
    }
    @Test fun `missing review is absent and date plan deletion includes date slot and revision`()=runBlocking {
        server.enqueue(json("""{"code":"REVIEW_NOT_FOUND"}""").setResponseCode(404));assertNull(repo.getMealReview("test-user","2026-09-21"));server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(204));repo.deleteMealDayPlan("2026-10-05","slot",0)
        val request=server.takeRequest();assertEquals("DELETE",request.method);assertEquals("/api/v1/meal-day-plans/2026-10-05/slot?version=0",request.path)
    }
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
}
