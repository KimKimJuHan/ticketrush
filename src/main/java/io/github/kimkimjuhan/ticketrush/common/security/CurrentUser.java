package io.github.kimkimjuhan.ticketrush.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 인증된 요청에서 사용자 ID를 꺼낸다.
 *
 * <p>요구사항 N5: 사용자 ID는 요청 본문이나 쿼리가 아니라 서버가 서명을 검증한 토큰에서만 얻는다.
 * (졸업작품에서 요청 본문의 userId를 믿어 다른 사람 기준으로 조회할 수 있던 문제를 반복하지 않기 위해)
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
