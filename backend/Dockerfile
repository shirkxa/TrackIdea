# syntax=docker/dockerfile:1

# --- Build ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Najpierw tylko wrapper i pom.xml, żeby zależności Mavena trafiały do cache warstw
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# --- Runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app
# /data – trwała baza (plik H2); podłącz wolumen, żeby dane przeżyły wymianę kontenera
RUN useradd --system --uid 10001 --no-create-home spring \
    && mkdir /data && chown spring /data
VOLUME /data
USER spring

COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./

EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75" \
    SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
