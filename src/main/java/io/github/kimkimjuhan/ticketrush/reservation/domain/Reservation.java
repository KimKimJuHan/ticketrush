package io.github.kimkimjuhan.ticketrush.reservation.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 예매 1건 (좌석 여러 개 포함). 테이블 정의는 V1__init_schema.sql의 reservations. */
@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.PERSIST)
    private List<ReservationItem> items = new ArrayList<>();

    private Reservation(Long userId, Long performanceId, String idempotencyKey) {
        this.userId = userId;
        this.performanceId = performanceId;
        this.idempotencyKey = idempotencyKey;
        this.status = ReservationStatus.CONFIRMED;
    }

    public static Reservation confirm(Long userId, Long performanceId, String idempotencyKey) {
        return new Reservation(userId, performanceId, idempotencyKey);
    }

    /** 판매 시점 가격을 항목에 복사한다. 이후 좌석 가격이 바뀌어도 이미 판 가격은 그대로다. */
    public void addItem(Long performanceSeatId, int price) {
        items.add(new ReservationItem(this, performanceSeatId, price));
        totalPrice += price;
    }
}
