package io.github.kimkimjuhan.ticketrush.seat.application;

import io.github.kimkimjuhan.ticketrush.common.error.BusinessException;
import io.github.kimkimjuhan.ticketrush.common.error.ErrorCode;
import io.github.kimkimjuhan.ticketrush.concert.domain.Performance;
import io.github.kimkimjuhan.ticketrush.concert.domain.PerformanceRepository;
import io.github.kimkimjuhan.ticketrush.reservation.domain.ReservationItemRepository;
import io.github.kimkimjuhan.ticketrush.seat.application.dto.HoldResponse;
import io.github.kimkimjuhan.ticketrush.seat.domain.PerformanceSeat;
import io.github.kimkimjuhan.ticketrush.seat.domain.PerformanceSeatRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좌석 선점 (F4) · 선점 해제.
 *
 * <h2>⚠️ 의도적으로 동시성 제어가 없는 버전 (ADR-0004)</h2>
 * 좌석을 읽고(①) → 살 수 있는지 판단하고(②) → HELD로 바꾼다(③). 두 요청이 동시에 ①을 지나면 둘 다 ②를 통과하고,
 * ③에서 나중에 커밋한 쪽이 먼저 쓴 선점자를 덮어쓴다(Lost Update). 두 사용자 모두 "선점 성공" 응답을 받는다.
 * 이 문제를 부하 테스트로 재현·측정한 뒤 개선 방식을 비교하는 것이 이 프로젝트의 핵심이므로, 지금은 고치지 않는다.
 * 중복 <b>판매</b>는 DB 제약조건(ADR-0003)이 막는다.
 */
@Service
@RequiredArgsConstructor
public class SeatHoldService {

    private final PerformanceRepository performanceRepository;
    private final PerformanceSeatRepository seatRepository;
    private final ReservationItemRepository reservationItemRepository;
    private final HoldProperties holdProperties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    /** 요청한 좌석은 전부 선점되거나 전부 실패한다. 하나라도 실패하면 예외 → 트랜잭션 롤백. */
    @Transactional
    public HoldResponse hold(Long userId, Long performanceId, List<Long> requestedSeatIds) {
        Instant now = clock.instant();
        Set<Long> seatIds = new LinkedHashSet<>(requestedSeatIds); // 중복 ID 제거, 순서 유지

        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> BusinessException.notFound("회차", performanceId));
        if (!performance.isBookingOpenAt(now)) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_OPEN);
        }

        // ① 읽기
        List<PerformanceSeat> seats = seatRepository.findByPerformanceIdAndIdIn(performanceId, seatIds);
        if (seats.size() != seatIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "이 회차에 없는 좌석이 포함되어 있습니다.");
        }

        // 1인 매수 제한: 이미 선점 중인 좌석(이번 요청 좌석 제외) + 이미 구매한 좌석 + 이번 요청
        long alreadyHeldOthers = seatRepository.countActiveHolds(performanceId, userId, now)
                - seats.stream().filter(seat -> seat.isActivelyHeldBy(userId, now)).count();
        long alreadyBought = reservationItemRepository.countActiveByUserAndPerformance(userId, performanceId);
        if (alreadyHeldOthers + alreadyBought + seats.size() > performance.getMaxSeatsPerUser()) {
            throw new BusinessException(ErrorCode.SEAT_LIMIT_EXCEEDED,
                    "1인당 최대 " + performance.getMaxSeatsPerUser() + "석까지 예매할 수 있습니다.");
        }

        // ② 판단
        for (PerformanceSeat seat : seats) {
            if (!seat.isHoldableBy(userId, now)) {
                throw new BusinessException(ErrorCode.SEAT_UNAVAILABLE);
            }
        }

        // ③ 쓰기 (트랜잭션 커밋 시 JPA 변경 감지로 UPDATE)
        Instant expiresAt = now.plus(holdProperties.duration());
        seats.forEach(seat -> seat.hold(userId, expiresAt));
        // 선점 성공 좌석 수. 같은 좌석의 성공이 여러 번 기록되면 중복 선점이 일어난 것이다
        meterRegistry.counter("ticketrush.seat.holds").increment(seats.size());

        return new HoldResponse(List.copyOf(seatIds), expiresAt);
    }

    @Transactional
    public void release(Long userId, Long performanceId, Long seatId) {
        PerformanceSeat seat = seatRepository.findByPerformanceIdAndIdIn(performanceId, List.of(seatId)).stream()
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("좌석", seatId));
        if (!seat.isHeldBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_HOLDER);
        }
        seat.releaseHold();
    }
}
