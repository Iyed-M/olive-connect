# ---- Stage 1: build the static files with Node ----
FROM node:24-alpine AS build
WORKDIR /app

# Install the exact pnpm version the project pins in package.json.
RUN npm install -g pnpm@12.4.1

# Install dependencies first (cached until the lockfile changes).
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# Copy the sources and build into /app/dist.
COPY . .
RUN pnpm build

# ---- Stage 2: serve the built files with nginx ----
FROM nginx:1.27-alpine

# nginx serves whatever is in this folder.
COPY --from=build /app/dist /usr/share/nginx/html

# Our config replaces the default nginx site.
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
