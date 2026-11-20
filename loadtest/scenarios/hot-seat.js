// =============================================================================
// 시나리오: 티켓 오픈 러시 (hot seat)
//
// 1,000명이 같은 순간에 좌석 100석에 몰린다. 각 사용자는
//   ⓪ 로그인 후 오픈 시각까지 대기 (측정 대상 아님)
//   ① 좌석 배치도 조회 → ② 빈 좌석 하나 선점 → ③ 결제 화면 체류(think time) → ④ 예매 확정
// 을 한 번씩 수행한다. ⓪에서 모두가 같은 시각(startAt)까지 기다리므로 ①부터는 동시에 출발한다.
//
// 측정 목적 (ADR-0004의 의도적 Lost Update를 숫자로 확인)
//   * 거짓 성공: ②에서 201 "선점 성공"을 받았지만 ④에서 NOT_HOLDER로 실패한 사용자 수
//   * 중복 판매: DB 제약조건(ADR-0003)이 막아야 하므로 0이어야 한다 → verify.sql로 확인
//   * 응답시간: SLO(예매 p95 500ms, 좌석 조회 p95 200ms) 충족 여부
//
// 실행: loadtest/README.md 참고 (loadtest/run.ps1 한 줄)
// =============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

// ---------------------------------------------------------------- 설정 (환경 변수로 바꿀 수 있음)
const BASE_URL = __ENV.BASE_URL || 'http://app:8080';
const PERFORMANCE_ID = Number(__ENV.PERFORMANCE_ID || 1);
const USERS = Number(__ENV.USERS || 1000);              // 동시 사용자 수 = VU 수
const HOT_SEATS = Number(__ENV.HOT_SEATS || 100);       // 앞에서부터 N석만 노린다 (작을수록 경합이 심해진다)
const THINK_MIN = Number(__ENV.THINK_MIN || 0.3);       // 선점 후 결제까지 머무는 시간(초)
const THINK_MAX = Number(__ENV.THINK_MAX || 1.0);
const START_DELAY = Number(__ENV.START_DELAY || 20);    // setup 이후 오픈까지 남은 시간(초). 이 안에 전원이 로그인해야 한다
const PASSWORD = 'loadtest-pass-123';                   // seed.sql과 같은 로컬 전용 값
const RUN_LABEL = __ENV.RUN_LABEL || 'baseline';

// ---------------------------------------------------------------- 커스텀 지표
const holdSuccess = new Counter('ticketrush_hold_success');           // 선점 201
const holdRejected = new Counter('ticketrush_hold_rejected');         // 선점 409 (이미 선점·판매된 좌석)
const reserveSuccess = new Counter('ticketrush_reserve_success');     // 예매 201
const falseSuccess = new Counter('ticketrush_false_success');         // 선점 성공 → 예매 403 NOT_HOLDER (빼앗김)
const dbGuardBlocked = new Counter('ticketrush_db_guard_blocked');    // 예매 409 SEAT_UNAVAILABLE (DB 최종 방어선 작동)
const soldOutSeen = new Counter('ticketrush_sold_out_seen');          // 배치도에 빈 좌석이 하나도 없음
const unexpected = new Counter('ticketrush_unexpected');              // 위 분류에 없는 응답 (5xx, 401 등)
const lateStart = new Counter('ticketrush_late_start');               // 오픈 시각까지 로그인을 못 끝낸 사용자

// 409는 이 시나리오에서 "정상적인 실패"(선점 경쟁 패배)다. http_req_failed가 409를 에러로 세지 않게 한다.
// 예매 단계의 403 NOT_HOLDER도 측정 대상이므로 그 요청에만 따로 허용한다 (아래 RESERVE_EXPECTED).
http.setResponseCallback(http.expectedStatuses(200, 201, 409));
const RESERVE_EXPECTED = http.expectedStatuses(201, 403, 409);

export const options = {
    setupTimeout: '1m',
    scenarios: {
        rush: {
            // 모든 VU가 미리 준비된 뒤 동시에 한 번씩 실행된다 → "오픈 순간" 재현
            executor: 'per-vu-iterations',
            vus: USERS,
            iterations: 1,
            maxDuration: '2m',
        },
    },
    thresholds: {
        // 성능 SLO (README)
        'http_req_duration{name:GET /seats}': ['p(95)<200'],
        'http_req_duration{name:POST /holds}': ['p(95)<500'],
        'http_req_duration{name:POST /reservations}': ['p(95)<500'],
        'http_req_failed{scenario:rush}': ['rate<0.001'],
        // 정합성 목표. 기준선(락 없음)에서는 실패하는 것이 정상이며, 그 숫자가 이번 측정의 결과다
        ticketrush_false_success: ['count==0'],
        ticketrush_unexpected: ['count==0'],
        ticketrush_late_start: ['count==0'], // 0이 아니면 동시 출발이 깨진 것이므로 START_DELAY를 늘린다
    },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// ---------------------------------------------------------------- setup: 좌석 목록 확인 + 오픈 시각 결정
// setup의 반환값은 VU마다 복사된다. 토큰 1,000개를 여기서 만들어 넘기면 1,000 × 1,000개가 복사되므로
// 로그인은 각 VU가 직접 하고, setup은 작은 값(좌석 ID, 출발 시각)만 넘긴다.
export function setup() {
    const seatRes = http.get(`${BASE_URL}/api/v1/performances/${PERFORMANCE_ID}/seats`, {
        tags: { name: 'setup' },
    });
    if (seatRes.status !== 200) {
        throw new Error(`좌석 배치도 조회 실패 (${seatRes.status}). seed.sql을 먼저 실행했는지 확인하세요.`);
    }
    const allSeats = seatRes.json('seats');
    const notAvailable = allSeats.filter((s) => s.status !== 'AVAILABLE').length;
    if (notAvailable > 0) {
        throw new Error(`이미 선점·판매된 좌석이 ${notAvailable}석 있습니다. seed.sql로 초기화하세요.`);
    }
    const hotSeatIds = allSeats.map((s) => s.seatId).sort((a, b) => a - b).slice(0, HOT_SEATS);

    // 서버가 어떤 잠금 방식으로 실행 중인지 결과에 남긴다 (ADR-0007, /actuator/info)
    const infoRes = http.get(`${BASE_URL}/actuator/info`, { tags: { name: 'setup' } });
    let lockMode = 'unknown';
    try {
        lockMode = infoRes.json('ticketrush.hold-locking') || 'unknown';
    } catch (e) {
        // 정보 엔드포인트가 없는 이전 버전 서버
    }

    const startAt = Date.now() + START_DELAY * 1000;
    console.log(`잠금 방식 ${lockMode}, 목표 좌석 ${hotSeatIds.length}석, ${START_DELAY}초 뒤 동시 오픈`);
    return { hotSeatIds, startAt, lockMode };
}

// ---------------------------------------------------------------- 사용자 1명의 행동
export default function (data) {
    // ⓪ 로그인 → 오픈 시각까지 대기
    const userNo = String(exec.vu.idInTest).padStart(4, '0');
    const loginRes = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email: `lt${userNo}@loadtest.local`, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' }, timeout: '120s' },
    );
    if (!check(loginRes, { 'login 200': (r) => r.status === 200 })) {
        unexpected.add(1, { step: 'login', status: String(loginRes.status) });
        return;
    }
    const token = loginRes.json('accessToken');
    const waitSec = (data.startAt - Date.now()) / 1000;
    if (waitSec > 0) {
        sleep(waitSec);
    } else {
        lateStart.add(1);
    }

    const auth = {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    };

    // ① 배치도 조회: 사용자는 화면에서 "비어 보이는" 좌석을 고른다
    const mapRes = http.get(`${BASE_URL}/api/v1/performances/${PERFORMANCE_ID}/seats`, {
        tags: { name: 'GET /seats' },
    });
    if (!check(mapRes, { 'seats 200': (r) => r.status === 200 })) {
        unexpected.add(1, { step: 'seats', status: String(mapRes.status) });
        return;
    }
    const hot = new Set(data.hotSeatIds);
    const candidates = mapRes.json('seats').filter((s) => s.status === 'AVAILABLE' && hot.has(s.seatId));
    if (candidates.length === 0) {
        soldOutSeen.add(1);
        return;
    }
    const seatId = candidates[Math.floor(Math.random() * candidates.length)].seatId;

    // ② 선점
    const holdRes = http.post(
        `${BASE_URL}/api/v1/performances/${PERFORMANCE_ID}/holds`,
        JSON.stringify({ seatIds: [seatId] }),
        { ...auth, tags: { name: 'POST /holds' } },
    );
    if (holdRes.status === 409) {
        holdRejected.add(1, { code: errorCode(holdRes) });
        return;
    }
    if (!check(holdRes, { 'hold 201': (r) => r.status === 201 })) {
        unexpected.add(1, { step: 'hold', status: String(holdRes.status), code: errorCode(holdRes) });
        return;
    }
    holdSuccess.add(1);

    // ③ 결제 화면에 머무는 시간. 이 사이에 다른 사용자가 같은 좌석 선점을 덮어쓸 수 있다
    sleep(THINK_MIN + Math.random() * (THINK_MAX - THINK_MIN));

    // ④ 예매 확정. Idempotency-Key는 사용자·반복마다 고유하게 만든다
    const reserveRes = http.post(
        `${BASE_URL}/api/v1/reservations`,
        JSON.stringify({ performanceId: PERFORMANCE_ID, seatIds: [seatId], paymentMethod: 'MOCK' }),
        {
            headers: { ...auth.headers, 'Idempotency-Key': `lt-${exec.vu.idInTest}-${exec.scenario.iterationInTest}` },
            tags: { name: 'POST /reservations' },
            responseCallback: RESERVE_EXPECTED,
        },
    );
    if (reserveRes.status === 201) {
        reserveSuccess.add(1);
        return;
    }
    // 상태 코드는 API 명세(docs/api/api-v1.md)를 따른다: NOT_HOLDER 403, SEAT_UNAVAILABLE 409
    const code = errorCode(reserveRes);
    if (reserveRes.status === 403 && code === 'NOT_HOLDER') {
        falseSuccess.add(1); // "선점 성공"이라는 응답을 믿었지만 실제로는 좌석을 빼앗겼다
    } else if (reserveRes.status === 409 && code === 'SEAT_UNAVAILABLE') {
        dbGuardBlocked.add(1); // 애플리케이션 검사를 통과했지만 DB 유니크 인덱스가 두 번째 판매를 막았다
    } else {
        unexpected.add(1, { step: 'reserve', status: String(reserveRes.status), code });
    }
}

function errorCode(res) {
    try {
        return res.json('code') || 'NONE';
    } catch (e) {
        return 'NOT_JSON';
    }
}

// ---------------------------------------------------------------- 결과 요약
export function handleSummary(data) {
    const count = (name) => (data.metrics[name] ? data.metrics[name].values.count : 0);
    const p95 = (name) => {
        const m = data.metrics[`http_req_duration{name:${name}}`];
        return m ? Math.round(m.values['p(95)']) : null;
    };

    const result = {
        label: RUN_LABEL,
        finishedAt: new Date().toISOString(),
        config: {
            lockMode: data.setup_data ? data.setup_data.lockMode : 'unknown',
            users: USERS,
            hotSeats: HOT_SEATS,
            thinkTimeSec: [THINK_MIN, THINK_MAX],
            startDelaySec: START_DELAY,
        },
        counts: {
            holdSuccess: count('ticketrush_hold_success'),
            holdRejected: count('ticketrush_hold_rejected'),
            reserveSuccess: count('ticketrush_reserve_success'),
            falseSuccess: count('ticketrush_false_success'),
            dbGuardBlocked: count('ticketrush_db_guard_blocked'),
            soldOutSeen: count('ticketrush_sold_out_seen'),
            unexpected: count('ticketrush_unexpected'),
            lateStart: count('ticketrush_late_start'),
        },
        p95ms: {
            seats: p95('GET /seats'),
            holds: p95('POST /holds'),
            reservations: p95('POST /reservations'),
        },
    };
    const c = result.counts;
    const lines = [
        '',
        '================ TicketRush 핫시트 결과 ================',
        `잠금 방식 ${result.config.lockMode} / 좌석 ${HOT_SEATS}석 / 사용자 ${USERS}명`,
        `선점 성공 응답        ${c.holdSuccess}  (좌석 수보다 많으면 중복 선점 발생)`,
        `선점 거절(409)        ${c.holdRejected}`,
        `예매 성공             ${c.reserveSuccess}`,
        `거짓 성공(NOT_HOLDER) ${c.falseSuccess}  ← 선점 성공 응답을 받고도 좌석을 빼앗긴 사용자`,
        `DB 방어선 작동        ${c.dbGuardBlocked}`,
        `매진 화면             ${c.soldOutSeen}`,
        `예상 밖 응답          ${c.unexpected}`,
        `늦은 출발             ${c.lateStart}  (0이 아니면 START_DELAY를 늘려 다시 실행)`,
        `p95 (ms)  좌석 조회 ${result.p95ms.seats} / 선점 ${result.p95ms.holds} / 예매 ${result.p95ms.reservations}`,
        '데이터 정합성은 verify.sql 결과로 확인한다.',
        '=======================================================',
        '',
    ];

    const stamp = result.finishedAt.replace(/[:.]/g, '-');
    return {
        stdout: textSummary(data, { indent: ' ', enableColors: false }) + lines.join('\n'),
        [`/results/hot-seat-${RUN_LABEL}-${stamp}.json`]: JSON.stringify({ summary: result, k6: data }, null, 2),
    };
}
