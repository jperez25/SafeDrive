# Built from the repo root after the load module is compiled and its classpath copied (platform/scripts/load.sh does both):
#   ./mvnw -pl integration-tests/load -am install -DskipTests
#   ./mvnw -pl integration-tests/load dependency:copy-dependencies -DincludeScope=test -DoutputDirectory=target/classpath
FROM amazoncorretto:25
# tar: `kubectl cp` needs it in the container to copy the report out.
RUN (dnf install -y tar gzip || yum install -y tar gzip) && (dnf clean all || yum clean all)
WORKDIR /load
COPY integration-tests/load/target/classpath /load/lib
COPY integration-tests/load/target/test-classes /load/classes
COPY integration-tests/load/run.sh /load/run.sh
RUN chmod +x /load/run.sh
USER nobody
ENTRYPOINT ["/load/run.sh"]
