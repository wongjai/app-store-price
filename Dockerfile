# Stage 1: Maven build
FROM maven:3.9-amazoncorretto-21-alpine AS build
WORKDIR /src
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM amazoncorretto:21-alpine

ENV LANG=C.UTF-8
ENV TZ=Asia/Hong_Kong

RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/${TZ} /etc/localtime && \
    echo "${TZ}" > /etc/timezone && \
    apk del tzdata && \
    rm -rf /var/cache/apk/*

WORKDIR /app
EXPOSE 8080

COPY --from=build /src/target/app-store-price-*.jar ./app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
