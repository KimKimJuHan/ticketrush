package io.github.kimkimjuhan.ticketrush.common.security;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** 로그인에 성공한 사용자에게 액세스 토큰(JWT)을 발급한다. */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public IssuedToken issue(Long userId, String role) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.ISSUER)
                .subject(String.valueOf(userId)) // 사용자 식별은 이 값으로만 한다 (N5)
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim("role", role)
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, properties.accessTokenTtl().toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
