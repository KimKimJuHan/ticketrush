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
| `UNAUTHORIZED` | 401 | 토큰 없음·만료 |
| `NOT_HOLDER` | 403 | 본인이 선점하지 않은 좌석 |
| `NOT_FOUND` | 404 | 리소스 없음 |
| `BOOKING_NOT_OPEN` | 409 | 예매 오픈 전 |
| `SEAT_UNAVAILABLE` | 409 | 이미 선점·판매된 좌석 |
| `SEAT_LIMIT_EXCEEDED` | 409 | 1인 매수 한도 초과 |
| `HOLD_EXPIRED` | 409 | 선점 만료 |
| `CANCEL_NOT_ALLOWED` | 409 | 취소 불가 (공연 시작 후 등) |

## 1. 인증

### `POST /auth/signup`
```json
{ "email": "user@example.com", "password": "8자 이상", "name": "홍길동" }
```
→ `201 Created`

### `POST /auth/login`
```json
{ "email": "user@example.com", "password": "..." }
```
→ `200 OK` `{ "accessToken": "eyJ...", "expiresIn": 3600 }`

## 2. 공연 조회

### `GET /concerts?page=0&size=20`
→ `200` 공연 목록 (페이지)

### `GET /concerts/{concertId}`
→ `200` 공연 상세 + 회차 목록 `[{ "performanceId", "startsAt", "bookingOpensAt" }]`

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
`status`는 `AVAILABLE` 또는 `UNAVAILABLE`만 노출한다. 다른 사람의 선점 여부를 구분해 보여주지 않는다.

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
- 실패: `BOOKING_NOT_OPEN`, `SEAT_UNAVAILABLE`, `SEAT_LIMIT_EXCEEDED`

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
- 같은 `Idempotency-Key`로 다시 요청하면 새 예매를 만들지 않고 **처음 결과를 그대로** 돌려준다.
- 실패: `NOT_HOLDER`, `HOLD_EXPIRED`

### `GET /reservations/me`
→ `200` 내 예매 목록 (최신순)

### `POST /reservations/{reservationId}/cancel`
→ `200` `{ "reservationId": 9001, "status": "CANCELLED" }`, 좌석은 다시 `AVAILABLE`

## 5. 운영

| 경로 | 설명 |
| --- | --- |
| `GET /actuator/health` | 상태 확인 (로드밸런서·컨테이너 헬스체크) |
| `GET /actuator/health/liveness`, `/readiness` | 컨테이너 재시작·트래픽 투입 판단 |
