package com.bapegg.routinlog.data
import com.bapegg.routinlog.auth.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class ConditionRepositoryTest {
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
    @Test fun `authenticated write preserves null observations and minute precision`()=runBlocking {
        server.enqueue(json("""{"date":"2026-10-01","version":0,"values":{"sleepMinutes":395,"soreness":"NONE"}}"""))
        val saved=repo.saveCondition("2026-10-01",ConditionWrite(ConditionValues(sleepMinutes=395,soreness="NONE")))
        val request=server.takeRequest(2,TimeUnit.SECONDS)!!
        assertEquals("Bearer test-current",request.getHeader("Authorization"));assertEquals("PUT",request.method)
        assertEquals("/api/v1/conditions/2026-10-01",request.path)
        val body=JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertTrue(body["version"].isJsonNull);assertTrue(body.getAsJsonObject("values")["fatigue"].isJsonNull)
        assertEquals(395,saved.values.sleepMinutes);assertNull(saved.values.activity)
    }
    @Test fun `date range and optimistic delete are transported unchanged`()=runBlocking {
        server.enqueue(json("""{"items":[]}"""));assertTrue(repo.listConditions("2026-09-01","2026-09-30").isEmpty())
        assertEquals("/api/v1/conditions?from=2026-09-01&to=2026-09-30",server.takeRequest().path)
        server.enqueue(MockResponse().setResponseCode(204));repo.deleteCondition("2026-09-30",3)
        assertEquals("/api/v1/conditions/2026-09-30?version=3",server.takeRequest().path)
    }
    private fun json(value:String)=MockResponse().setHeader("Content-Type","application/json").setBody(value)
}
