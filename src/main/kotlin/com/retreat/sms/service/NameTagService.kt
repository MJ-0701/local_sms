package com.retreat.sms.service

import com.retreat.sms.dto.NameTagMember
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Service
class NameTagService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${nametag.template-path:templates/nametag_template.pptx}")
    private lateinit var templatePath: String

    @Value("\${nametag.output-dir:output}")
    private lateinit var outputDir: String

    // 플레이스홀더 키
    companion object {
        const val PH_NAME = "{{이름}}"
        const val PH_VILLAGE = "{{마을}}"
        // 1장에 2명일 경우 두 번째 슬롯
        const val PH_NAME2 = "{{이름2}}"
        const val PH_VILLAGE2 = "{{마을2}}"
    }

    /**
     * 템플릿 슬라이드의 플레이스홀더를 분석하여 1장당 몇 명인지 자동 감지
     */
    private fun detectSlotsPerSlide(ppt: XMLSlideShow): Int {
        if (ppt.slides.isEmpty()) return 1
        val templateSlide = ppt.slides[0]
        var hasSlot2 = false

        for (shape in templateSlide.shapes) {
            if (shape is XSLFTextShape) {
                val text = shape.text
                if (text.contains(PH_NAME2) || text.contains(PH_VILLAGE2)) {
                    hasSlot2 = true
                    break
                }
            }
        }
        return if (hasSlot2) 2 else 1
    }

    /**
     * 슬라이드 내 모든 텍스트에서 플레이스홀더를 치환
     */
    private fun replacePlaceholders(slide: XSLFSlide, replacements: Map<String, String>) {
        for (shape in slide.shapes) {
            if (shape is XSLFTextShape) {
                for (paragraph in shape.textBody.paragraphs) {
                    // Run 단위로 텍스트가 쪼개질 수 있으므로, 전체 paragraph 텍스트를 합쳐서 치환
                    val fullText = paragraph.runs.joinToString("") { it.rawText ?: "" }

                    var needsReplace = false
                    for ((placeholder, _) in replacements) {
                        if (fullText.contains(placeholder)) {
                            needsReplace = true
                            break
                        }
                    }

                    if (needsReplace) {
                        // 치환된 텍스트 생성
                        var replacedText = fullText
                        for ((placeholder, value) in replacements) {
                            replacedText = replacedText.replace(placeholder, value)
                        }

                        // 첫 번째 run에 전체 텍스트 세팅, 나머지 run은 비우기
                        // (서식은 첫 번째 run의 서식 유지)
                        if (paragraph.runs.isNotEmpty()) {
                            val runs = paragraph.runs
                            runs[0].setText(replacedText)
                            for (i in 1 until runs.size) {
                                runs[i].setText("")
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 슬라이드를 복제 (레이아웃 기반으로 새 슬라이드 추가 후 내용 복사)
     */
    private fun duplicateSlide(ppt: XMLSlideShow, sourceSlide: XSLFSlide): XSLFSlide {
        val layout = sourceSlide.slideLayout
        val newSlide = ppt.createSlide(layout)

        // 소스 슬라이드의 모든 shape을 XML 레벨에서 복사
        try {
            val srcXml = sourceSlide.xmlObject
            val destXml = newSlide.xmlObject

            // 기존 shape 제거
            while (destXml.cSld.spTree.sizeOfSpArray() > 0) {
                destXml.cSld.spTree.removeSpAt(0)
            }
            while (destXml.cSld.spTree.sizeOfGrpSpArray() > 0) {
                destXml.cSld.spTree.removeGrpSpAt(0)
            }
            while (destXml.cSld.spTree.sizeOfPicArray() > 0) {
                destXml.cSld.spTree.removePicAt(0)
            }

            // 소스의 shape들을 복사
            val importedTree = srcXml.cSld.spTree.copy()
            val spList = importedTree.spList
            for (sp in spList) {
                destXml.cSld.spTree.addNewSp().set(sp.copy())
            }
            val grpSpList = importedTree.grpSpList
            for (grpSp in grpSpList) {
                destXml.cSld.spTree.addNewGrpSp().set(grpSp.copy())
            }
            val picList = importedTree.picList
            for (pic in picList) {
                destXml.cSld.spTree.addNewPic().set(pic.copy())
            }

        } catch (e: Exception) {
            log.warn("XML 복제 실패, 기본 레이아웃 슬라이드 사용: ${e.message}")
        }

        return newSlide
    }

    /**
     * 명찰 PPT 생성
     * 
     * @param members 명찰에 넣을 인원 목록
     * @return 생성된 파일 경로
     */
    fun generateNameTags(members: List<NameTagMember>): File {
        val templateFile = File(templatePath)
        require(templateFile.exists()) { "템플릿 파일이 없습니다: $templatePath" }

        val ppt = XMLSlideShow(FileInputStream(templateFile))
        require(ppt.slides.isNotEmpty()) { "템플릿에 슬라이드가 없습니다" }

        val slotsPerSlide = detectSlotsPerSlide(ppt)
        log.info("템플릿 감지: 1장당 ${slotsPerSlide}명")

        // 템플릿 슬라이드(첫 번째)를 기준으로 복제
        val templateSlide = ppt.slides[0]

        if (slotsPerSlide == 1) {
            // 1장 1명: 첫 슬라이드는 첫 번째 인원, 이후 복제
            replacePlaceholders(templateSlide, mapOf(
                PH_NAME to members[0].name,
                PH_VILLAGE to members[0].village
            ))

            for (i in 1 until members.size) {
                val newSlide = duplicateSlide(ppt, templateSlide)
                replacePlaceholders(newSlide, mapOf(
                    PH_NAME to members[i].name,
                    PH_VILLAGE to members[i].village
                ))
            }
        } else {
            // 1장 2명: 2명씩 묶어서 처리
            val chunks = members.chunked(2)

            // 첫 슬라이드 처리
            val firstChunk = chunks[0]
            val firstReplacements = mutableMapOf(
                PH_NAME to firstChunk[0].name,
                PH_VILLAGE to firstChunk[0].village
            )
            if (firstChunk.size > 1) {
                firstReplacements[PH_NAME2] = firstChunk[1].name
                firstReplacements[PH_VILLAGE2] = firstChunk[1].village
            } else {
                // 홀수 인원: 두 번째 슬롯 비움
                firstReplacements[PH_NAME2] = ""
                firstReplacements[PH_VILLAGE2] = ""
            }
            replacePlaceholders(templateSlide, firstReplacements)

            // 나머지 슬라이드
            for (i in 1 until chunks.size) {
                val chunk = chunks[i]
                val newSlide = duplicateSlide(ppt, templateSlide)

                val replacements = mutableMapOf(
                    PH_NAME to chunk[0].name,
                    PH_VILLAGE to chunk[0].village
                )
                if (chunk.size > 1) {
                    replacements[PH_NAME2] = chunk[1].name
                    replacements[PH_VILLAGE2] = chunk[1].village
                } else {
                    replacements[PH_NAME2] = ""
                    replacements[PH_VILLAGE2] = ""
                }
                replacePlaceholders(newSlide, replacements)
            }
        }

        // 출력
        val outDirFile = File(outputDir)
        if (!outDirFile.exists()) outDirFile.mkdirs()

        val outputFile = File(outDirFile, "명찰_${System.currentTimeMillis()}.pptx")
        FileOutputStream(outputFile).use { ppt.write(it) }
        ppt.close()

        log.info("명찰 생성 완료: ${outputFile.absolutePath} (${members.size}명, ${ppt.slides.size}장)")
        return outputFile
    }
}
