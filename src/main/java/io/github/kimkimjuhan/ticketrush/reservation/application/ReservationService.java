package io.github.kimkimjuhan.ticketrush.reservation.application;

import io.github.kimkimjuhan.ticketrush.common.error.BusinessException;
import io.github.kimkimjuhan.ticketrush.common.error.ErrorCode;
import io.github.kimkimjuhan.ticketrush.reservation.application.dto.ReservationResponse;
import io.github.kimkimjuhan.ticketrush.reservation.application.dto.ReserveRequest;
import io.github.kimkimjuhan.ticketrush.reservation.domain.Payment;
import io.github.kimkimjuhan.ticketrush.reservation.domain.PaymentRepository;
import io.github.kimkimjuhan.ticketrush.reservation.domain.Reservation;
import io.github.kimkimjuhan.ticketrush.reservation.domain.ReservationRepository;
import io.github.kimkimjuhan.ticketrush.seat.domain.PerformanceSeat;
import io.github.kimkimjuhan.ticketrush.seat.domain.PerformanceSeatRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예매 확정 (F5): 선점한 좌석을 (모의) 결제하고 SOLD로 바꾼다.
 *
 * <p>멱등성: 클라이언트가 보낸 Idempotency-Key가 같으면 새 예매를 만들지 않고 처음 결과를 돌려준다.
 * 결제 버튼 연타나 네트워크 재시도로 같은 요청이 여러 번 와도 결제는 한 번만 일어난다.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;
    private static final String SEAT_SOLD_CONSTRAINT = "ux_reservation_items_active_seat";
    private static final String IDEMPOTENCY_CONSTRAINT = "uq_reservations_idempotency";

    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final PerformanceSeatRepository seatRepository;
    private final Clock clock;

    @Transactional
    public ReservationResponse reserve(Long userId, String idempotencyKey, ReserveRequest request) {
        validateIdempotencyKey(idempotencyKey);

        Optional<Reservation> previous = reservationRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (previous.isPresent()) {
            return ReservationResponse.from(previous.get());
        }

        Instant now = clock.instant();
        Set<Long> seatIds = new LinkedHashSet<>(request.seatIds());
        List<PerformanceSeat> seats = seatRepository.findByPerformanceIdAndIdIn(request.performanceId(), seatIds);
        if (seats.size() != seatIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "이 회차에 없는 좌석이 포함되어 있습니다.");
        }
        for (PerformanceSeat seat : seats) {
            if (!seat.isHeldBy(userId)) {
                throw new BusinessException(ErrorCode.NOT_HOLDER); // 선점 안 했거나 다른 사람에게 덮어써짐
            }
            if (!seat.isActivelyHeldBy(userId, now)) {
                throw new BusinessException(ErrorCode.HOLD_EXPIRED);
            }
        }

        Reservation reservation = Reservation.confirm(userId, request.performanceId(), idempotencyKey);
        seats.forEach(seat -> reservation.addItem(seat.getId(), seat.getPrice()));
        try {
            reservationRepository.save(reservation);
            paymentRepository.save(Payment.mockPaid(reservation.getId(), reservation.getTotalPrice()));
            seats.forEach(PerformanceSeat::markSold);
            reservationRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
        return ReservationResponse.from(reservation);
    }

    /** DB 제약조건 위반을 사용자에게 의미 있는 에러로 바꾼다. 어떤 제약인지는 이름으로 구분한다. */
    private static BusinessException translate(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        if (message.contains(SEAT_SOLD_CONSTRAINT)) {
            // ADR-0003의 최종 방어선이 동작한 경우: 애플리케이션 검사를 통과했지만 이미 팔린 좌석
            return new BusinessException(ErrorCode.SEAT_UNAVAILABLE);
        }
        if (message.contains(IDEMPOTENCY_CONSTRAINT)) {
            // 같은 Idempotency-Key 요청이 동시에 두 개 들어와 다른 쪽이 먼저 저장한 경우
            return new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }
        throw e;
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key 헤더는 1~" + MAX_IDEMPOTENCY_KEY_LENGTH + "자여야 합니다.");
        }
    }
}
