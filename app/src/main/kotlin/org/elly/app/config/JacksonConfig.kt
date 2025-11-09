// app/config/JacksonConfig.kt
package org.elly.app.config

import com.fasterxml.jackson.databind.DeserializationFeature
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

@Configuration
class JacksonConfig {
    @Bean
    fun jacksonCustomizer() = Jackson2ObjectMapperBuilder()
        .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}
