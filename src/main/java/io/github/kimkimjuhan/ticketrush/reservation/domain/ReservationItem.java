package io.github.kimkimjuhan.ticketrush.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예매에 포함된 좌석 1개.
 *
 * <p>(performance_seat_id) WHERE cancelled_at IS NULL 부분 유니크 인덱스가 중복 판매의 최종 방어선이다 (ADR-0003).
 */
@Entity
@Table(name = "reservation_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "performance_seat_id", nullable = false)
    private Long performanceSeatId;

    @Column(nullable = false)
    private int price;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    ReservationItem(Reservation reservation, Long performanceSeatId, int price) {
        this.reservation = reservation;
        this.performanceSeatId = performanceSeatId;
        this.price = price;
    }
}
