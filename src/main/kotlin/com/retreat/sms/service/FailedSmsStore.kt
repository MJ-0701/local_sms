package com.retreat.sms.service

import com.retreat.sms.dto.SmsTarget
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

data class FailedSmsRecord(
    val target: SmsTarget,
    val errorMessage: String?,
    val failedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 발송 실패건을 메모리에 저장하고 재발송 시 제공
 */
@Component
class FailedSmsStore {

    private val log = LoggerFactory.getLogger(javaClass)
    private val failedRecords = mutableListOf<FailedSmsRecord>()

    fun addAll(records: List<FailedSmsRecord>) {
        failedRecords.addAll(records)
        log.info("발송 실패 ${records.size}건 저장 (총 누적: ${failedRecords.size}건)")
    }

    fun getAll(): List<FailedSmsRecord> = failedRecords.toList()

    fun getTargets(): List<SmsTarget> = failedRecords.map { it.target }

    fun count(): Int = failedRecords.size

    fun clear() {
        val count = failedRecords.size
        failedRecords.clear()
        log.info("실패 목록 초기화 ($count 건 삭제)")
    }
}
