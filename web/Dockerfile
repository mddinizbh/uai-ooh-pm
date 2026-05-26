# ── Stage 1: build ────────────────────────────────────────────────────────────
# Builds the Vite SPA into static assets under /app/dist.
FROM node:22-alpine AS build
WORKDIR /app

# Dependency cache layer: invalidated only when lock-file changes.
COPY package*.json ./
RUN npm ci --silent

# Build (tsc -b && vite build) — output lands in /app/dist
COPY . .
RUN npm run build

# ── Stage 2: runtime (Nginx static) ───────────────────────────────────────────
FROM nginx:1.27-alpine

# Custom config: SPA routing + asset caching + health probe
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Static assets built in stage 1
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
