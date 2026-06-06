FROM dhi.io/maven:3-jdk25-alpine-dev AS build

WORKDIR /build

COPY pom.xml ./pom.xml

RUN mvn dependency:go-offline

COPY src ./src

RUN mvn package -DskipTests

FROM dhi.io/amazoncorretto:21-alpine as final

ARG JAR_FILE=/build/target/*.jar

COPY --from=build ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
