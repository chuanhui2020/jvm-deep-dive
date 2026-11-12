FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -B -ntp verify

FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY --from=build /workspace/target/lab.jar /app/lab.jar
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx128m -XX:+UseG1GC -XX:ActiveProcessorCount=1 -XX:NativeMemoryTracking=summary -Djava.io.tmpdir=/tmp -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/data/oom.hprof -Xlog:gc*=debug,class+unload=info:file=/data/gc.log:uptime,level,tags:filecount=3,filesize=20M"
USER 10001:10001
ENTRYPOINT ["java", "-cp", "/app/lab.jar", "dev.jvm.labs.PodLauncher"]
CMD ["fault", "30", "2"]
