# Build context is the pte-web repo root (see docker-compose.deploy.yml), because
# the two apps are pnpm workspace members and cannot be built in isolation from it.
#
# Deliberately single-stage. Splitting build/runtime would mean copying a pnpm
# workspace's symlinked node_modules across stages, which is fragile; the extra
# image size is irrelevant on a 200GB boot volume. The standalone-output
# optimisation is the thing to reach for if that ever stops being true — it needs
# `output: 'standalone'` plus `outputFileTracingRoot` in each app's next.config,
# which is an app-code change and out of scope for deployment.
FROM node:22-bookworm-slim

WORKDIR /workspace
RUN corepack enable

COPY . .
RUN pnpm install --frozen-lockfile

# NEXT_PUBLIC_* is inlined into the client bundle at BUILD time, not read at
# runtime — passing it as a compose `environment` entry would silently leave the
# bundle pointing at the localhost:8080 default in features/auth/constants.ts.
ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}

ARG APP
ENV APP=${APP}
ENV NODE_OPTIONS=--max-old-space-size=2048
RUN pnpm --filter ${APP} build

ENV NODE_ENV=production
ENV PORT=3000
EXPOSE 3000

# APP must come through ENV, not ARG — build args do not exist at container start.
CMD ["sh", "-c", "pnpm --filter $APP start"]
