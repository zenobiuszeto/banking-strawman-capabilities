---
name: github-actions-cicd
description: |
  **GitHub Actions CI/CD Skill**: Production-grade CI/CD pipelines for Java 21 + Spring Boot 3.x banking platform. Covers multi-job CI workflows (compile, unit tests, integration tests, security scan, Docker build), CD deployment pipelines (staging → approval → production), environment secrets, caching strategies, matrix builds, release automation, and rollback workflows.

  MANDATORY TRIGGERS: GitHub Actions, workflow, .github/workflows, ci.yml, cd.yml, on: push, on: pull_request, jobs, steps, uses, actions/checkout, actions/setup-java, gradle, ./gradlew, docker/build-push-action, docker/login-action, GHCR, ghcr.io, ECR, GCR, docker/metadata-action, trivy-action, aquasecurity/trivy, OWASP, dependency check, SonarQube, JaCoCo, artifacts, upload-artifact, secrets, environment, deployment, workflow_dispatch, needs, if: condition, matrix, concurrency, cache, self-hosted runner, Slack notification, release drafter, semantic versioning, tag, rollback.
---

# GitHub Actions CI/CD Skill — Banking Platform

You are writing CI/CD workflows for the **Java 21 / Spring Boot 3.3+ banking platform**. Workflows live in `.github/workflows/` and follow the project's existing `ci.yml` / `cd.yml` pattern.

**Pipeline stages**: Compile → Unit Tests → Integration Tests → Security Scan → Docker Build → Staging Deploy → Manual Approval → Production Deploy

---

## CI Workflow — Full Reference (`ci.yml`)

```yaml
name: CI — Build, Test & Scan

on:
  push:
    branches: [ "main", "develop", "release/**", "feature/**" ]
  pull_request:
    branches: [ "main", "develop" ]

# Cancel in-progress runs for the same branch (saves minutes)
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

env:
  JAVA_VERSION: "21"
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  # ────────────────────────────────────────────────────────────
  # 1. Compile + Static Analysis
  # ────────────────────────────────────────────────────────────
  compile:
    name: Compile & Lint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK ${{ env.JAVA_VERSION }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: temurin
          cache: gradle                # Cache Gradle wrapper + dependencies

      - name: Gradle compile
        run: ./gradlew compileJava compileTestJava --no-daemon --parallel

      - name: Checkstyle
        run: ./gradlew checkstyleMain checkstyleTest --no-daemon
        continue-on-error: true

      - name: SpotBugs
        run: ./gradlew spotbugsMain --no-daemon
        continue-on-error: true

  # ────────────────────────────────────────────────────────────
  # 2. Unit Tests + Coverage
  # ────────────────────────────────────────────────────────────
  unit-tests:
    name: Unit Tests
    runs-on: ubuntu-latest
    needs: compile
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: temurin
          cache: gradle

      - name: Run unit tests with JaCoCo
        run: ./gradlew test jacocoTestReport jacocoTestCoverageVerification --no-daemon

      - name: Publish test results
        uses: EnricoMi/publish-unit-test-result-action@v2
        if: always()
        with:
          files: build/test-results/test/**/*.xml
          comment_mode: always

      - name: Upload test reports
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: unit-test-reports-${{ github.sha }}
          path: |
            build/reports/tests/test/
            build/reports/jacoco/
          retention-days: 14

  # ────────────────────────────────────────────────────────────
  # 3. Integration Tests (Testcontainers)
  # ────────────────────────────────────────────────────────────
  integration-tests:
    name: Integration Tests
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: temurin
          cache: gradle

      - name: Run integration tests
        run: ./gradlew integrationTest --no-daemon
        env:
          TESTCONTAINERS_RYUK_DISABLED: "false"
          # Testcontainers pulls Docker images — ensure Docker is available
          DOCKER_HOST: unix:///var/run/docker.sock

      - name: Upload integration test reports
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: integration-test-reports-${{ github.sha }}
          path: build/reports/tests/integrationTest/
          retention-days: 14

  # ────────────────────────────────────────────────────────────
  # 4. Security — OWASP + SAST
  # ────────────────────────────────────────────────────────────
  security-scan:
    name: Security Scan
    runs-on: ubuntu-latest
    needs: compile
    permissions:
      security-events: write    # Required for SARIF upload
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: temurin
          cache: gradle

      - name: OWASP Dependency Check
        run: ./gradlew dependencyCheckAnalyze --no-daemon
        continue-on-error: true   # Report, don't block (unless CVE threshold is set)
        env:
          NVD_API_KEY: ${{ secrets.NVD_API_KEY }}

      - name: Upload OWASP report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: owasp-report-${{ github.sha }}
          path: build/reports/dependency-check-report.html

      - name: Run Trivy filesystem scan (source code)
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: fs
          scan-ref: .
          format: sarif
          output: trivy-fs-results.sarif
          severity: CRITICAL,HIGH

      - name: Upload Trivy SARIF
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: trivy-fs-results.sarif

  # ────────────────────────────────────────────────────────────
  # 5. Build & Push Docker Image → GHCR
  # ────────────────────────────────────────────────────────────
  build-image:
    name: Build & Push Docker Image
    runs-on: ubuntu-latest
    needs: [ unit-tests, integration-tests ]
    if: github.event_name != 'pull_request'   # Don't push on PRs
    permissions:
      contents: read
      packages: write
    outputs:
      image-digest: ${{ steps.push.outputs.digest }}
      image-tag:    ${{ steps.meta.outputs.tags }}
      short-tag:    sha-${{ github.sha }}
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: temurin
          cache: gradle

      - name: Build JAR
        run: ./gradlew bootJar -x test --no-daemon --parallel

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract Docker metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=ref,event=branch
            type=semver,pattern={{version}}
            type=semver,pattern={{major}}.{{minor}}
            type=sha,prefix=sha-
            type=raw,value=latest,enable=${{ github.ref == 'refs/heads/main' }}
          labels: |
            org.opencontainers.image.title=Banking Platform
            org.opencontainers.image.vendor=Banking Corp

      - name: Build and push multi-arch image
        id: push
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags:      ${{ steps.meta.outputs.tags }}
          labels:    ${{ steps.meta.outputs.labels }}
          platforms: linux/amd64,linux/arm64
          cache-from: type=gha
          cache-to:   type=gha,mode=max
          provenance: true   # Generate SLSA provenance attestation

  # ────────────────────────────────────────────────────────────
  # 6. Container Scan (post-push)
  # ────────────────────────────────────────────────────────────
  container-scan:
    name: Container Vulnerability Scan
    runs-on: ubuntu-latest
    needs: build-image
    permissions:
      security-events: write
    steps:
      - name: Run Trivy container scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:sha-${{ github.sha }}
          format: sarif
          output: trivy-image.sarif
          severity: CRITICAL,HIGH
          exit-code: "1"         # Fail CI on CRITICAL/HIGH CVEs in final image

      - uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: trivy-image.sarif
```

---

## CD Workflow — Full Reference (`cd.yml`)

```yaml
name: CD — Deploy to Kubernetes

on:
  push:
    branches: [ "main" ]
  workflow_dispatch:
    inputs:
      environment:
        description: Target environment
        type: choice
        options: [ staging, production ]
        default: staging
      image-tag:
        description: Image tag (default = sha of HEAD)

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}
  K8S_NAMESPACE: banking

jobs:
  deploy-staging:
    name: Deploy → Staging
    runs-on: ubuntu-latest
    environment:
      name: staging
      url: https://staging.bankingplatform.com
    steps:
      - uses: actions/checkout@v4

      - name: Resolve image tag
        id: tag
        run: echo "tag=${{ github.event.inputs.image-tag || format('sha-{0}', github.sha) }}" >> $GITHUB_OUTPUT

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id:     ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region:            ${{ secrets.AWS_REGION }}

      - name: Update kubeconfig (EKS staging)
        run: aws eks update-kubeconfig --name banking-platform-staging --region ${{ secrets.AWS_REGION }}

      - name: Rolling deploy
        run: |
          IMAGE="${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ steps.tag.outputs.tag }}"
          kubectl set image deployment/banking-platform banking-platform="$IMAGE" -n ${{ env.K8S_NAMESPACE }}
          kubectl rollout status deployment/banking-platform -n ${{ env.K8S_NAMESPACE }} --timeout=300s

      - name: Smoke test
        run: |
          sleep 15  # Allow readiness probe to pass
          curl -sf https://staging.bankingplatform.com/api/actuator/health | jq '.status'
          curl -sf https://staging.bankingplatform.com/api/actuator/health/readiness | jq '.status'

      - name: Gatling smoke test
        run: |
          ./gradlew gatlingRun \
            -Dgatling.simulation=simulations.SmokeTestSimulation \
            -Dgatling.baseUrl=https://staging.bankingplatform.com \
            --no-daemon

      - name: Upload Gatling report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: gatling-staging-${{ github.sha }}
          path: build/reports/gatling/
          retention-days: 30

  approve-production:
    name: Manual Approval Gate
    runs-on: ubuntu-latest
    needs: deploy-staging
    environment: production-approval    # Requires human reviewer in repo Settings → Environments
    steps:
      - run: echo "Production deploy approved by ${{ github.actor }}"

  deploy-production:
    name: Deploy → Production
    runs-on: ubuntu-latest
    needs: approve-production
    environment:
      name: production
      url: https://api.bankingplatform.com
    steps:
      - uses: actions/checkout@v4

      - name: Resolve image tag
        id: tag
        run: echo "tag=${{ github.event.inputs.image-tag || format('sha-{0}', github.sha) }}" >> $GITHUB_OUTPUT

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id:     ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region:            ${{ secrets.AWS_REGION }}

      - name: Update kubeconfig (EKS prod)
        run: aws eks update-kubeconfig --name banking-platform-production --region ${{ secrets.AWS_REGION }}

      - name: Rolling deploy with rollback on failure
        id: deploy
        run: |
          IMAGE="${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ steps.tag.outputs.tag }}"
          kubectl set image deployment/banking-platform banking-platform="$IMAGE" -n ${{ env.K8S_NAMESPACE }}
          kubectl rollout status deployment/banking-platform -n ${{ env.K8S_NAMESPACE }} --timeout=600s

      - name: Production smoke test
        id: smoke
        run: |
          sleep 20
          curl -sf https://api.bankingplatform.com/api/actuator/health | jq '.status'

      - name: Rollback on smoke failure
        if: failure() && steps.smoke.outcome == 'failure'
        run: |
          echo "Smoke test failed — rolling back"
          kubectl rollout undo deployment/banking-platform -n ${{ env.K8S_NAMESPACE }}
          kubectl rollout status deployment/banking-platform -n ${{ env.K8S_NAMESPACE }} --timeout=300s

      - name: Notify Slack — success
        if: success()
        uses: slackapi/slack-github-action@v1
        with:
          payload: '{"text":"✅ Banking Platform deployed to production — ${{ steps.tag.outputs.tag }} by ${{ github.actor }}"}'
        env:
          SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}

      - name: Notify Slack — failure
        if: failure()
        uses: slackapi/slack-github-action@v1
        with:
          payload: '{"text":"🚨 Banking Platform PRODUCTION DEPLOY FAILED — ${{ steps.tag.outputs.tag }} — <${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}|View Run>"}'
        env:
          SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}
```

---

## Secrets Required

| Secret | Scope | Used In |
|--------|-------|---------|
| `AWS_ACCESS_KEY_ID` | Repo | CD — EKS kubectl access |
| `AWS_SECRET_ACCESS_KEY` | Repo | CD — EKS kubectl access |
| `AWS_REGION` | Repo | CD — EKS region |
| `SLACK_WEBHOOK_URL` | Repo | CD — Slack notifications |
| `NVD_API_KEY` | Repo | CI — OWASP NVD rate limit bypass |
| `SONAR_TOKEN` | Repo | CI — SonarQube analysis |
| `GITHUB_TOKEN` | Auto | CI/CD — GHCR push, SARIF upload |

---

## Manual Rollback Workflow (`rollback.yml`)

```yaml
name: Manual Rollback

on:
  workflow_dispatch:
    inputs:
      environment:
        type: choice
        options: [ staging, production ]
        required: true
      revision:
        description: "Rollback to revision number (leave empty for previous)"
        required: false

jobs:
  rollback:
    name: Rollback ${{ github.event.inputs.environment }}
    runs-on: ubuntu-latest
    environment: ${{ github.event.inputs.environment }}
    steps:
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id:     ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region:            ${{ secrets.AWS_REGION }}

      - name: Update kubeconfig
        run: |
          CLUSTER="banking-platform-${{ github.event.inputs.environment }}"
          aws eks update-kubeconfig --name "$CLUSTER" --region ${{ secrets.AWS_REGION }}

      - name: Rollback
        run: |
          REVISION="${{ github.event.inputs.revision }}"
          if [ -z "$REVISION" ]; then
            kubectl rollout undo deployment/banking-platform -n banking
          else
            kubectl rollout undo deployment/banking-platform -n banking --to-revision="$REVISION"
          fi
          kubectl rollout status deployment/banking-platform -n banking --timeout=300s

      - name: Show current image
        run: |
          kubectl get deployment banking-platform -n banking \
            -o jsonpath='{.spec.template.spec.containers[0].image}'
```

---

## Gradle Cache Optimization

```yaml
# Cache Gradle both wrapper and dependencies — speeds up CI by 60–80%
- uses: actions/setup-java@v4
  with:
    java-version: "21"
    distribution: temurin
    cache: gradle                    # Caches ~/.gradle/caches and ~/.gradle/wrapper

# Optional: explicit cache with more control
- uses: actions/cache@v4
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
      build/
    key: gradle-${{ runner.os }}-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
    restore-keys: gradle-${{ runner.os }}-
```

---

## Critical Rules

1. **Always use `concurrency: cancel-in-progress: true`** on feature branches — avoid wasting minutes on superseded pushes.
2. **Never push Docker images on pull requests** — only push from `main` / `release/**` branches.
3. **Use `environment:` with protection rules** for staging and production — enforces required reviewers.
4. **Store all secrets in GitHub Secrets** — never hardcode in workflows or check in as files.
5. **Pin action versions with SHA** for security-sensitive steps (e.g., `actions/checkout@v4`) — use Dependabot to keep them updated.
6. **Always upload test reports as artifacts** — `if: always()` ensures reports are captured even on failure.
7. **Gate container scan on CRITICAL/HIGH** — `exit-code: "1"` blocks the pipeline on known CVEs.
8. **Include a manual rollback workflow** — ops teams need a one-click rollback during incidents.
9. **Add `--no-daemon` to all Gradle commands in CI** — Gradle daemon state persists between jobs on self-hosted but not on GitHub-hosted runners.
10. **Set `retention-days`** on all artifacts — don't accumulate artifacts indefinitely (default is 90 days = expensive).
