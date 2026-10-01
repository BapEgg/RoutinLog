package com.bapegg.routinlog.data
import com.bapegg.routinlog.auth.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class ReportRepositoryTest {
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
    @Test fun `weekly report transports decimal targets and preserves missing observations`()=runBlocking {
        server.enqueue(json("""{"from":"2026-09-21","to":"2026-09-27","weekEnd":"2026-09-27","latestWeek":"2026-09-28","timeZone":"Asia/Seoul","generatedAt":"2026-10-01T00:00:00Z",
          "nutrition":[{"key":"proteinG","target":1190.50,"targetDays":7,"recorded":null,"knownItems":0,"missingItems":2}],
          "mealDays":[],"workouts":[],"body":[],"weight":{"value":null,"count":0,"previous":83.125,"previousCount":2,"change":null},
          "waist":{"value":null,"count":0,"previous":null,"previousCount":0,"change":null},"conditions":[],"steps":[]}"""))
        val report=repo.weeklyReport("test-user","2026-09-21")
        val sent=server.takeRequest()
        assertEquals("/api/v1/reports/weekly?week=2026-09-21",sent.path)
        assertEquals("Bearer test-current",sent.getHeader("Authorization"))
        assertEquals(java.math.BigDecimal("1190.50"),report.nutrition.single().target)
        assertNull(report.nutrition.single().recorded);assertNull(report.weight.change)
        assertEquals(java.math.BigDecimal("83.125"),report.weight.previous)
        assertTrue(report.steps.isEmpty())
    }
    @Test fun `request from an old account never uses another accounts credentials`()=runBlocking {
        try { repo.weeklyReport("another-user",null);fail("Owner must match") }catch(e:AccountException){assertEquals(AccountErrorKind.CANCELLED,e.kind)}
        assertNull(server.takeRequest(100,TimeUnit.MILLISECONDS))
    }
    private fun json(value:String)=MockResponse().setHeader("Content-Type","application/json").setBody(value)
}
