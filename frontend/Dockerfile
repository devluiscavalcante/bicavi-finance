# Imagem do frontend em dois estágios:
#  1. build: Node gera os arquivos estáticos (dist/), incluindo o service worker
#  2. runtime: nginx serve esses arquivos e repassa /api para o backend.
#     Node não existe na imagem final: em produção o frontend é só HTML/JS/CSS.

# ---------- 1. build ----------
FROM node:24-alpine AS build
WORKDIR /app

# Mesmo truque de cache do backend: dependências antes do código.
COPY package.json package-lock.json ./
# npm ci: instala EXATAMENTE as versões do package-lock (build reproduzível).
RUN npm ci

COPY . .
RUN npm run build

# ---------- 2. runtime ----------
FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
