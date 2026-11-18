# syntax=docker/dockerfile:1
# =============================================================================
# TicketRush 애플리케이션 이미지 (멀티 스테이지 빌드)
#   1단계 build  : JDK로 소스를 빌드해 실행 가능한 jar를 만든다
#   2단계 runtime: JRE만 있는 가벼운 이미지에 jar만 복사한다 (빌드 도구·소스는 포함하지 않음)
# =============================================================================

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 빌드 설정을 소스보다 먼저 복사한다. 의존성이 바뀌지 않으면 이 레이어를 캐시에서 재사용해 빌드가 빨라진다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# 테스트는 CI가 이미 실행했으므로 이미지 빌드에서는 생략한다
RUN ./gradlew --no-daemon bootJar -x test


FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# root가 아닌 전용 사용자로 실행한다. 컨테이너가 탈취돼도 피해 범위를 줄이기 위한 기본 수칙
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build --chown=app:app /workspace/build/libs/app.jar app.jar
USER app

EXPOSE 8080

# 컨테이너 메모리 제한의 75%를 힙으로 쓴다. 고정 -Xmx 대신 비율로 두면 메모리 설정만 바꿔도 따라간다.
# 메모리가 부족하면 어정쩡하게 살아 있지 않고 즉시 종료해 오케스트레이터가 재시작하게 한다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
