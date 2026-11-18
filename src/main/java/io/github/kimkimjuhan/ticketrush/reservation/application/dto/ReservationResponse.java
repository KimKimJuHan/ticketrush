package io.github.kimkimjuhan.ticketrush.reservation.application.dto;

import io.github.kimkimjuhan.ticketrush.reservation.domain.Reservation;
import io.github.kimkimjuhan.ticketrush.reservation.domain.ReservationItem;
import io.github.kimkimjuhan.ticketrush.reservation.domain.ReservationStatus;
import java.util.List;

public record ReservationResponse(
        Long reservationId,
        ReservationStatus status,
        int totalPrice,
        List<Long> seatIds
) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getStatus(),
                reservation.getTotalPrice(),
                reservation.getItems().stream().map(ReservationItem::getPerformanceSeatId).toList());
    }
}
