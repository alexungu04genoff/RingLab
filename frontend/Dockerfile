FROM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_GOOGLE_CLIENT_ID
RUN test -n "$VITE_GOOGLE_CLIENT_ID" && npm run build

FROM caddy:2-alpine
COPY --from=build /build/dist/ /srv/
COPY Caddyfile /etc/caddy/Caddyfile
