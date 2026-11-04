FROM node:26-bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends time procps && rm -rf /var/lib/apt/lists/* \
 && useradd --uid 1001 --no-create-home --shell /usr/sbin/nologin sandbox
COPY runner.sh /judge/runner.sh
COPY language/javascript.sh /judge/language.sh
COPY wrapper/javascript/ /judge/wrapper/
RUN chmod 755 /judge/runner.sh
ENTRYPOINT ["/judge/runner.sh"]
