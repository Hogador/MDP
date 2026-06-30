FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN gradle dependencies --no-daemon --quiet 2>/dev/null || true
COPY src ./src
RUN gradle installDist --no-daemon

FROM eclipse-temurin:21-jre
RUN groupadd -r appuser && useradd -r -g appuser -d /app -s /sbin/nologin appuser
WORKDIR /app
COPY --from=build /app/build/install/mdaopay-paymaster .
RUN chown -R appuser:appuser /app
USER appuser:appuser
EXPOSE 8080
ENTRYPOINT ["./bin/mdaopay-paymaster"]