package com.team.blog.interaction.application;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 하루 비밀값 (009 research R5, FR-023). 그날 처음 부르면 만들고 하루가 지나면 사라진다. 구현은 Redis {@code
 * view:salt:{yyyyMMdd}} ({@code SET NX} + TTL 26시간). 얻지 못하면(Redis 장애) 빈 값.
 */
@FunctionalInterface
public interface ViewSaltSource {

    Optional<String> salt(LocalDate date);
}
