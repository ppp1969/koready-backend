FROM eclipse-temurin:25-jdk-noble AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew \
    && ./gradlew dependencies --configuration runtimeClasspath --no-daemon --quiet

COPY src ./src
RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:25-jre-noble

RUN groupadd --system koready \
    && useradd --system --gid koready --home-dir /app --shell /usr/sbin/nologin koready

WORKDIR /app
COPY --from=builder --chown=koready:koready /workspace/build/libs/app.jar ./app.jar

USER koready

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
