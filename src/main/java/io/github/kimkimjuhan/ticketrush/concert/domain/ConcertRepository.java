package io.github.kimkimjuhan.ticketrush.concert.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConcertRepository extends JpaRepository<Concert, Long> {
}
