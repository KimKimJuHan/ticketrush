package io.github.kimkimjuhan.ticketrush.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 (application.yml의 ticketrush.jwt).
 *
 * <p>비밀키가 짧으면 무차별 대입으로 서명을 위조할 수 있다. HS256은 32바이트 이상이 필요하므로,
 * 조건을 만족하지 않으면 애플리케이션이 아예 시작하지 않게 한다(잘못된 설정으로 운영되는 것보다 낫다).
 */
@ConfigurationProperties("ticketrush.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "ticketrush.jwt.secret(환경변수 JWT_SECRET)은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofHours(1);
        }
    }

    public byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
