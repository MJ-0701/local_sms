package com.retreat.sms.controller

import com.retreat.sms.dto.*
import com.retreat.sms.service.*
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/sms")
class SmsController(
    private val solapiService: SolapiService,
    private val sheetService: GoogleSheetService,
    private val fileParserService: FileParserService,
    private val failedSmsStore: FailedSmsStore
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 구글시트 회비 현황 조회
     * GET /api/sms/members
     * GET /api/sms/members?filter=unpaid  (미납자만)
     * GET /api/sms/members?filter=paid    (납부자만)
     */
    @GetMapping("/members")
    fun getMembers(@RequestParam filter: String? = null): SheetMembersResponse {
        val members = sheetService.getMembersWithPhone()

        val filtered = when (filter) {
            "unpaid" -> members.filter { !it.paid }
            "paid" -> members.filter { it.paid }
            else -> members
        }

        return SheetMembersResponse(
            totalMembers = members.size,
            paidCount = members.count { it.paid },
            unpaidCount = members.count { !it.paid },
            noPhoneCount = filtered.count { it.phone.isNullOrBlank() },
            members = filtered
        )
    }

    /**
     * 문자 발송 (JSON 바디)
     * POST /api/sms/send
     */
    @PostMapping("/send")
    fun sendSms(@RequestBody request: SmsSendRequest): SmsSendResponse {
        val targets: List<SmsTarget>

        if (request.fromSheet) {
            val members = sheetService.getMembersWithPhone()

            val filtered = if (request.filterUnpaid) {
                members.filter { !it.paid }
            } else {
                members
            }

            val withPhone = filtered.filter { !it.phone.isNullOrBlank() }
            val noPhone = filtered.filter { it.phone.isNullOrBlank() }

            if (noPhone.isNotEmpty()) {
                log.warn("연락처 없는 인원 ${noPhone.size}명 제외: ${noPhone.map { "${it.name}(${it.village})" }}")
            }

            targets = withPhone.map { SmsTarget(it.name, it.phone!!) }
        } else {
            targets = request.targets ?: emptyList()
        }

        return doSend(targets, request.message, request.subject)
    }

    /**
     * 파일 업로드로 문자 발송
     * POST /api/sms/send-file
     *
     * Form-data:
     *   file: .xlsx / .xls / .csv (컬럼: 이름 | 연락처)
     *   message: 문자 내용
     *   subject: LMS 제목 (선택, 기본값: [수련회비 납부안내])
     */
    @PostMapping("/send-file")
    fun sendSmsFromFile(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("message") message: String,
        @RequestParam("subject", required = false, defaultValue = "[수련회비 납부안내]") subject: String
    ): SmsSendResponse {
        log.info("파일 업로드 발송: ${file.originalFilename} (${file.size} bytes)")
        val targets = fileParserService.parse(file)
        return doSend(targets, message, subject)
    }

    /**
     * 발송 실패건 조회
     * GET /api/sms/failed
     */
    @GetMapping("/failed")
    fun getFailedList(): Map<String, Any> {
        val records = failedSmsStore.getAll()
        return mapOf(
            "totalFailed" to records.size,
            "records" to records.map {
                mapOf(
                    "name" to it.target.name,
                    "phone" to it.target.phone,
                    "error" to (it.errorMessage ?: "알 수 없음"),
                    "failedAt" to it.failedAt.toString()
                )
            }
        )
    }

    /**
     * 실패건 재발송
     * POST /api/sms/retry
     * { "message": "재발송 메시지", "subject": "LMS 제목(선택)" }
     */
    @PostMapping("/retry")
    fun retrySend(@RequestBody request: Map<String, String>): SmsSendResponse {
        val targets = failedSmsStore.getTargets()
        if (targets.isEmpty()) {
            return SmsSendResponse(0, 0, 0, false, emptyList(), "재발송할 실패건이 없습니다")
        }

        val message = request["message"] ?: throw IllegalArgumentException("message 필수")
        val subject = request["subject"] ?: "[수련회비 납부안내]"

        log.info("═══ 실패건 재발송 시작 (${targets.size}건) ═══")

        // 재발송 전에 기존 실패 목록 초기화
        failedSmsStore.clear()

        return doSend(targets, message, subject)
    }

    /**
     * 실패건 목록 초기화
     * DELETE /api/sms/failed
     */
    @DeleteMapping("/failed")
    fun clearFailed(): Map<String, String> {
        failedSmsStore.clear()
        return mapOf("status" to "OK", "message" to "실패 목록이 초기화되었습니다")
    }

    /**
     * 테스트 발송 (본인에게만)
     * POST /api/sms/test
     * { "message": "테스트입니다", "phone": "01012345678" }
     */
    @PostMapping("/test")
    fun testSms(@RequestBody request: Map<String, String>): SmsSendResponse {
        val message = request["message"] ?: "테스트 문자입니다"
        val phone = request["phone"] ?: throw IllegalArgumentException("phone 필수")
        val subject = request["subject"] ?: "[수련회비 납부안내]"

        val targets = listOf(SmsTarget("테스트", phone))
        val results = solapiService.sendBulkLms(targets, message, subject)

        return SmsSendResponse(
            totalTargets = 1,
            successCount = results.count { it.status != "FAIL" },
            failCount = results.count { it.status == "FAIL" },
            dryRun = results.any { it.status == "DRY_RUN" },
            results = results
        )
    }

    // ── 공통 발송 로직 ──

    private fun doSend(targets: List<SmsTarget>, message: String, subject: String): SmsSendResponse {
        if (targets.isEmpty()) {
            return SmsSendResponse(0, 0, 0, true, emptyList(), "발송 대상이 없습니다")
        }

        // 연락처 정규화 (하이픈 제거)
        val normalizedTargets = targets.map {
            it.copy(phone = it.phone.replace(Regex("[^0-9]"), ""))
        }

        // 중복 제거
        val uniqueTargets = normalizedTargets.distinctBy { it.phone }
        if (uniqueTargets.size < normalizedTargets.size) {
            log.info("중복 번호 ${normalizedTargets.size - uniqueTargets.size}건 제거")
        }

        log.info("═══ 문자 발송 시작 ═══")
        log.info("수신자: ${uniqueTargets.size}명")
        log.info("제목: $subject")
        log.info("메시지 (${message.toByteArray(Charsets.UTF_8).size} bytes):")
        log.info(message)
        log.info("═══════════════════")

        val results = solapiService.sendBulkLms(uniqueTargets, message, subject)

        val successCount = results.count { it.status == "SUCCESS" || it.status == "DRY_RUN" }
        val failCount = results.count { it.status == "FAIL" }

        // 실패건 저장
        val failedResults = results.filter { it.status == "FAIL" }
        if (failedResults.isNotEmpty()) {
            val failedRecords = failedResults.map { result ->
                FailedSmsRecord(
                    target = SmsTarget(result.name, result.phone),
                    errorMessage = result.message
                )
            }
            failedSmsStore.addAll(failedRecords)
            log.warn("발송 실패 ${failedResults.size}건이 저장되었습니다. GET /api/sms/failed 로 확인 가능")
        }

        return SmsSendResponse(
            totalTargets = uniqueTargets.size,
            successCount = successCount,
            failCount = failCount,
            dryRun = results.any { it.status == "DRY_RUN" },
            results = results
        )
    }
}
