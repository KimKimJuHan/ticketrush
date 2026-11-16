# 작업 규칙

1인 프로젝트지만 팀 프로젝트와 같은 규칙으로 진행한다. 커밋 기록과 PR 자체가 포트폴리오의 일부다.

## 브랜치 전략: GitHub Flow

```
main ──●────────●──────────●──   (항상 빌드·테스트 통과 상태, 직접 push 금지)
        \      /  \       /
         feat/...   fix/...      (작업 단위 브랜치 → PR → Squash merge)
```

| 접두사 | 용도 | 예 |
| --- | --- | --- |
| `feat/` | 기능 추가 | `feat/seat-hold` |
| `fix/` | 버그 수정 | `fix/hold-expiry-timezone` |
| `refactor/` | 동작 변화 없는 구조 개선 | `refactor/reservation-service` |
| `perf/` | 성능 개선·실험 | `perf/pessimistic-lock` |
| `infra/` | 인프라·배포·CI | `infra/terraform-vpc` |
| `docs/` | 문서 | `docs/adr-0005` |
| `test/` | 테스트만 추가 | `test/concurrent-hold` |

## 커밋 메시지: Conventional Commits

```
<type>(<범위>): <무엇을 했는지, 한국어 가능, 50자 이내>

<왜 했는지, 필요할 때만>
```

예:
```
feat(hold): 좌석 선점 API 추가
fix(reservation): 선점 만료 직후 결제가 성공하던 문제 수정
perf(hold): 비관적 락 적용, p95 1.8s → 0.4s
```

`type`은 브랜치 접두사와 같다(`feat`, `fix`, `refactor`, `perf`, `infra`→`ci` 또는 `build`, `docs`, `test`, `chore`).

## PR 규칙

1. PR 하나 = 목적 하나. 변경이 400줄을 넘으면 나눈다.
2. CI(빌드·테스트)가 초록색이어야 머지한다.
3. PR 템플릿의 체크리스트를 채운다.
4. 머지는 **Squash and merge** → main의 커밋 하나가 PR 하나에 대응한다.
5. 혼자 하더라도 머지 전에 Files changed 탭에서 스스로 한 번 리뷰한다.

## GitHub 설정 (최초 1회)

- Settings → Branches → `main` 보호 규칙: PR 필수, CI 통과 필수
- Settings → General → Pull Requests: Squash merging만 허용, 머지 후 브랜치 자동 삭제
