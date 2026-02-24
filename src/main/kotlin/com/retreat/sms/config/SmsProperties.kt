package com.retreat.sms.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sms")
data class SmsProperties(
    val dryRun: Boolean = true,
    val delayMs: Long = 100,
    val batchSize: Int = 1000
)
