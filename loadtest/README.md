# 부하 테스트 (k6)

로컬 관측성 스택(`deploy/local`) 위에서 티켓 오픈 순간을 재현합니다. 도구 선택 이유는 [ADR-0006](../docs/adr/0006-load-testing-with-k6.md), 측정 결과는 [docs/perf/](../docs/perf/)에 기록합니다.

> ⚠️ 부하 테스트는 이 프로젝트의 로컬·AWS 테스트 환경에만 실행합니다. 다른 사람의 서버나 공개 서비스에 실행하면 안 됩니다.

## 실행 (Windows PowerShell)

```powershell
cd $HOME\Desktop\ticketrush
./loadtest/run.ps1
```

k6는 Docker 이미지로 실행하므로 따로 설치할 필요가 없습니다. 스크립트는 다음 4단계를 차례로 진행합니다.

| 단계 | 내용 |
| --- | --- |
| 1. 스택 확인 | `docker compose up -d --wait`. 앱이 healthy가 될 때까지 기다립니다 |
| 2. 시드 | [`seed/seed.sql`](seed/seed.sql)로 **모든 테이블을 비우고** 사용자 1,000명, 좌석 100석을 만듭니다 |
| 3. k6 | [`scenarios/hot-seat.js`](scenarios/hot-seat.js)를 실행하고, 결과 지표를 Prometheus로 보냅니다 |
| 4. 검증 | [`seed/verify.sql`](seed/verify.sql)로 DB에 남은 상태의 정합성을 검사합니다 |

옵션:

```powershell
./loadtest/run.ps1 -Locking none -Warmup         # 잠금 없음(기준선)으로 앱을 다시 띄우고, 워밍업 1회 후 측정
./loadtest/run.ps1 -Locking pessimistic -Warmup  # 비관적 락으로 같은 측정
./loadtest/run.ps1 -HotSeats 10                  # 1,000명이 10석에 몰림 (경합 극대화)
./loadtest/run.ps1 -Users 300 -Label small       # 사용자 수 줄이기
```

- `-Locking`: 앱의 좌석 잠금 방식(`pessimistic` | `none`, ADR-0007). 바꾸면 앱 컨테이너가 새 설정으로 다시 만들어진다. 생략하면 운영 기본값 `pessimistic`.
- `-Warmup`: 측정 전에 같은 시나리오를 한 번 더 돌린다. 앱이 막 뜬 직후에는 JIT 컴파일 전이라 느리므로, 비교는 워밍업 후 값으로 한다.
- `-Label`: 결과 파일 이름과 Grafana의 `testid`. 생략하면 `lock-<잠금 방식>`.
- 실제로 적용된 잠금 방식은 `/actuator/info`에서 읽어 결과 JSON(`config.lockMode`)에 기록된다.

## 시나리오: hot-seat

각 사용자(VU)는 다음 순서로 한 번씩 움직입니다.

```
⓪ 로그인 → 오픈 시각까지 대기      (측정 제외, 전원이 같은 시각에 출발하기 위한 준비)
① 좌석 배치도 조회                   GET  /api/v1/performances/1/seats
② 빈 좌석 하나 선점                  POST /api/v1/performances/1/holds
③ 결제 화면 체류 0.3~1.0초
④ 예매 확정                          POST /api/v1/reservations  (Idempotency-Key 포함)
```

## 결과 읽는 법

k6는 **응답**을 세고, `verify.sql`은 **DB에 남은 상태**를 봅니다. 두 결과를 함께 봐야 합니다.

| 지표 | 의미 | 기준선(락 없음)에서 예상 |
| --- | --- | --- |
| `ticketrush_hold_success` | 선점 성공(201) 응답 수 | 좌석 수(100)보다 **많음**. 같은 좌석이 여러 번 "성공"함 |
| `ticketrush_false_success` | 선점 성공을 받고도 예매 단계에서 403 `NOT_HOLDER`가 난 사용자 수. **거짓 성공** | 0보다 큼 (threshold 실패) |
| `ticketrush_db_guard_blocked` | 예매 단계에서 DB 유니크 인덱스가 두 번째 판매를 막은 횟수 | 0 이상 |
| `ticketrush_reserve_success` | 예매 성공 수 | 100 이하 |
| verify `double_sale` | 같은 좌석이 두 번 팔렸는지 | **0 (PASS)**. DB 제약조건이 막음 |
| verify `sold_but_not_marked` | 팔린 좌석의 상태가 Lost Update로 HELD로 덮어써졌는지 | 0이 아닐 수 있음 |

threshold 중 `ticketrush_false_success count==0`은 **개선 목표**입니다. 기준선에서 이 항목이 실패하는 것이 정상이고, 실패한 숫자가 곧 이번 측정의 결과입니다. 이후 락 전략을 적용해 이 값이 0이 되는지 비교합니다.

## 설계 메모

- **로그인은 각 VU가 직접 합니다.** k6는 `setup()`이 반환한 값을 VU마다 복사합니다. 토큰 1,000개를 setup에서 만들어 넘기면 1,000 VU × 1,000개가 복사되어 메모리가 수백 MB 늘어납니다.
- **시드 계정의 BCrypt cost는 4입니다.** 검증 비용은 해시에 기록된 cost를 따르므로, 1,000명 로그인이 몇 초 안에 끝납니다. 운영 계정의 기본 cost는 10이며, 로그인 성능은 이 시나리오의 측정 대상이 아닙니다.
- **409와 예매 단계의 403은 실패로 세지 않습니다.** 선점 경쟁에서 지는 것(409)과 빼앗긴 선점으로 예매를 시도하는 것(403 `NOT_HOLDER`)은 이 시나리오가 측정하려는 결과이므로 `http_req_failed`에서 제외하고, 에러 코드별 커스텀 카운터로 따로 셉니다. 응답 분류는 상태 코드와 에러 코드를 함께 확인합니다.
- **k6와 앱이 같은 PC에서 돕니다.** 앱 컨테이너는 CPU 2코어로 제한되어 있지만 k6와 CPU를 나눠 씁니다. 절대 수치보다는 같은 환경에서의 **전후 비교**에 의미가 있습니다. AWS 단계에서 부하 발생기를 별도 인스턴스로 분리합니다.
