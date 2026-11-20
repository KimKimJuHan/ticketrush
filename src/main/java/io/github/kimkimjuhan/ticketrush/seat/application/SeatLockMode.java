package io.github.kimkimjuhan.ticketrush.seat.application;

/**
 * 좌석 상태를 바꾸기 전에 좌석 행을 어떻게 읽을지 (application.yml의 ticketrush.hold.locking).
 *
 * <p>운영 기본값은 {@link #PESSIMISTIC}이다. {@link #NONE}은 부하 테스트에서 기준선(ADR-0004)을 같은 코드로
 * 다시 재현해 비교하기 위해서만 남겨 둔다 (ADR-0007).
 */
public enum SeatLockMode {

    /** 잠금 없이 읽는다. 동시 요청 시 선점이 덮어써질 수 있다 (Lost Update). 실험용. */
    NONE,

    /** {@code SELECT ... FOR UPDATE}로 행을 잠근 뒤 읽는다. 같은 좌석을 바꾸려는 다른 트랜잭션은 커밋까지 기다린다. */
    PESSIMISTIC
}
