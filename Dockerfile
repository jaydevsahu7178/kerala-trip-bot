FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy compiled class or source
COPY TelegramTripBot.java .

RUN apk add --no-cache openjdk21-jdk && \
    javac TelegramTripBot.java && \
    apk del openjdk21-jdk

EXPOSE 8080

CMD ["java", "TelegramTripBot"]
