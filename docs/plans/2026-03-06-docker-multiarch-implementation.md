# Docker Multi-Arch Build CI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create a GitHub Actions workflow that builds and pushes a multi-arch Docker image (amd64 + arm64) to GHCR on pushes to `main` and version tags.

**Architecture:** Single workflow file using Docker's official GitHub Actions toolkit (QEMU + Buildx + build-push-action). Registry-based layer caching stored in GHCR. Tags managed by `docker/metadata-action`.

**Tech Stack:** GitHub Actions, Docker Buildx, QEMU, ghcr.io, `docker/metadata-action@v5`, `docker/build-push-action@v6`, `docker/setup-qemu-action@v3`, `docker/setup-buildx-action@v3`

---

### Task 1: Create the GitHub Actions workflow

**Files:**
- Create: `.github/workflows/docker-publish.yml`

**Step 1: Create the workflows directory**

```bash
mkdir -p .github/workflows
```

**Step 2: Create the workflow file**

Create `.github/workflows/docker-publish.yml` with this exact content:

```yaml
name: Build and push Docker image

on:
  push:
    branches:
      - main
    tags:
      - 'v*'

permissions:
  contents: read
  packages: write

jobs:
  build-and-push:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up QEMU
        uses: docker/setup-qemu-action@v3

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository }}
          tags: |
            type=ref,event=branch
            type=semver,pattern={{version}}
            type=semver,pattern={{major}}.{{minor}}
            type=raw,value=latest,enable={{is_default_branch}}

      - name: Build and push
        uses: docker/build-push-action@v6
        with:
          context: .
          platforms: linux/amd64,linux/arm64
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=registry,ref=ghcr.io/${{ github.repository }}:buildcache
          cache-to: type=registry,ref=ghcr.io/${{ github.repository }}:buildcache,mode=max
```

**Step 3: Verify YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/docker-publish.yml'))" && echo "YAML OK"`

Expected output: `YAML OK`

If Python yaml is unavailable, run: `cat .github/workflows/docker-publish.yml` and visually confirm indentation is consistent.

**Step 4: Commit**

```bash
git add .github/workflows/docker-publish.yml
git commit -m "ci: add multi-arch Docker build workflow for ghcr.io"
```

---

### Task 2: Verify workflow triggers are correct

**Files:**
- Read: `.github/workflows/docker-publish.yml`

**Step 1: Confirm branch trigger**

Check that `on.push.branches` contains `main`. This should fire on every push to `main` and produce a `latest` tag.

**Step 2: Confirm tag trigger**

Check that `on.push.tags` contains `v*`. A push of tag `v1.2.3` will produce image tags `1.2.3`, `1.2`, and `latest`.

**Step 3: Confirm no PR builds**

There is no `pull_request` trigger — the workflow only runs on actual pushes. This is intentional (no push to GHCR on PRs).

**Step 4: Push branch to remote to test**

```bash
git push origin main
```

Then check: `gh run list --workflow=docker-publish.yml --limit 3`

Expected: a run appears with status `in_progress` or `completed`.

**Step 5: (Optional) Test tag build**

```bash
git tag v0.1.0
git push origin v0.1.0
```

Then check: `gh run list --workflow=docker-publish.yml --limit 3`

Expected: a second run appears triggered by the tag push.

After it completes, verify image is visible: `gh api /user/packages/container/telegram-news-bot/versions --jq '.[].metadata.container.tags'`
