package io.github.kimkimjuhan.ticketrush.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 인증 설정.
 *
 * <ul>
 *   <li>세션 없이(stateless) 매 요청의 {@code Authorization: Bearer <JWT>}로 사용자를 식별한다.
 *       서버가 로그인 상태를 기억하지 않으므로 서버를 여러 대로 늘려도 그대로 동작한다(N4).
 *   <li>CSRF 보호는 끈다. 쿠키가 아니라 헤더로 토큰을 보내므로 CSRF 공격이 성립하지 않는다.
 *   <li>인증 실패 응답도 다른 에러와 같은 형식(RFC 9457 + code)이 되도록 GlobalExceptionHandler로 넘긴다.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    static final String ISSUER = "ticketrush";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {

        AuthenticationEntryPoint entryPoint =
                (request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex);
        AccessDeniedHandler accessDeniedHandler =
                (request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex);

        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/concerts/**", "/api/v1/performances/*/seats").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        // 지표는 인증 없이 수집하되, 운영에서는 네트워크(보안그룹)로 내부 접근만 허용한다
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // 만료 시각 + 발급자(우리 서버가 발급한 토큰인지)를 함께 검사한다
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    /** {bcrypt}...처럼 알고리즘 이름을 함께 저장해, 나중에 해시 방식을 바꿔도 기존 비밀번호가 동작하게 한다. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static SecretKey secretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secretBytes(), "HmacSHA256");
    }
}
