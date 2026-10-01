package com.bapegg.routinlog.food

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

enum class FoodUrlFailure(val message: String) {
    INVALID_URL("https://로 시작하는 공개 상품 주소를 입력해주세요. 로그인 정보가 포함된 주소는 사용할 수 없어요."),
    UNSAFE_ADDRESS("이 주소는 상품 정보 확인에 사용할 수 없어요. 공개된 상품 페이지 주소를 확인해주세요."),
    ACCESS_BLOCKED("판매처에서 자동 접근을 제한하거나 로그인이 필요해요. 영양성분표 사진으로 등록해주세요."),
    PAGE_NOT_FOUND("상품 페이지를 찾지 못했어요. 주소가 바뀌거나 상품이 삭제됐을 수 있어요."),
    CONNECTION_FAILED("상품 페이지에 연결하지 못했어요. 주소를 확인하거나 잠시 후 다시 시도해주세요."),
    TOO_LARGE("상품 페이지가 너무 커서 읽지 못했어요. 영양성분표 사진으로 등록해주세요."),
    UNSUPPORTED_PAGE("이 주소의 문서 형식을 읽을 수 없어요. 상품 페이지 주소나 영양성분표 사진을 사용해주세요."),
    NO_NUTRITION("읽을 수 있는 영양정보를 찾지 못했어요. 이미지나 화면 로딩 후 표시되는 정보는 사진으로 등록해주세요."),
    AMBIGUOUS("여러 상품이나 기준량의 영양정보가 함께 있어요. 해당 제품의 영양성분표 사진으로 등록해주세요."),
    UNSUPPORTED_BASIS("g 기준 영양정보를 확인하지 못했어요. mL·1개를 g으로 바꾸지 않으니 제품 표기를 직접 확인해주세요."),
    TOO_MANY_REQUESTS("상품 확인 요청이 많아요. 잠시 후 다시 시도해주세요."),
}
internal class FoodUrlProblem(val reason: FoodUrlFailure) : RuntimeException(reason.name)
internal fun urlFail(reason: FoodUrlFailure): Nothing = throw FoodUrlProblem(reason)
data class FetchedFoodPage(val url: String, val html: String)
fun interface FoodPageFetcher { fun fetch(url: String): FetchedFoodPage }

/** Every redirect and the exact DNS addresses handed to the socket are checked. No proxy/cookies/auth. */
internal object PublicFoodAddress {
    fun parse(value: String): HttpUrl {
        if (value.length !in 1..2048 || value.any { it.isWhitespace() || it.code < 32 } || '\\' in value)
            urlFail(FoodUrlFailure.INVALID_URL)
        val url = value.toHttpUrlOrNull() ?: urlFail(FoodUrlFailure.INVALID_URL)
        if (!url.isHttps || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null)
            urlFail(FoodUrlFailure.INVALID_URL)
        val host = url.host
        if (host.length > 253 || !host.matches(Regex("[a-z0-9-]+(?:\\.[a-z0-9-]+)+")) || host.none { it in 'a'..'z' } ||
            host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".test"))
            urlFail(FoodUrlFailure.UNSAFE_ADDRESS)
        return url
    }
    fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 255 }
        if (bytes.size == 4) {
            val a = bytes[0]; val b = bytes[1]; val c = bytes[2]
            return !(a == 0 || a == 10 || a == 127 || a >= 224 || a == 100 && b in 64..127 || a == 169 && b == 254 ||
                a == 172 && b in 16..31 || a == 192 && (b == 168 || b == 0 && c in setOf(0, 2) || b == 88 && c == 99) ||
                a == 198 && (b in 18..19 || b == 51 && c == 100) || a == 203 && b == 0 && c == 113)
        }
        // Global unicast only; reject special-purpose, documentation and IPv4 transition ranges.
        return bytes.size == 16 && bytes[0] in 0x20..0x3f &&
            !(bytes[0] == 0x20 && bytes[1] == 0x01 && (bytes[2] < 2 || bytes[2] == 0x0d && bytes[3] == 0xb8)) &&
            !(bytes[0] == 0x20 && bytes[1] == 0x02) && !(bytes[0] == 0x3f && bytes[1] == 0xff)
    }
}
internal class PublicFoodDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (addresses.isEmpty() || addresses.any { !PublicFoodAddress.isPublic(it) }) urlFail(FoodUrlFailure.UNSAFE_ADDRESS)
        return addresses
    }
}

@Component
class SafeFoodPageFetcher internal constructor(private val client: OkHttpClient) : FoodPageFetcher {
    @org.springframework.beans.factory.annotation.Autowired
    constructor() : this(OkHttpClient.Builder().dns(PublicFoodDns()).proxy(Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS).build())

    override fun fetch(url: String): FetchedFoodPage {
        var current = PublicFoodAddress.parse(url)
        val visited = mutableSetOf<String>()
        try {
            repeat(3) {
                if (!visited.add(current.toString())) urlFail(FoodUrlFailure.CONNECTION_FAILED)
                client.newCall(Request.Builder().url(current).header("User-Agent", "RoutinLog-NutritionPreview/1.0")
                    .header("Accept", "text/html,application/xhtml+xml").header("Accept-Encoding", "identity").build()).execute().use { response ->
                    if (response.code in setOf(301, 302, 303, 307, 308)) {
                        val next = response.header("Location")?.let { current.resolve(it) } ?: urlFail(FoodUrlFailure.CONNECTION_FAILED)
                        current = PublicFoodAddress.parse(next.toString())
                        return@use
                    }
                    if (response.code in setOf(401, 403, 429)) urlFail(FoodUrlFailure.ACCESS_BLOCKED)
                    if (response.code in setOf(404, 410)) urlFail(FoodUrlFailure.PAGE_NOT_FOUND)
                    if (!response.isSuccessful) urlFail(FoodUrlFailure.CONNECTION_FAILED)
                    val body = response.body ?: urlFail(FoodUrlFailure.NO_NUTRITION)
                    val type = body.contentType()
                    if (type == null || "${type.type}/${type.subtype}" !in setOf("text/html", "application/xhtml+xml") ||
                        response.header("Content-Encoding").let { it != null && !it.equals("identity", true) }) urlFail(FoodUrlFailure.UNSUPPORTED_PAGE)
                    if (body.contentLength() > MAX_BYTES) urlFail(FoodUrlFailure.TOO_LARGE)
                    val bytes = body.byteStream().readNBytes(MAX_BYTES + 1)
                    if (bytes.size > MAX_BYTES) urlFail(FoodUrlFailure.TOO_LARGE)
                    return FetchedFoodPage(current.toString(), bytes.toString(type.charset(Charsets.UTF_8) ?: Charsets.UTF_8))
                }
            }
            urlFail(FoodUrlFailure.CONNECTION_FAILED)
        } catch (known: FoodUrlProblem) { throw known }
        catch (_: IOException) { urlFail(FoodUrlFailure.CONNECTION_FAILED) }
        catch (_: IllegalArgumentException) { urlFail(FoodUrlFailure.UNSUPPORTED_PAGE) }
    }
    companion object { const val MAX_BYTES = 2_000_000 }
}
