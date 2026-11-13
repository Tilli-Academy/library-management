# --- Build stage: produce the static files in dist/ ---
FROM node:25-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# --- Runtime stage: nginx without root rights, serving dist/ and forwarding /api to the backend ---
FROM nginxinc/nginx-unprivileged:1.29-alpine
# The template is filled in from environment variables at startup (only PAGETURN_* names are replaced).
COPY default.conf.template /etc/nginx/templates/default.conf.template
ENV NGINX_ENVSUBST_FILTER=PAGETURN_
ENV PAGETURN_TRUSTED_PROXY_CIDR=127.0.0.1/32
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080
