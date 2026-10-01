package com.bapegg.routinlog.data
import com.bapegg.routinlog.auth.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class StepsRepositoryTest {
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
    @Test fun `step requests preserve bounds and account ownership`()=runBlocking {
        server.enqueue(json("""{"active":null,"serverTime":"2026-10-01T00:00:00Z"}"""))
        assertNull(repo.stepConnection("test-user").active)
        assertEquals("Bearer test-current",server.takeRequest().getHeader("Authorization"))
        server.enqueue(MockResponse().setResponseCode(204))
        repo.saveSteps("test-user","connection",StepBatch(listOf(StepObservation("2026-09-30","2026-09-30T00:00:00Z","2026-10-01T00:00:00Z",3000))))
        val sent=server.takeRequest()
        assertEquals("/api/v1/step-connections/connection/days",sent.path)
        assertEquals(3000,JsonParser.parseString(sent.body.readUtf8()).asJsonObject["items"].asJsonArray[0].asJsonObject["steps"].asInt)
    }
    @Test fun `old owners sensor values never use new account credentials`()=runBlocking {
        try { repo.saveSteps("another-user","id",StepBatch(emptyList()));fail("Owner must match") }catch(e:AccountException){assertEquals(AccountErrorKind.CANCELLED,e.kind)}
        assertNull(server.takeRequest(100,TimeUnit.MILLISECONDS))
    }
    private fun json(value:String)=MockResponse().setHeader("Content-Type","application/json").setBody(value)
}
