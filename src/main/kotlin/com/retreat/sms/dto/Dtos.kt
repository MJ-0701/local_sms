package com.retreat.sms.dto

// 문자 발송 요청
data class SmsSendRequest(
    val message: String,                    // 문자 내용
    val subject: String = "[수련회비 납부안내]",  // LMS 제목
    val targets: List<SmsTarget>? = null,   // 직접 지정 시
    val fromSheet: Boolean = false,         // true면 구글시트에서 수신자 가져옴
    val filterUnpaid: Boolean = false       // true면 미납자에게만 발송
)

data class SmsTarget(
    val name: String,
    val phone: String
)

// 문자 발송 결과
data class SmsSendResponse(
    val totalTargets: Int,
    val successCount: Int,
    val failCount: Int,
    val dryRun: Boolean,
    val results: List<SmsResult>,
    val info: String? = null
)

data class SmsResult(
    val name: String,
    val phone: String,
    val status: String,     // SUCCESS, FAIL, SKIPPED
    val message: String? = null
)

// 구글시트 회비 현황
data class MemberPaymentInfo(
    val village: String,
    val name: String,
    val gender: String?,
    val paid: Boolean,
    val phone: String?
)

// 시트 조회 응답
data class SheetMembersResponse(
    val totalMembers: Int,
    val paidCount: Int,
    val unpaidCount: Int,
    val noPhoneCount: Int,
    val members: List<MemberPaymentInfo>
)
