package com.bapegg.routinlog.food

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.io.IOException
import kotlin.test.*

class FoodUrlSecurityTest {
    @Test fun `only credential-free public HTTPS DNS names on default port are accepted`() {
        listOf("http://example.com/a", "https://user:secret@example.com/a", "file:///etc/passwd", "https://example.com:8080/", "https://example.com/a#b",
            "https://127.1/", "https://2130706433/", "https://[::1]/", "https://localhost/", "https://a.local/", "https://a.internal/", "https://example.com\\@localhost/").forEach {
            assertFailsWith<FoodUrlProblem>(it) { PublicFoodAddress.parse(it) }
        }
        assertEquals("example.com", PublicFoodAddress.parse("https://example.com/product?id=1").host)
    }
    @Test fun `nonpublic IPv4 IPv6 and mapped or transition addresses are rejected`() {
        listOf("0.0.0.0", "10.2.3.4", "100.64.0.1", "127.0.0.1", "169.254.169.254", "172.16.0.1", "192.168.1.1", "192.0.0.1",
            "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "255.255.255.255", "::", "::1", "fe80::1", "fc00::1",
            "::ffff:127.0.0.1", "64:ff9b::7f00:1", "2001:db8::1", "2002:7f00:1::1", "2001::1", "3fff::1").forEach {
            assertFalse(PublicFoodAddress.isPublic(InetAddress.getByName(it)), it)
        }
        listOf("8.8.8.8", "1.1.1.1", "2001:4860:4860::8888", "2606:4700:4700::1111").forEach { assertTrue(PublicFoodAddress.isPublic(InetAddress.getByName(it)), it) }
    }
    @Test fun `DNS rejects mixed answers and returns the exact validated lookup without resolving twice`() {
        var lookups = 0
        val public = InetAddress.getByName("8.8.8.8")
        val dns = PublicFoodDns(resolver { lookups++; listOf(public) })
        assertEquals(listOf(public), dns.lookup("example.com")); assertEquals(1, lookups)
        assertFailsWith<FoodUrlProblem> { PublicFoodDns(resolver { listOf(public, InetAddress.getByName("127.0.0.1")) }).lookup("example.com") }
        assertFailsWith<FoodUrlProblem> { PublicFoodDns(resolver { emptyList() }).lookup("example.com") }
    }
    private fun resolver(lookup: () -> List<InetAddress>) = object : Dns { override fun lookup(hostname: String) = lookup() }
    private fun fetcher(answer: (Request) -> Response) = SafeFoodPageFetcher(OkHttpClient.Builder().addInterceptor { answer(it.request()) }.build())
    private fun response(request: Request, code: Int = 200, html: String = "<html>test</html>", type: String = "text/html", redirect: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(html.toResponseBody(type.toMediaType()))
            .apply { if (redirect != null) header("Location", redirect) }.build()
    @Test fun `redirect cannot escape to a private host or cleartext request`() {
        listOf("https://127.0.0.1/", "http://example.com/", "https://a.internal/", "https://user:password@example.com/").forEach { target ->
            var calls = 0
            assertFailsWith<FoodUrlProblem> { fetcher { calls++; response(it, 302, redirect = target) }.fetch("https://example.com/") }
            assertEquals(1, calls)
        }
    }
    @Test fun `redirect count body size and content type are bounded`() {
        var calls = 0
        assertFailsWith<FoodUrlProblem> { fetcher { calls++; response(it, 302, redirect = "/$calls") }.fetch("https://example.com/") }
        assertEquals(3, calls)
        assertEquals(FoodUrlFailure.TOO_LARGE, assertFailsWith<FoodUrlProblem> { fetcher { response(it, html = "x".repeat(SafeFoodPageFetcher.MAX_BYTES + 1)) }.fetch("https://example.com/") }.reason)
        assertEquals(FoodUrlFailure.UNSUPPORTED_PAGE, assertFailsWith<FoodUrlProblem> { fetcher { response(it, type = "application/pdf") }.fetch("https://example.com/") }.reason)
    }
    @Test fun `retailer denial missing page and transport errors have distinct reasons`() {
        mapOf(403 to FoodUrlFailure.ACCESS_BLOCKED, 404 to FoodUrlFailure.PAGE_NOT_FOUND, 500 to FoodUrlFailure.CONNECTION_FAILED).forEach { (code, reason) ->
            assertEquals(reason, assertFailsWith<FoodUrlProblem> { fetcher { response(it, code) }.fetch("https://example.com/") }.reason)
        }
        assertEquals(FoodUrlFailure.CONNECTION_FAILED, assertFailsWith<FoodUrlProblem> { fetcher { throw IOException("secret URL must not be returned") }.fetch("https://example.com/") }.reason)
    }
    @Test fun `requests never contain user session cookies or authorization and preserve final source`() {
        val page = fetcher {
            assertNull(it.header("Authorization")); assertNull(it.header("Cookie")); assertEquals("identity", it.header("Accept-Encoding"))
            response(it)
        }.fetch("https://example.com/product?id=1")
        assertEquals("https://example.com/product?id=1", page.url)
    }
}
