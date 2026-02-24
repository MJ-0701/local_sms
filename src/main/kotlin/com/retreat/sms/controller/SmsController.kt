package com.retreat.sms.controller

import com.retreat.sms.dto.*
import com.retreat.sms.service.GoogleSheetService
import com.retreat.sms.service.SolapiService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/sms")
class SmsController(
    private val solapiService: SolapiService,
    private val sheetService: GoogleSheetService
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
     * 문자 발송
     * POST /api/sms/send
     *
     * Body 예시 1) 구글시트 전체 발송:
     * {
     *   "message": "잇는공동체 수련회 회비 안내입니다.\n계좌: ...",
     *   "fromSheet": true
     * }
     *
     * Body 예시 2) 미납자에게만:
     * {
     *   "message": "회비 미납 안내...",
     *   "fromSheet": true,
     *   "filterUnpaid": true
     * }
     *
     * Body 예시 3) 직접 지정:
     * {
     *   "message": "테스트 문자입니다",
     *   "targets": [
     *     {"name": "홍길동", "phone": "01012345678"}
     *   ]
     * }
     */
    @PostMapping("/send")
    fun sendSms(@RequestBody request: SmsSendRequest): SmsSendResponse {
        val targets: List<SmsTarget>

        if (request.fromSheet) {
            // 구글시트에서 수신자 가져오기
            val members = sheetService.getMembersWithPhone()

            val filtered = if (request.filterUnpaid) {
                members.filter { !it.paid }
            } else {
                members
            }

            // 연락처 없는 사람 제외
            val withPhone = filtered.filter { !it.phone.isNullOrBlank() }
            val noPhone = filtered.filter { it.phone.isNullOrBlank() }

            if (noPhone.isNotEmpty()) {
                log.warn("연락처 없는 인원 ${noPhone.size}명 제외: ${noPhone.map { "${it.name}(${it.village})" }}")
            }

            targets = withPhone.map { SmsTarget(it.name, it.phone!!) }
        } else {
            targets = request.targets ?: emptyList()
        }

        if (targets.isEmpty()) {
            return SmsSendResponse(0, 0, 0, true, emptyList())
        }

        // 연락처 정규화 (하이픈 제거)
        val normalizedTargets = targets.map {
            it.copy(phone = it.phone.replace(Regex("[^0-9]"), ""))
        }

        // 중복 제거 (같은 번호에 여러 번 보내지 않도록)
        val uniqueTargets = normalizedTargets.distinctBy { it.phone }
        if (uniqueTargets.size < normalizedTargets.size) {
            log.info("중복 번호 ${normalizedTargets.size - uniqueTargets.size}건 제거")
        }

        log.info("═══ 문자 발송 시작 ═══")
        log.info("수신자: ${uniqueTargets.size}명")
        log.info("메시지 (${request.message.toByteArray(Charsets.UTF_8).size} bytes):")
        log.info(request.message)
        log.info("═══════════════════")

        val results = solapiService.sendBulkLms(uniqueTargets, request.message)

        val successCount = results.count { it.status == "SUCCESS" || it.status == "DRY_RUN" }
        val failCount = results.count { it.status == "FAIL" }

        return SmsSendResponse(
            totalTargets = uniqueTargets.size,
            successCount = successCount,
            failCount = failCount,
            dryRun = results.any { it.status == "DRY_RUN" },
            results = results
        )
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

        val targets = listOf(SmsTarget("테스트", phone))
        val results = solapiService.sendBulkLms(targets, message)

        return SmsSendResponse(
            totalTargets = 1,
            successCount = results.count { it.status != "FAIL" },
            failCount = results.count { it.status == "FAIL" },
            dryRun = results.any { it.status == "DRY_RUN" },
            results = results
        )
    }
}
