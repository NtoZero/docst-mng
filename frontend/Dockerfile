FROM node:20-alpine
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install
COPY . .

# Next.js NEXT_PUBLIC_* 환경변수는 빌드/런타임 시 .env.local에서 읽음
ARG NEXT_PUBLIC_API_BASE=http://localhost:8342
RUN echo "NEXT_PUBLIC_API_BASE=${NEXT_PUBLIC_API_BASE}" > .env.local

EXPOSE 3000
CMD ["npm", "run", "dev", "--", "-H", "0.0.0.0"]
