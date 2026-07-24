# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
COPY src ./src
RUN chmod +x mvnw && ./mvnw package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app


# New Relic agent
RUN apk add --no-cache curl unzip && \
    curl -L -o newrelic-java.zip https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic-java.zip && \
    unzip newrelic-java.zip -d /app/newrelic && \
    rm newrelic-java.zip && \
    apk del unzip

COPY --from=build /app/target/*.jar app.jar

RUN find /app/newrelic -name "newrelic.jar" 2>/dev/null

ENTRYPOINT ["java", \
  "-javaagent:/app/newrelic/newrelic/newrelic.jar", \
  "-Dnewrelic.config.app_name=pedisur-backend", \
  "-Dnewrelic.config.log_file_name=STDOUT", \
  "-Dnewrelic.config.transaction_tracer.enabled=true", \
  "-Dnewrelic.config.transaction_tracer.record_sql=obfuscated", \
  "-Dnewrelic.config.datastore_tracer.database_name_reporting.enabled=true", \
  "-Dnewrelic.config.datastore_tracer.instance_reporting.enabled=true", \
  "-Dnewrelic.config.class_transformer.com.newrelic.instrumentation.jdbc-hikaricp.enabled=true", \
  "-jar", "app.jar"]