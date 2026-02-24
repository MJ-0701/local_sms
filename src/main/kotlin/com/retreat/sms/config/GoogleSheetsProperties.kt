package com.retreat.sms.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "google.sheets")
data class GoogleSheetsProperties(
    val spreadsheetId: String,
    val credentialsPath: String,
    val sheetName: String
)
