FROM eclipse-temurin:21-jdk

WORKDIR /app

# Copy ONLY the server subfolder
COPY TP-Integrador/server/ .

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies (optional, speeds up later runs)
RUN ./gradlew --no-daemon dependencies || true

# Run the app
CMD ["./gradlew", "run", "--no-daemon"]
