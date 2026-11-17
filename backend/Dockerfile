# 운영용 백엔드 이미지. 빌드 컨텍스트는 backend/ (compose.prod.yaml).
# 서버(arm64)에서 직접 빌드하므로 멀티 아키텍처 빌드 설정이 필요 없다.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
# 의존성 정의만 먼저 복사해 소스가 바뀌어도 의존성 다운로드 층은 캐시를 재사용한다
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*-SNAPSHOT.jar app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
