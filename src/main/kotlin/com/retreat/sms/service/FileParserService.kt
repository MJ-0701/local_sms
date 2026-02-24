package com.retreat.sms.service

import com.retreat.sms.dto.SmsTarget
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.InputStreamReader

@Service
class FileParserService {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 업로드된 파일에서 SmsTarget 목록 추출
     * 지원 형식: .xlsx, .xls, .csv
     * 컬럼: 이름 | 연락처
     */
    fun parse(file: MultipartFile): List<SmsTarget> {
        val filename = file.originalFilename ?: throw IllegalArgumentException("파일명이 없습니다")

        return when {
            filename.endsWith(".xlsx") || filename.endsWith(".xls") -> parseExcel(file)
            filename.endsWith(".csv") -> parseCsv(file)
            else -> throw IllegalArgumentException("지원하지 않는 파일 형식입니다: $filename (.xlsx, .xls, .csv만 가능)")
        }
    }

    private fun parseExcel(file: MultipartFile): List<SmsTarget> {
        val targets = mutableListOf<SmsTarget>()

        WorkbookFactory.create(file.inputStream).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            for (rowIdx in 1..sheet.lastRowNum) { // 0번 행은 헤더로 건너뜀
                val row = sheet.getRow(rowIdx) ?: continue

                val name = row.getCell(0)?.let { cell ->
                    when (cell.cellType) {
                        CellType.STRING -> cell.stringCellValue.trim()
                        CellType.NUMERIC -> cell.numericCellValue.toLong().toString()
                        else -> null
                    }
                }

                val phone = row.getCell(1)?.let { cell ->
                    when (cell.cellType) {
                        CellType.STRING -> cell.stringCellValue.trim()
                        CellType.NUMERIC -> cell.numericCellValue.toLong().toString()
                        else -> null
                    }
                }

                if (!name.isNullOrBlank() && !phone.isNullOrBlank()) {
                    val normalized = phone.replace(Regex("[^0-9]"), "")
                    targets.add(SmsTarget(name, normalized))
                } else {
                    log.warn("행 ${rowIdx + 1} 건너뜀: 이름='$name', 연락처='$phone'")
                }
            }
        }

        log.info("Excel 파일에서 ${targets.size}건 파싱 완료")
        return targets
    }

    private fun parseCsv(file: MultipartFile): List<SmsTarget> {
        val targets = mutableListOf<SmsTarget>()

        BufferedReader(InputStreamReader(file.inputStream, Charsets.UTF_8)).use { reader ->
            reader.readLine() // 헤더 건너뜀

            var lineNum = 2
            reader.forEachLine { line ->
                val parts = line.split(",").map { it.trim() }
                if (parts.size >= 2) {
                    val name = parts[0]
                    val phone = parts[1].replace(Regex("[^0-9]"), "")

                    if (name.isNotBlank() && phone.isNotBlank()) {
                        targets.add(SmsTarget(name, phone))
                    } else {
                        log.warn("행 $lineNum 건너뜀: 이름='$name', 연락처='${parts[1]}'")
                    }
                }
                lineNum++
            }
        }

        log.info("CSV 파일에서 ${targets.size}건 파싱 완료")
        return targets
    }
}
