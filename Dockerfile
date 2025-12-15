# Build the project
FROM adoptopenjdk/maven-openjdk11 AS build
WORKDIR /build
COPY . .
RUN mvn -q clean package -DskipTests

# Production image
FROM eclipse-temurin:11-alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /ola-hd
COPY --from=build /build/target/olahd*.jar app.jar
ENV OLA_HD_MIN_MEMORY=6G
ENV OLA_HD_MAX_MEMORY=6G
ENV OLA_HD_PORT=8080
EXPOSE ${OLA_HD_PORT}
CMD java \
	-Xms${OLA_HD_MIN_MEMORY} \
	-Xmx${OLA_HD_MAX_MEMORY} \
	-Dserver.port=${OLA_HD_PORT} \
	-Dspring.config.location=/config/ \
	-jar app.jar
