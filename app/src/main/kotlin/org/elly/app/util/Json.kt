package org.elly.app.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object Json {
    val mapper: ObjectMapper = jacksonObjectMapper()
    fun toJson(value: Any): String = mapper.writeValueAsString(value)
}
