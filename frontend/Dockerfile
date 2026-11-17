# 운영용 프론트 + 리버스 프록시 이미지. 빌드 컨텍스트는 frontend/ (compose.prod.yaml).
# 프론트는 빌드하면 정적 파일뿐이라 별도 서버 없이 Caddy 가 직접 내려준다. Caddyfile 은 deploy/Caddyfile 을 볼륨으로 연결한다.

FROM node:22-alpine AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@10.34.9 --activate
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM caddy:2.10-alpine
COPY --from=build /workspace/dist /srv
