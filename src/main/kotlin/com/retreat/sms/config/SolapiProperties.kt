package com.retreat.sms.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "solapi")
data class SolapiProperties(
    val apiKey: String,
    val apiSecret: String,
    val sender: String,
    val baseUrl: String
)
