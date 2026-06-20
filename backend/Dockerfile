# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Corporate TLS proxies (MITM) make Maven fail to download dependencies.
# Drop your company's root CA(s) as .crt (PEM) files into backend/certs/ and they
# are imported into the JVM truststore here. If certs/ only contains .gitkeep,
# this is a no-op (works on open networks / the server).
COPY certs/ /tmp/certs/
RUN for c in /tmp/certs/*.crt; do \
      [ -f "$c" ] || continue; \
      keytool -importcert -noprompt -trustcacerts \
        -alias "corp-$(basename "$c")" -file "$c" \
        -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit || true; \
    done

COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

# ---- run stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
