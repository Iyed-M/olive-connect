# Dev shortcuts. Run `just` to list recipes.

compose_dev := "docker compose -f deploy/docker-compose.dev.yml"

# Port of the API on the host. Read by the API (server.port) and by the Vite proxy.
# Change it when 8080 is taken: `API_PORT=8081 just dev`
export API_PORT := env("API_PORT", "8080")

default:
    @just --list

# Start MongoDB + Redis, then the API and the frontend (Ctrl+C stops both)
dev: infra
    #!/usr/bin/env bash
    trap 'kill 0' EXIT
    (cd backend && ./mvnw spring-boot:run) &
    (cd frontend && pnpm dev) &
    wait

# Start MongoDB + Redis in the background and wait until they are healthy
infra:
    {{compose_dev}} up -d --wait

# Run only the API on :API_PORT
api:
    cd backend && ./mvnw spring-boot:run

# Run only the frontend on :5173 (proxies /api to :API_PORT)
web:
    cd frontend && pnpm dev

# Stop MongoDB + Redis (data volumes are kept)
down:
    {{compose_dev}} down
