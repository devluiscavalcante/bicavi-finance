# Imagem do frontend em dois estágios:
#  1. build: Node gera os arquivos estáticos (dist/), incluindo o service worker
#  2. runtime: nginx serve esses arquivos e repassa /api para o backend.
#     Node não existe na imagem final: em produção o frontend é só HTML/JS/CSS.
#
# Imagens pela tag (24-alpine, stable-alpine), e não pelo digest: assim o
# "build --pull" traz as correções de segurança do nginx (ver DEPLOY.md, seção 8).

# ---------- 1. build ----------
FROM node:24-alpine AS build
WORKDIR /app

# Mesmo truque de cache do backend: dependências antes do código.
COPY package.json package-lock.json ./
# npm ci: instala EXATAMENTE as versões do package-lock (build reproduzível).
# --ignore-scripts: dependência comprometida não roda código na instalação
# (ver o comentário no .github/workflows/ci.yml).
RUN npm ci --ignore-scripts

COPY . .
RUN npm run build

# ---------- 2. runtime ----------
# Variante "unprivileged" da própria NGINX Inc.: o nginx roda como usuário comum
# (uid 101), e não como root. Se for invadido, o invasor não é root no container.
# Por isso escuta na porta 8080: portas abaixo de 1024 exigem root.
FROM nginxinc/nginx-unprivileged:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
