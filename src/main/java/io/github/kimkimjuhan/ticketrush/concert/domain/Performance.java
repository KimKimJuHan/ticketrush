package io.github.kimkimjuhan.ticketrush.concert.domain;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** 공연의 회차(특정 날짜·시간의 공연). 테이블 정의는 V1__init_schema.sql의 performances. */
@Entity
@Table(name = "performances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: 회차만 필요할 때 공연까지 매번 조회하지 않는다 (불필요한 쿼리 방지)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concert_id", nullable = false)
    private Concert concert;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "booking_opens_at", nullable = false)
    private Instant bookingOpensAt;

    // DB 타입이 SMALLINT라 short로 맞춘다. int로 두면 스키마 검증(validate)에서 시작이 실패한다
    @Column(name = "max_seats_per_user", nullable = false)
    private short maxSeatsPerUser;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
