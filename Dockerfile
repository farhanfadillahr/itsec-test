FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -Punit-only clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S itsectest && adduser -S -G itsectest itsectest

COPY --from=build /build/target/*.jar app.jar
RUN chown -R itsectest:itsectest /app
USER itsectest

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD wget --spider -q http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
