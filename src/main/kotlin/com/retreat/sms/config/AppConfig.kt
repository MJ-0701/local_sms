package com.retreat.sms.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableConfigurationProperties(
    SolapiProperties::class,
    GoogleSheetsProperties::class,
    SmsProperties::class
)
class AppConfig {

    @Bean
    fun webClient(solapiProperties: SolapiProperties): WebClient =
        WebClient.builder()
            .baseUrl(solapiProperties.baseUrl)
            .build()
}
