# ===== Build Stage =====
FROM node:22-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm cache clean --force && npm install --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production --output-path=dist/frontend

# ===== Serve Stage =====
FROM nginx:alpine
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
# Nginx config will be mounted from docker-compose
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
