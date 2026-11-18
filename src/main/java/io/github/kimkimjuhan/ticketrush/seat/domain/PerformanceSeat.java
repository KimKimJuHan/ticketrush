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
            case HELD -> holdExpiresAt != null && !holdExpiresAt.isAfter(now);
            case SOLD -> false;
        };
    }
}
