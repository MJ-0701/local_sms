package com.retreat.sms.service

import com.retreat.sms.config.SolapiProperties
import com.retreat.sms.config.SmsProperties
import com.retreat.sms.dto.SmsResult
import com.retreat.sms.dto.SmsTarget
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class SolapiService(
    private val webClient: WebClient,
    private val solapiProps: SolapiProperties,
    private val smsProps: SmsProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 솔라피 HMAC-SHA256 인증 헤더 생성
     */
    private fun generateAuthHeader(): String {
        val date = Instant.now().toString()
        val salt = UUID.randomUUID().toString()
        val data = "$date$salt"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(solapiProps.apiSecret.toByteArray(), "HmacSHA256"))
        val signature = mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }

        return "HMAC-SHA256 apiKey=${solapiProps.apiKey}, date=$date, salt=$salt, signature=$signature"
    }

    /**
     * LMS 대량 발송 (솔라피 /messages/v4/send-many)
     */
    fun sendBulkLms(targets: List<SmsTarget>, message: String, subject: String = "[수련회비 납부안내]"): List<SmsResult> {
        val results = mutableListOf<SmsResult>()

        // 솔라피는 한 번에 최대 10,000건, 배치로 나눔
        val batches = targets.chunked(smsProps.batchSize)

        for ((batchIdx, batch) in batches.withIndex()) {
            log.info("배치 ${batchIdx + 1}/${batches.size} - ${batch.size}건 발송 시작")

            if (smsProps.dryRun) {
                // 드라이런: 실제 발송 안함
                batch.forEach { target ->
                    log.info("[DRY-RUN] → ${target.name} (${target.phone})")
                    results.add(SmsResult(target.name, target.phone, "DRY_RUN", "테스트 모드"))
                }
                continue
            }

            // 솔라피 send-many 요청 바디
            val messages = batch.map { target ->
                mapOf(
                    "to" to target.phone,
                    "from" to solapiProps.sender,
                    "text" to message,
                    "type" to "LMS",
                    "subject" to subject
                )
            }

            try {
                val response = webClient.post()
                    .uri("/messages/v4/send-many")
                    .header("Authorization", generateAuthHeader())
                    .header("Content-Type", "application/json")
                    .bodyValue(mapOf("messages" to messages))
                    .retrieve()
                    .bodyToMono(Map::class.java)
                    .block()

                val groupInfo = response?.get("groupInfo") as? Map<*, *>
                val count = groupInfo?.get("count") as? Map<*, *>

                log.info("배치 ${batchIdx + 1} 결과: $count")

                batch.forEach { target ->
                    results.add(SmsResult(target.name, target.phone, "SUCCESS"))
                }

            } catch (e: Exception) {
                log.error("배치 ${batchIdx + 1} 발송 실패: ${e.message}")
                batch.forEach { target ->
                    results.add(SmsResult(target.name, target.phone, "FAIL", e.message))
                }
            }

            // 배치 간 딜레이
            if (batchIdx < batches.size - 1) {
                Thread.sleep(smsProps.delayMs)
            }
        }

        return results
    }
}
