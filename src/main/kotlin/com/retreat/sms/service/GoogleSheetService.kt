package com.retreat.sms.service

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.retreat.sms.config.GoogleSheetsProperties
import com.retreat.sms.dto.MemberPaymentInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.FileInputStream

@Service
class GoogleSheetService(
    private val props: GoogleSheetsProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun getSheetsService(): Sheets {
        val credentials = GoogleCredentials
            .fromStream(FileInputStream(props.credentialsPath))
            .createScoped(listOf(SheetsScopes.SPREADSHEETS_READONLY))

        return Sheets.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials)
        ).setApplicationName("retreat-sms").build()
    }

    /**
     * 회비납부자 시트 읽기
     * 3행에 마을명 헤더, 4행부터 이름/성별/납부여부가 3열씩 반복
     */
    fun getMembers(): List<MemberPaymentInfo> {
        val service = getSheetsService()
        val range = "${props.sheetName}!A1:AZ100"

        val response = service.spreadsheets().values()
            .get(props.spreadsheetId, range)
            .execute()

        val rows = response.getValues() ?: return emptyList()
        if (rows.size < 4) return emptyList()

        // 3행(index 2)에서 마을명 헤더 위치 자동 감지
        val headerRow = rows[2]
        data class VillageColumn(val name: String, val nameCol: Int, val genderCol: Int, val paidCol: Int)

        val villageColumns = mutableListOf<VillageColumn>()
        for (col in headerRow.indices) {
            val value = headerRow[col]?.toString()?.trim() ?: ""
            if (value.contains("마을")) {
                villageColumns.add(VillageColumn(value, col, col + 1, col + 2))
            }
        }

        if (villageColumns.isEmpty()) {
            log.warn("마을 헤더를 찾을 수 없습니다")
            return emptyList()
        }

        log.info("감지된 마을: ${villageColumns.map { it.name }}")

        // 4행(index 3)부터 인원 읽기
        val members = mutableListOf<MemberPaymentInfo>()

        for (rowIdx in 3 until rows.size) {
            val row = rows[rowIdx]

            for (vc in villageColumns) {
                val name = row.getOrNull(vc.nameCol)?.toString()?.trim() ?: ""
                if (name.isBlank()) continue

                val gender = row.getOrNull(vc.genderCol)?.toString()?.trim()
                val paidRaw = row.getOrNull(vc.paidCol)?.toString()?.trim() ?: ""
                val paid = paidRaw.equals("TRUE", ignoreCase = true)
                    || paidRaw.equals("O", ignoreCase = true)
                    || paidRaw == "○"
                    || paidRaw == "✓"

                // 연락처는 회비납부자 시트에 없을 수 있음 → 별도 매핑 필요
                members.add(
                    MemberPaymentInfo(
                        village = vc.name,
                        name = name,
                        gender = gender,
                        paid = paid,
                        phone = null // 연락처는 인원DB에서 매핑
                    )
                )
            }
        }

        log.info("총 ${members.size}명 로드 (납부: ${members.count { it.paid }}, 미납: ${members.count { !it.paid }})")
        return members
    }

    /**
     * 인원DB 시트에서 이름→연락처 매핑 가져오기
     */
    fun getPhoneMap(): Map<String, String> {
        val service = getSheetsService()
        // 인원DB: F열=이름, H열=연락처
        val range = "스프레드시트(신청서) 자동연결!A1:H300"

        val response = service.spreadsheets().values()
            .get(props.spreadsheetId, range)
            .execute()

        val rows = response.getValues() ?: return emptyMap()
        val phoneMap = mutableMapOf<String, String>()

        for (i in 1 until rows.size) {
            val row = rows[i]
            val name = row.getOrNull(5)?.toString()?.trim() ?: continue  // F열 (index 5)
            val phone = row.getOrNull(7)?.toString()?.trim() ?: continue  // H열 (index 7)
            if (name.isNotBlank() && phone.isNotBlank()) {
                val cleanPhone = phone.replace(Regex("[^0-9]"), "")
                phoneMap[name] = cleanPhone
            }
        }

        log.info("연락처 매핑 ${phoneMap.size}건 로드")
        return phoneMap
    }

    /**
     * 회비납부자 + 연락처 매핑 통합
     */
    fun getMembersWithPhone(): List<MemberPaymentInfo> {
        val members = getMembers()
        val phoneMap = getPhoneMap()

        return members.map { m ->
            m.copy(phone = phoneMap[m.name])
        }
    }
}
