# Build stage: static Vite build. VITE_GRAPHQL_URL is baked in at build time
# (Vite env vars are compile-time, not runtime) — docker-compose.yml passes
# it as a plain environment var here for local `docker build`/`docker-compose
# build` convenience; a real deployment would pass it as a build arg instead.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_GRAPHQL_URL=http://localhost:8080/graphql
ENV VITE_GRAPHQL_URL=${VITE_GRAPHQL_URL}
RUN npm run build

# Runtime stage: static file serving only. The unprivileged nginx image
# runs as the nginx user (UID 101) and listens on 8080, not 80.
FROM nginxinc/nginx-unprivileged:alpine
# The package upgrade needs root. USER 101 below drops it again.
USER 0
# The published base image often lags behind Alpine's security index. For
# example, it shipped libcrypto3 3.5.7-r0 (CVE-2026-14456, fixed in 3.5.8-r0),
# brotli-libs 1.2.0-r1 (CVE-2026-111497, fixed in 1.2.1-r0) and zlib 1.3.2-r0
# (CVE-2026-85091, fixed in 1.3.2-r1). Upgrade all packages, so each new
# fix applies without a change to this file.
RUN apk upgrade --no-cache
COPY --from=build /app/dist /usr/share/nginx/html
USER 101
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD ["wget", "-q", "--spider", "http://127.0.0.1:8080/"]
