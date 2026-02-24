# retreat-sms

## Project Overview
Spring Boot 3 + Kotlin + Solapi API 기반 수련회 대량 문자 발송 및 명찰 PPT 생성 서비스

## Tech Stack
- Kotlin 2.1.0, Java 21
- Spring Boot 3.4.2
- Google Sheets API (서비스 계정 인증, credentials.json)
- Solapi SMS API
- Apache POI (PPTX 명찰 생성)

## Project Structure
```
src/main/kotlin/com/retreat/sms/
  config/         - AppConfig, GoogleSheetsProperties, SmsProperties, SolapiProperties
  controller/     - SmsController, NameTagController
  dto/            - Dtos, NameTagDtos
  service/        - GoogleSheetService, SolapiService, NameTagService
src/main/resources/
  application.yml
templates/        - nametag_template.pptx
```

## Key APIs
- `GET  /api/sms/members` - 회비 현황 조회 (?filter=unpaid)
- `POST /api/sms/test` - 테스트 발송 (본인에게만)
- `POST /api/sms/send` - 구글시트/직접지정 문자 발송
- `POST /api/nametag/generate` - 명찰 PPT 생성
- `POST /api/nametag/preview` - 명찰 미리보기

## Build & Run
```bash
./gradlew bootRun
```
Server runs on port 8080.

## Notes
- `sms.dry-run: true` 로 테스트 먼저 수행
- application.yml 에서 solapi, google sheets 설정 필요
