FROM eclipse-temurin:25-jdk-noble
RUN apt-get update && apt-get install -y --no-install-recommends time procps && rm -rf /var/lib/apt/lists/* \
 && useradd --uid 1001 --no-create-home --shell /usr/sbin/nologin sandbox
COPY runner.sh /judge/runner.sh
COPY language/java.sh /judge/language.sh
COPY wrapper/java/ /judge/wrapper/
RUN chmod 755 /judge/runner.sh
ENTRYPOINT ["/judge/runner.sh"]
