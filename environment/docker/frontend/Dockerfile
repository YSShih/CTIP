# syntax=docker/dockerfile:1.7
# build context = repo root (CTIP/)
# 唯一的 frontend Dockerfile(docs/spec/05-environment.md §5.3,強制契約)。
# 禁止 Dockerfile.<env>;行為差異以 multi-stage build + target 區分。

########## development ##########
FROM node:24-alpine AS development
WORKDIR /workspace
EXPOSE 5173
# 原始碼由 bind mount 提供於 /workspace
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]

########## build ##########
FROM node:24-alpine AS build
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

########## production ##########
FROM nginx:1.30-alpine AS production
COPY --from=build /src/dist /usr/share/nginx/html
COPY environment/config/nginx/default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
# nginx:alpine 內含 busybox 的 wget,故用 wget 而非 curl
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s \
  CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1
