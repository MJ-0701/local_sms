package com.retreat.sms.dto

// 명찰 생성 요청
data class NameTagRequest(
    val members: List<NameTagMember>? = null,  // 직접 지정
    val fromSheet: Boolean = false              // 구글시트에서 가져오기
)

data class NameTagMember(
    val name: String,
    val village: String
)

// 명찰 생성 결과
data class NameTagResponse(
    val totalCount: Int,
    val slideCount: Int,
    val filename: String,
    val message: String
)
