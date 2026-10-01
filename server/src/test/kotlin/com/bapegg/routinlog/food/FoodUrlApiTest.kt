package com.bapegg.routinlog.food

import com.bapegg.routinlog.account.persistence.UserAccountEntity
import com.bapegg.routinlog.account.persistence.UserAccountRepository
import com.bapegg.routinlog.security.AuthenticatedUser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID
import kotlin.test.assertTrue

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class FoodUrlApiTest @Autowired constructor(private val mvc: MockMvc, private val accounts: UserAccountRepository, private val meals: MealService) {
    @MockitoBean lateinit var fetcher: FoodPageFetcher
    private val owners = mutableListOf<UUID>()
    private fun owner() = accounts.saveAndFlush(UserAccountEntity()).id.also { owners += it }
    private fun auth(id: UUID) = authentication(UsernamePasswordAuthenticationToken(AuthenticatedUser(id), null, emptyList()))
    private val body = """{"url":"https://example.com/product"}"""
    private val page = FetchedFoodPage("https://example.com/product", """<title>테스트 식품</title><table><caption>80g당</caption><tr><th>열량</th><td>160 kcal</td></tr><tr><th>지방</th><td>0 g</td></tr></table>""")
    @AfterEach fun cleanup() { owners.forEach { if (accounts.existsById(it)) accounts.deleteById(it) } }

    @Test fun `URL requests require auth and preview neither saves food nor bypasses consent`() {
        mvc.perform(post("/api/v1/food-url/preview").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        verifyNoInteractions(fetcher)
        doReturn(page).`when`(fetcher).fetch(anyString())
        val id = owner()
        mvc.perform(post("/api/v1/food-url/preview").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.draft.basisGrams").value(80)).andExpect(jsonPath("$.draft.nutrition.fatG").value(0))
            .andExpect(jsonPath("$.draft.nutrition.fiberG").isEmpty)
        assertTrue(meals.foods(id).items.isEmpty())
        mvc.perform(put("/api/v1/foods/${UUID.randomUUID()}").with(auth(id)).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"테스트","basisGrams":80,"nutrition":{"kcal":160},"preparation":"UNKNOWN"}"""))
            .andExpect(status().isForbidden)
    }
    @Test fun `blocked retailer returns an explicit reason with no draft`() {
        doThrow(FoodUrlProblem(FoodUrlFailure.ACCESS_BLOCKED)).`when`(fetcher).fetch(anyString())
        mvc.perform(post("/api/v1/food-url/preview").with(auth(owner())).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(jsonPath("$.reasonCode").value("ACCESS_BLOCKED")).andExpect(jsonPath("$.draft").isEmpty)
    }
    @Test fun `deleted account cannot receive a late preview and private URL does not trigger fetching`() {
        val id = owner()
        mvc.perform(post("/api/v1/food-url/preview").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content("""{"url":"https://127.0.0.1/"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.reasonCode").value("UNSAFE_ADDRESS"))
        verifyNoInteractions(fetcher)
        doAnswer { accounts.deleteById(id); page }.`when`(fetcher).fetch(anyString())
        mvc.perform(post("/api/v1/food-url/preview").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
    }
}
