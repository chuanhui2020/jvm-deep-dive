FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -B -ntp verify

FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY --from=build /workspace/target/lab.jar /app/lab.jar
ENV JAVA_TOOL_OPTIONS="-Xmx64m -Djdk.virtualThreadScheduler.parallelism=1 -Djdk.virtualThreadScheduler.maxPoolSize=1 -XX:NativeMemoryTracking=summary -Djava.io.tmpdir=/tmp"
USER 10001:10001
ENTRYPOINT ["java", "-cp", "/app/lab.jar", "dev.jvm.labs.PodLauncher"]
CMD []
