FROM eclipse-temurin:25-jdk-noble
RUN apt-get update && apt-get install -y --no-install-recommends time procps && rm -rf /var/lib/apt/lists/* \
 && useradd --uid 1001 --no-create-home --shell /usr/sbin/nologin sandbox
COPY wrapper/java/ /tmp/wrapper-source/
RUN mkdir -p /tmp/wrapper-classes /judge/wrapper \
 && javac -encoding UTF-8 -d /tmp/wrapper-classes /tmp/wrapper-source/*.java \
 && jar --create --file /judge/wrapper/wrapper.jar -C /tmp/wrapper-classes . \
 && rm -rf /tmp/wrapper-source /tmp/wrapper-classes
# 채점 때와 같은 명령으로 한 번 돌려 javac·java 가 시작할 때 읽는 클래스를 AOT 캐시로 남긴다.
COPY aot-training/ /tmp/aot-training/
RUN cd /tmp/aot-training \
 && javac -J-XX:+UseSerialGC -J-XX:AOTCacheOutput=/judge/javac.aot -encoding UTF-8 -cp /judge/wrapper/wrapper.jar -d build Solution.java \
 && java -XX:+UseSerialGC -XX:AOTCacheOutput=/judge/java.aot -cp /judge/wrapper/wrapper.jar Main < input.json \
 && rm -rf /tmp/aot-training
COPY runner.sh /judge/runner.sh
COPY language/java.sh /judge/language.sh
RUN chmod 755 /judge/runner.sh
ENTRYPOINT ["/judge/runner.sh"]
