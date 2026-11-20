# API 명세 v1

- Base path: `/api/v1`
- 형식: JSON (UTF-8), 시각은 ISO 8601 UTC (`2026-12-24T11:00:00Z`)
- 인증: `Authorization: Bearer <JWT>`. 🔒 = 로그인 필요
- **사용자 식별은 토큰에서만 한다.** 요청 본문이나 쿼리에 `userId`를 받지 않는다.

## 에러 형식

[RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) 형식에 프로젝트 고유 `code`를 더한다. 클라이언트는 `code`로 분기한다.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "이미 다른 사용자가 선점한 좌석입니다.",
  "code": "SEAT_UNAVAILABLE",
  "instance": "/api/v1/performances/12/holds"
}
```

| code | status | 의미 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 요청 형식 오류 |
| `UNAUTHORIZED` | 401 | 토큰 없음·만료·위조 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 (어느 쪽인지 구분하지 않음) |
| `FORBIDDEN` | 403 | 권한 없음 |
| `NOT_HOLDER` | 403 | 본인이 선점하지 않은 좌석 |
| `NOT_FOUND` | 404 | 리소스 없음 |
| `EMAIL_ALREADY_EXISTS` | 409 | 이미 가입된 이메일 (대소문자 구분 없음) |
| `DUPLICATE_REQUEST` | 409 | 같은 Idempotency-Key 요청이 동시에 처리 중 |
| `BOOKING_NOT_OPEN` | 409 | 예매 오픈 전 |
| `SEAT_UNAVAILABLE` | 409 | 이미 선점·판매된 좌석 |
| `SEAT_LIMIT_EXCEEDED` | 409 | 1인 매수 한도 초과 |
| `HOLD_EXPIRED` | 409 | 선점 만료 |
| `CANCEL_NOT_ALLOWED` | 409 | 취소 불가 (공연 시작 후 등) |

## 1. 인증

### `POST /auth/signup`
```json
{ "email": "user@example.com", "password": "8~64자", "name": "홍길동" }
```
→ `201 Created` `{ "userId": 1 }`
- 이메일은 소문자로 정규화해 저장한다. 실패: 400 `VALIDATION_FAILED`, 409 `EMAIL_ALREADY_EXISTS`

### `POST /auth/login`
```json
{ "email": "user@example.com", "password": "..." }
```
→ `200 OK` `{ "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600 }`
- 토큰: HS256 서명 JWT, 유효시간 1시간, `sub` = 사용자 ID. 실패: 401 `INVALID_CREDENTIALS`

### `GET /users/me` 🔒
→ `200` `{ "userId": 1, "email": "user@example.com", "name": "홍길동" }`

## 2. 공연 조회

### `GET /concerts?page=0&size=20&sort=id,desc`
- `page`: 0부터 시작. `size`: 기본 20, 최대 100
- `sort`: `id` 또는 `title`만 허용 (기본 `id,desc` = 최근 등록순). 그 외 필드는 400 `VALIDATION_FAILED`

→ `200`
```json
{
  "content": [
    { "concertId": 3, "title": "겨울 콘서트", "venue": "올림픽홀" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### `GET /concerts/{concertId}`
→ `200` (회차는 시작 시각 순)
```json
{
  "concertId": 3,
  "title": "겨울 콘서트",
  "venue": "올림픽홀",
  "description": "...",
  "performances": [
    { "performanceId": 12, "startsAt": "2026-12-24T10:00:00Z", "bookingOpensAt": "2026-12-17T10:00:00Z" }
  ]
}
```
실패: 404 `NOT_FOUND`, 숫자가 아닌 id는 400 `VALIDATION_FAILED`

### `GET /performances/{performanceId}/seats`
가장 많이 호출되는 API. 6주차 성능 측정 대상.

→ `200`
```json
{
  "performanceId": 12,
  "seats": [
    { "seatId": 501, "section": "A", "row": "3", "number": 14, "grade": "VIP", "price": 165000, "status": "AVAILABLE" }
  ]
}
```
- `status`는 `AVAILABLE` 또는 `UNAVAILABLE`만 노출한다. 다른 사람의 선점 여부를 구분해 보여주지 않는다.
- 선점 시간이 지난 좌석은 정리 작업 전이라도 `AVAILABLE`로 보인다.
- 정렬: 구역 → 열 → 번호. 실패: 404 `NOT_FOUND`

## 3. 선점 🔒

### `POST /performances/{performanceId}/holds`
```json
{ "seatIds": [501, 502] }
```
→ `201 Created`
```json
{ "seatIds": [501, 502], "expiresAt": "2026-12-24T11:05:00Z" }
```
- 요청한 좌석은 **전부 선점되거나 전부 실패**한다(원자성). 한 좌석이라도 불가능하면 409.
- 선점 유지 시간 5분. 본인이 이미 선점한 좌석을 다시 요청하면 만료 시각이 연장된다.
- 1인 한도 = 이 회차에서 선점 중인 좌석 + 이미 구매한 좌석 + 이번 요청 ≤ `max_seats_per_user`
- 실패: 409 `BOOKING_NOT_OPEN` · `SEAT_UNAVAILABLE` · `SEAT_LIMIT_EXCEEDED`, 404 `NOT_FOUND`(이 회차에 없는 좌석)
- 동시 요청: 좌석 행을 잠근 뒤 판단하므로(ADR-0007), 같은 좌석에 동시에 요청하면 한 명만 201을 받고 나머지는 409 `SEAT_UNAVAILABLE`을 받는다.

### `DELETE /performances/{performanceId}/holds/{seatId}`
본인 선점 해제 → `204 No Content`

## 4. 예매 🔒

### `POST /reservations`
헤더: `Idempotency-Key: <클라이언트가 만든 UUID>` (필수)
```json
{ "performanceId": 12, "seatIds": [501, 502], "paymentMethod": "MOCK" }
```
→ `201 Created`
```json
{ "reservationId": 9001, "status": "CONFIRMED", "totalPrice": 330000, "seats": [501, 502] }
```
- 같은 `Idempotency-Key`로 다시 요청하면 새 예매·결제를 만들지 않고 **처음 결과를 그대로** 돌려준다.
- `Idempotency-Key`는 1~64자. 없으면 400 `VALIDATION_FAILED`
- 실패: 403 `NOT_HOLDER`(선점하지 않았거나 다른 사람에게 덮어써짐), 409 `HOLD_EXPIRED`, 409 `SEAT_UNAVAILABLE`(DB 제약조건이 중복 판매를 막은 경우)

### `GET /reservations/me` (예정)
→ `200` 내 예매 목록 (최신순)

### `POST /reservations/{reservationId}/cancel` (예정)
→ `200` `{ "reservationId": 9001, "status": "CANCELLED" }`, 좌석은 다시 `AVAILABLE`

## 5. 운영

| 경로 | 설명 |
| --- | --- |
| `GET /actuator/health` | 상태 확인 (로드밸런서·컨테이너 헬스체크) |
| `GET /actuator/health/liveness`, `/readiness` | 컨테이너 재시작·트래픽 투입 판단 |
