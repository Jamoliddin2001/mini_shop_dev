# Multi-stage build for the React/Vite frontend.
# Stage 1 builds the static bundle with Node; stage 2 serves it with nginx, which also
# reverse-proxies /api to the backend so the browser only ever talks to a single origin.

# ---- Stage 1: build ----
FROM node:22-alpine AS build
WORKDIR /app

# Install dependencies from the lockfile first for a cached, reproducible layer.
COPY package.json package-lock.json ./
RUN npm ci

# Same-origin API access: the bundle calls a RELATIVE base URL, and nginx (stage 2)
# proxies /api to the backend. This avoids baking a backend host into the image and
# sidesteps CORS entirely (browser stays on one origin).
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

COPY . .
RUN npm run build

# ---- Stage 2: serve ----
FROM nginx:alpine AS runtime

# SPA + /api reverse-proxy configuration.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Static assets produced by `vite build`.
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

# nginx:alpine already logs access/error to stdout/stderr and runs CMD ["nginx", "-g", "daemon off;"].
