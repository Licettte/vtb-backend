// BankClientsConfig.kt
package org.elly.app.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@ConfigurationProperties("elly")
data class EllyBanksProps(
    var teamClientId: String = "",        // TeamID
    var teamClientSecret: String = "",
    var banks: Map<String, BankProps> = emptyMap()
)
data class BankProps(var baseUrl: String = "")


@Configuration
@EnableConfigurationProperties(EllyBanksProps::class)
class BankClientsConfig {

    @Bean
    fun webClient(builder: WebClient.Builder): WebClient = builder.build()

    @Bean
    fun webClientBuilder(): WebClient.Builder =
        WebClient.builder().exchangeStrategies(
            ExchangeStrategies.builder()
                .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
                .build()
        )
}
