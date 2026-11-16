# TicketRush — 트래픽이 몰려도 중복 판매하지 않는 콘서트 예매 서버

![CI](https://github.com/KimKimJuHan/ticketrush/actions/workflows/ci.yml/badge.svg)

> 인기 공연 오픈 직후 수천 명이 동시에 몰리는 상황을 재현하고, **중복 판매 0건**과 **응답 p95 0.5초**를 목표로 설계·측정·개선한 기록입니다.

🚧 **진행 중** · 현재 단계: 3주차 (MVP)

## 목표 (SLO)

| 지표 | 목표 | 현재 |
| --- | --- | --- |
| 좌석 중복 판매 | 0건 | 측정 전 |
| 예매 API p95 (동시 1,000명) | 500ms 이하 | 측정 전 |
| 좌석 조회 API p95 (동시 1,000명) | 200ms 이하 | 측정 전 |
| 요청 성공률 (5xx 제외) | 99.9% 이상 | 측정 전 |

## 기술 스택

Java 21 · Spring Boot · Spring Data JPA · PostgreSQL · Redis · Flyway · Testcontainers · Docker Compose · GitHub Actions
(예정) k6 · Prometheus · Grafana · Loki · Terraform · AWS

## 설계 문서

| 문서 | 내용 |
| --- | --- |
| [ERD](docs/design/erd.md) | 테이블 구조와 설계 이유 |
| [좌석 상태 전이](docs/design/seat-state.md) | AVAILABLE → HELD → SOLD 규칙과 동시성 위험 지점 |
| [API 명세 v1](docs/api/api-v1.md) | 엔드포인트, 에러 코드 |
| [결정 기록 (ADR)](docs/adr/) | 주요 기술 결정과 이유 |

## 로컬 실행

필요: JDK 21, Docker Desktop

```bash
./gradlew bootRun          # compose.yaml의 PostgreSQL·Redis가 자동으로 함께 뜬다
curl localhost:8080/actuator/health
```

테스트 (Docker 필요, Testcontainers가 실제 DB를 띄움):
```bash
./gradlew test
```

## 진행 기록

| 주차 | 단계 | 상태 |
| --- | --- | --- |
| 0 | Java·Spring 기초 | 진행 중 |
| 1 | 설계 (ERD, API, ADR) | ✅ |
| 2 | 프로젝트 뼈대, CI, 중복 판매 방지 제약조건 테스트 | ✅ |
| 3~4 | MVP (공연 조회 → 좌석 조회 → 인증 → 선점 → 예매) | 진행 중 |
| 5 | 관측성 | |
| 6 | 첫 부하 테스트 | |
| 7~8 | 동시성 개선 실험 | |
| 9~10 | AWS, Terraform | |
| 11 | 장애 실험, 포스트모템 | |
| 12 | 마무리 | |
