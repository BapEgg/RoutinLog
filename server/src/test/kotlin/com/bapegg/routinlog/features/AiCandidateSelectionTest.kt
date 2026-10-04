package com.bapegg.routinlog.features

import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.*

class AiCandidateSelectionTest {
    private val json=jacksonObjectMapper()
    private fun response(text:String,status:String="completed",type:String="output_text")=json.writeValueAsString(mapOf("status" to status,"output" to listOf(mapOf("content" to listOf(mapOf("type" to type,"text" to text))))))
    @Test fun `only server approved candidate IDs survive provider output`() {
        val allowed=listOf("observe","meals")
        assertEquals("meals",AiCandidateSelection.parse(json,response("""{"selectedId":"meals"}"""),allowed))
        assertNull(AiCandidateSelection.parse(json,response("""{"selectedId":"invented-prescription"}"""),allowed))
        assertNull(AiCandidateSelection.parse(json,response("""{"selectedId":"meals","calories":10000}"""),allowed))
        assertNull(AiCandidateSelection.parse(json,response("""{"selectedId":"meals"}""","incomplete"),allowed))
        assertNull(AiCandidateSelection.parse(json,response("""{"selectedId":"meals"}""",type="refusal"),allowed))
    }
}
