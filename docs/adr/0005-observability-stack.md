# ADR-0005: 관측성은 Prometheus + Grafana로 시작하고, 지표 이름을 SLO에 맞춘다

- 상태: 채택
- 날짜: 2026-11-18

## 배경
부하 테스트의 목적은 "느리다", "깨진다"를 숫자로 증명하고 원인을 찾는 것이다. 테스트 도구(k6)의 결과만으로는
서버 내부에서 무엇이 병목인지(DB 커넥션, CPU, 특정 API) 알 수 없다.

## 결정
- 애플리케이션: Spring Boot Actuator + Micrometer로 `/actuator/prometheus`에 지표를 노출한다.
  - `http.server.requests`는 **히스토그램**으로 내보낸다. 평균이 아니라 p95를 SLO로 정했기 때문이다.
  - 비즈니스 지표를 직접 센다: `ticketrush_seat_holds_total`, `ticketrush_seats_sold_total`,
    `ticketrush_business_errors_total{code}`. 특히 `code="NOT_HOLDER"`는 Lost Update 피해 건수다.
- 수집·시각화: Prometheus(5초 간격 수집) + Grafana. 데이터소스와 대시보드는 파일로 프로비저닝해 리포에 둔다.
- 로컬 전체 스택은 `deploy/local/compose.yaml`로 한 번에 띄운다. 앱 컨테이너는 CPU 2개·메모리 1GB로 고정해
  측정 조건을 일정하게 유지한다.

## 검토한 대안
| 대안 | 기각 이유 |
| --- | --- |
| k6 결과만 보기 | 클라이언트 관점 숫자만 있고 서버 내부 병목을 볼 수 없다 |
| Spring Boot Admin | 개발 편의 도구에 가깝고, 시계열 저장·질의가 약하다 |
| 관리형 서비스(CloudWatch 등)부터 | 로컬에서 비용 없이 반복 실험하기 어렵다. AWS 단계에서 함께 검토한다 |

## 결과
- 대시보드 하나로 RED 지표(요청량·에러·응답시간), 비즈니스 지표, 자원(커넥션 풀·힙·CPU)을 함께 본다.
- `/actuator/prometheus`는 인증 없이 열려 있다. 운영에서는 로드밸런서 경로에서 제외하고 보안그룹으로 내부 수집만 허용한다.
- 로그 수집(Loki)과 분산 추적은 아직 없다. 지표로 원인을 좁히지 못하는 경우가 생기면 추가한다.
