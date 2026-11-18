package io.github.kimkimjuhan.ticketrush.seat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 회차별 좌석 = 판매 단위. 테이블 정의는 V1__init_schema.sql의 performance_seats.
 *
 * <p>회차와 사용자는 엔티티 연관관계 대신 ID 값으로만 들고 있다. 좌석 조회는 가장 자주 호출되는 API라서,
 * 실수로 연관 엔티티를 건드려 추가 쿼리가 나가는 일을 구조적으로 막기 위해서다.
 */
@Entity
@Table(name = "performance_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(nullable = false, length = 10)
    private String section;

    @Column(name = "row_label", nullable = false, length = 10)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Column(nullable = false, length = 10)
    private String grade;

    @Column(nullable = false)
    private int price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    @Column(name = "held_by_user_id")
    private Long heldByUserId;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 지금 이 좌석을 살 수 있는가.
     *
     * <p>선점이 만료된 좌석은 DB에 아직 HELD로 남아 있어도(정리 작업 전) 살 수 있는 좌석으로 본다.
     * 정리 작업이 늦게 돌아도 사용자에게는 정확한 상태가 보이게 하기 위해서다.
     */
    public boolean isAvailableAt(Instant now) {
        return switch (status) {
            case AVAILABLE -> true;
            case HELD -> isHoldExpired(now);
            case SOLD -> false;
        };
    }

    /** 이 사용자가 선점할 수 있는가. 본인이 이미 선점 중인 좌석은 다시 선점(시간 연장)할 수 있다. */
    public boolean isHoldableBy(Long userId, Instant now) {
        return isAvailableAt(now) || isActivelyHeldBy(userId, now);
    }

    public boolean isActivelyHeldBy(Long userId, Instant now) {
        return status == SeatStatus.HELD && userId.equals(heldByUserId) && !isHoldExpired(now);
    }

    public boolean isHeldBy(Long userId) {
        return status == SeatStatus.HELD && userId.equals(heldByUserId);
    }

    /** AVAILABLE → HELD. 호출 전에 {@link #isHoldableBy}로 확인해야 한다. */
    public void hold(Long userId, Instant expiresAt) {
        this.status = SeatStatus.HELD;
        this.heldByUserId = userId;
        this.holdExpiresAt = expiresAt;
    }

    /** HELD → AVAILABLE (본인 취소). */
    public void releaseHold() {
        this.status = SeatStatus.AVAILABLE;
        this.heldByUserId = null;
        this.holdExpiresAt = null;
    }

    /** HELD → SOLD (결제 완료). DB CHECK 제약상 SOLD 상태에서는 선점 정보를 비워야 한다. */
    public void markSold() {
        this.status = SeatStatus.SOLD;
        this.heldByUserId = null;
        this.holdExpiresAt = null;
    }

    private boolean isHoldExpired(Instant now) {
        return holdExpiresAt != null && !holdExpiresAt.isAfter(now);
    }
}
