# Docker Multi-Arch Build CI Design

**Date:** 2026-03-06

## Overview

GitHub Actions workflow to build and push a multi-architecture Docker image to GitHub Container Registry (ghcr.io).

## Trigger

- Push to `main` branch — builds and pushes image tagged `latest`
- Push of version tag matching `v*` — builds and pushes image tagged with the version (e.g., `v1.2.0`) and `latest`

## Workflow

Single file: `.github/workflows/docker-publish.yml`

Single job: `build-and-push` on `ubuntu-latest`.

### Steps

1. Checkout code
2. Set up QEMU — enables ARM emulation on the x86 GitHub-hosted runner
3. Set up Docker Buildx — enables multi-platform builds
4. Log in to GHCR using `secrets.GITHUB_TOKEN` (no manual secret required)
5. Extract metadata — uses `docker/metadata-action` to derive image tags and labels from git context
6. Build and push — `docker/build-push-action` targeting `linux/amd64,linux/arm64`, with GHCR-backed layer cache

### Image Naming

`ghcr.io/<owner>/<repo>:<tag>` — derived automatically from the GitHub repository context.

`metadata-action` handles tag logic:
- `latest` on pushes to `main`
- Semantic version tag (e.g., `v1.2.0`, `1.2.0`, `1.2`) on version tag pushes

### Caching

Registry-based cache stored in GHCR as a separate tag (`buildcache`):

```
cache-from: type=registry,ref=ghcr.io/<owner>/<repo>:buildcache
cache-to: type=registry,ref=ghcr.io/<owner>/<repo>:buildcache,mode=max
```

`mode=max` caches all layers (including intermediate), maximizing cache hits on subsequent builds.

## Permissions

Workflow sets:
```yaml
permissions:
  contents: read
  packages: write
```

`packages: write` is required to push to GHCR. `GITHUB_TOKEN` is used automatically — no manual secrets needed.

## Architecture

- `linux/amd64` — standard x86-64 (cloud VMs, most servers)
- `linux/arm64` — 64-bit ARM (Apple Silicon, Raspberry Pi 4+, AWS Graviton)

QEMU emulation handles the ARM build on GitHub's x86 runners. This is slower than native (~3-5x) but requires no self-hosted infrastructure.
