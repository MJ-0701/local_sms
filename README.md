# 🏕️ 수련회 대량 문자 발송 서비스

Spring Boot 3 + Kotlin + 솔라피(Solapi) API

## 🔧 세팅 순서

### 1. 솔라피 설정
1. [솔라피 콘솔](https://console.solapi.com) 가입
2. **발신번호 등록** (본인 명의 휴대폰 → 인증 문자 받기)
3. **API Key / Secret** 확인: 콘솔 → 개발/연동 → API Key 관리

### 2. 구글 서비스 계정 설정
1. [Google Cloud Console](https://console.cloud.google.com) → 프로젝트 생성
2. Google Sheets API 활성화
3. 서비스 계정 생성 → JSON 키 다운로드 → 프로젝트 루트에 `credentials.json`으로 저장
4. 서비스 계정 이메일을 **구글시트에 뷰어로 공유**

### 3. application.yml 수정
```yaml
solapi:
  api-key: "실제_API_KEY"
  api-secret: "실제_API_SECRET"
  sender: "등록된_발신번호"

google:
  sheets:
    spreadsheet-id: "구글시트_ID"    # URL에서 /d/ 뒤의 긴 문자열
    credentials-path: "credentials.json"

sms:
  dry-run: true   # 처음엔 true로 테스트!
```

### 4. 실행
```bash
./gradlew bootRun
```

## 📡 Postman API

### 회비 현황 조회
```
GET http://localhost:8080/api/sms/members
GET http://localhost:8080/api/sms/members?filter=unpaid
```

### 테스트 발송 (본인에게만)
```
POST http://localhost:8080/api/sms/test
Content-Type: application/json

{
  "message": "테스트 문자입니다",
  "phone": "01012345678"
}
```

### 구글시트 전체 발송
```
POST http://localhost:8080/api/sms/send
Content-Type: application/json

{
  "message": "[잇는공동체 수련회]\n\n회비 안내드립니다.\n\n금액: 50,000원\n계좌: 카카오뱅크 3333-00-1234567 (홍길동)\n\n감사합니다 🙏",
  "fromSheet": true
}
```

### 미납자에게만 발송
```
POST http://localhost:8080/api/sms/send
Content-Type: application/json

{
  "message": "[잇는공동체 수련회]\n\n아직 회비 입금이 확인되지 않았습니다.\n\n금액: 50,000원\n계좌: 카카오뱅크 3333-00-1234567 (홍길동)\n입금 시 이름 기재 부탁드립니다.\n\n감사합니다 🙏",
  "fromSheet": true,
  "filterUnpaid": true
}
```

### 직접 지정 발송
```
POST http://localhost:8080/api/sms/send
Content-Type: application/json

{
  "message": "개별 안내 문자입니다",
  "targets": [
    {"name": "홍길동", "phone": "010-1234-5678"},
    {"name": "김철수", "phone": "01098765432"}
  ]
}
```

## 🏷️ 명찰 PPT 생성

### 템플릿 준비
1. PPT 파일에 텍스트 박스로 플레이스홀더 입력:
   - 1장 1명: `{{마을}}`, `{{이름}}`
   - 1장 2명: `{{마을}}`, `{{이름}}`, `{{마을2}}`, `{{이름2}}`
2. `templates/nametag_template.pptx`로 저장

### 명찰 생성 (구글시트 전체)
```
POST http://localhost:8080/api/nametag/generate
Content-Type: application/json

{ "fromSheet": true }
```
→ pptx 파일 다운로드됨

### 명찰 생성 (직접 지정)
```
POST http://localhost:8080/api/nametag/generate
Content-Type: application/json

{
  "members": [
    {"name": "홍길동", "village": "헤세드마을"},
    {"name": "김철수", "village": "나래마을"}
  ]
}
```

### 미리보기 (정보만)
```
POST http://localhost:8080/api/nametag/preview
Content-Type: application/json

{ "fromSheet": true }
```

## ⚠️ 주의사항

- **dry-run: true** 상태에서 먼저 테스트! (실제 발송 안됨, 로그만 출력)
- 실제 발송 전 `/api/sms/test`로 본인에게 먼저 보내보기
- LMS 최대 2,000 byte (한글 약 660자)
- 솔라피 건당 비용: LMS 약 30원
