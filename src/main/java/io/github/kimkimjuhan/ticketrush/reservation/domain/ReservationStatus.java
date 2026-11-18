package io.github.kimkimjuhan.ticketrush.reservation.domain;

/** DB CHECK 제약(ck_reservations_status)과 값이 같아야 한다. */
public enum ReservationStatus {
    CONFIRMED,
    CANCELLED
}
