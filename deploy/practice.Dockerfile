FROM node:22-bookworm-slim

WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
# Build-time flag that shows the AI chat panel; keep it false until the backend chat is enabled.
ARG NEXT_PUBLIC_CHAT_ENABLED=false
ENV NEXT_PUBLIC_CHAT_ENABLED=${NEXT_PUBLIC_CHAT_ENABLED}
ENV NEXT_TELEMETRY_DISABLED=1
ENV NODE_OPTIONS=--max-old-space-size=2048
RUN npm run build
RUN chown -R node:node .next

ENV NODE_ENV=production
ENV PORT=3000
EXPOSE 3000
USER node
CMD ["npm", "run", "start", "--", "--hostname", "0.0.0.0"]
