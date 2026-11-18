package io.github.kimkimjuhan.ticketrush.reservation.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** 멱등 재요청 처리용. 항목까지 한 번에 가져와 N+1을 피한다. */
    @EntityGraph(attributePaths = "items")
    Optional<Reservation> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
