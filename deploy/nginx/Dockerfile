# The production web image keeps the frontend build identical to frontend/Dockerfile
# and adds OpenSSL so a local compose run can create a short-lived self-signed cert.
FROM node:24-alpine AS build

WORKDIR /app
RUN npm install -g pnpm@12.4.1
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:1.27.3-alpine
RUN apk add --no-cache openssl \
    && rm -f /var/log/nginx/access.log /var/log/nginx/error.log \
    && mkdir -p /var/log/nginx /etc/nginx/tls

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80 443
