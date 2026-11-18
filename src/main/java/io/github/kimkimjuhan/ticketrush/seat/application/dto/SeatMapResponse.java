package io.github.kimkimjuhan.ticketrush.seat.application.dto;

import io.github.kimkimjuhan.ticketrush.seat.domain.PerformanceSeat;
import java.time.Instant;
import java.util.List;

/**
 * 좌석 배치도.
 *
 * <p>상태는 AVAILABLE / UNAVAILABLE 두 가지만 노출한다. 누군가 선점 중(HELD)인지 판매 완료(SOLD)인지는
 * 다른 사용자에게 필요 없는 정보이고, 노출하면 선점 현황을 엿보는 데 쓰일 수 있다.
 */
public record SeatMapResponse(Long performanceId, List<SeatItem> seats) {

    public static SeatMapResponse of(Long performanceId, List<PerformanceSeat> seats, Instant now) {
        return new SeatMapResponse(performanceId, seats.stream().map(seat -> SeatItem.of(seat, now)).toList());
    }

    public record SeatItem(
            Long seatId,
            String section,
            String row,
            int number,
            String grade,
            int price,
            DisplayStatus status
    ) {

        static SeatItem of(PerformanceSeat seat, Instant now) {
            return new SeatItem(
                    seat.getId(),
                    seat.getSection(),
                    seat.getRowLabel(),
                    seat.getSeatNumber(),
                    seat.getGrade(),
                    seat.getPrice(),
                    seat.isAvailableAt(now) ? DisplayStatus.AVAILABLE : DisplayStatus.UNAVAILABLE);
        }
    }

    public enum DisplayStatus {
        AVAILABLE,
        UNAVAILABLE
    }
}
