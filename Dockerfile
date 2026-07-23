# Build stage
FROM public.ecr.aws/docker/library/gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN ./gradlew build -x test

# Run stage
FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]