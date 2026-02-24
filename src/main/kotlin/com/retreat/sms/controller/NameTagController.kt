package com.retreat.sms.controller

import com.retreat.sms.dto.NameTagMember
import com.retreat.sms.dto.NameTagRequest
import com.retreat.sms.dto.NameTagResponse
import com.retreat.sms.service.GoogleSheetService
import com.retreat.sms.service.NameTagService
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/nametag")
class NameTagController(
    private val nameTagService: NameTagService,
    private val sheetService: GoogleSheetService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 명찰 PPT 생성 + 파일 다운로드
     *
     * POST /api/nametag/generate
     *
     * Body 예시 1) 구글시트에서 전체 인원:
     * { "fromSheet": true }
     *
     * Body 예시 2) 직접 지정:
     * {
     *   "members": [
     *     {"name": "홍길동", "village": "헤세드마을"},
     *     {"name": "김철수", "village": "나래마을"}
     *   ]
     * }
     */
    @PostMapping("/generate")
    fun generate(@RequestBody request: NameTagRequest): ResponseEntity<FileSystemResource> {
        val members: List<NameTagMember>

        if (request.fromSheet) {
            val sheetMembers = sheetService.getMembersWithPhone()
            members = sheetMembers.map { NameTagMember(name = it.name, village = it.village) }
            log.info("구글시트에서 ${members.size}명 로드")
        } else {
            members = request.members ?: throw IllegalArgumentException("members 또는 fromSheet=true 필수")
        }

        require(members.isNotEmpty()) { "인원이 없습니다" }

        val outputFile = nameTagService.generateNameTags(members)

        val resource = FileSystemResource(outputFile)
        val encodedFilename = java.net.URLEncoder.encode(outputFile.name, "UTF-8")

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$encodedFilename")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation"))
            .contentLength(outputFile.length())
            .body(resource)
    }

    /**
     * 명찰 미리보기 (생성만 하고 정보 반환)
     *
     * POST /api/nametag/preview
     */
    @PostMapping("/preview")
    fun preview(@RequestBody request: NameTagRequest): NameTagResponse {
        val members: List<NameTagMember>

        if (request.fromSheet) {
            val sheetMembers = sheetService.getMembersWithPhone()
            members = sheetMembers.map { NameTagMember(name = it.name, village = it.village) }
        } else {
            members = request.members ?: throw IllegalArgumentException("members 또는 fromSheet=true 필수")
        }

        require(members.isNotEmpty()) { "인원이 없습니다" }

        val outputFile = nameTagService.generateNameTags(members)

        return NameTagResponse(
            totalCount = members.size,
            slideCount = members.size, // 1장1명 기준, 2명이면 /2
            filename = outputFile.name,
            message = "${members.size}명 명찰 생성 완료"
        )
    }
}
