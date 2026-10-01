package com.bapegg.routinlog.data
import com.bapegg.routinlog.auth.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class WorkoutReviewRepositoryTest {
    private val server=MockWebServer()
    private lateinit var repo:AccountRepository
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
    private val reviewJson="""{"week":"2026-09-21","targetWeek":"2026-09-28","targetDate":"2026-09-30","routineName":"legs",
      "exercise":{"id":"exercise","name":"squat","equipment":"smith","target":"quads","recordType":"WEIGHT_REPS","loadConvention":"MACHINE"},
      "planned":[{"setId":"set","weightKg":100,"reps":10,"durationSeconds":null}],"alternative":null,"suggestedChoice":"KEEP","goal":"GAIN",
      "reason":"review","observations":[],"limitations":[],"evidence":[],"policyVersion":"workout-review-1","status":"DRAFT","version":0}"""
    @Test fun `prepare and decision retain exact set targets owner credentials and reason`()=runBlocking {
        server.enqueue(json(reviewJson))
        val view=repo.prepareReview("test-user","2026-09-21",ReviewPrepare())
        val prepared=server.takeRequest();assertEquals("/api/v1/workout-reviews/2026-09-21/prepare",prepared.path)
        assertEquals("POST",prepared.method);assertEquals("Bearer test-current",prepared.getHeader("Authorization"))
        assertEquals(java.math.BigDecimal("100"),view.planned.single().weightKg)
        server.enqueue(json(reviewJson))
        repo.decideReview("test-user",view.week,ReviewDecision(0,"APPLY","CUSTOM",listOf(ReviewTarget("set",java.math.BigDecimal("75.125"),9)),"personal choice"))
        val request=server.takeRequest();val body=JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("/api/v1/workout-reviews/2026-09-21/decision",request.path)
        assertEquals("75.125",body["targets"].asJsonArray[0].asJsonObject["weightKg"].asString)
        assertEquals("personal choice",body["reason"].asString)
    }
    @Test fun `only specific missing draft is interpreted as absent while stale error is retained`()=runBlocking {
        server.enqueue(json("""{"code":"REVIEW_NOT_FOUND"}""").setResponseCode(404))
        assertNull(repo.getReview("test-user","2026-09-21"));server.takeRequest()
        server.enqueue(json("""{"code":"REVIEW_STALE"}""").setResponseCode(409))
        try {repo.prepareReview("test-user","2026-09-21",ReviewPrepare(0));fail("Expected stale response")}
        catch(e:AccountException){assertEquals("REVIEW_STALE",e.code);assertEquals(AccountErrorKind.CONFLICT,e.kind)}
    }
    @Test fun `previous owner cannot prepare or submit a decision using new credentials`()=runBlocking {
        try { repo.prepareReview("another-user","2026-09-21",ReviewPrepare());fail("Owner must match") }
        catch(e:AccountException){assertEquals(AccountErrorKind.CANCELLED,e.kind)}
        assertNull(server.takeRequest(100,TimeUnit.MILLISECONDS))
    }
    private fun json(value:String)=MockResponse().setHeader("Content-Type","application/json").setBody(value)
}
