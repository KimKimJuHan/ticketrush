package io.github.kimkimjuhan.ticketrush.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 모의 결제. 실제 PG 연동은 범위 밖이다 (명세서 1장). 테이블 정의는 V1의 payments. */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(nullable = false)
    private int amount;

    /** PAID | REFUNDED (DB CHECK 제약). 아직 환불이 없어 문자열 상수로 둔다. */
    @Column(nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    private Payment(Long reservationId, int amount) {
        this.reservationId = reservationId;
        this.amount = amount;
        this.status = "PAID";
    }

    public static Payment mockPaid(Long reservationId, int amount) {
        return new Payment(reservationId, amount);
    }
}
