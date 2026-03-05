# CI/CD Reference — GitHub Actions, Dev → UAT → Prod Pipeline

## 1. CI Pipeline

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main, 'release/**']
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: testdb
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports: ['5432:5432']
        options: --health-cmd pg_isready --health-interval 10s

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: gradle

      - name: Validate schemas
        run: ./gradlew generateAvroJava

      - name: Build and test
        run: ./gradlew build jacocoTestReport
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/testdb
          SPRING_DATASOURCE_USERNAME: test
          SPRING_DATASOURCE_PASSWORD: test

      - name: Check coverage gate
        run: ./gradlew jacocoTestCoverageVerification

      - name: Run Checkstyle
        run: ./gradlew checkstyleMain

      - name: Run SpotBugs
        run: ./gradlew spotbugsMain

      - name: SonarQube analysis
        run: ./gradlew sonarqube
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}

      - name: Build Docker image
        run: docker build -t ${{ env.IMAGE_NAME }}:${{ github.sha }} .

      - name: Push to registry
        if: github.ref == 'refs/heads/main'
        run: |
          echo ${{ secrets.REGISTRY_PASSWORD }} | docker login -u ${{ secrets.REGISTRY_USER }} --password-stdin
          docker push ${{ env.IMAGE_NAME }}:${{ github.sha }}
```

## 2. Deploy to Dev (auto on main merge)

```yaml
# .github/workflows/deploy-dev.yml
name: Deploy Dev

on:
  workflow_run:
    workflows: ["CI"]
    branches: [main]
    types: [completed]

jobs:
  deploy-dev:
    if: ${{ github.event.workflow_run.conclusion == 'success' }}
    runs-on: ubuntu-latest
    environment: dev
    steps:
      - name: Deploy to Dev
        run: |
          helm upgrade --install account-service helm/account-service \
            -f helm/account-service/values-dev.yaml \
            --set image.tag=${{ github.sha }} \
            --namespace platform-dev \
            --wait --timeout 5m
```

## 3. Promote to UAT (manual approval)

```yaml
# .github/workflows/deploy-uat.yml
name: Deploy UAT

on:
  workflow_dispatch:
    inputs:
      image_tag:
        description: 'Image tag to deploy'
        required: true

jobs:
  integration-tests:
    runs-on: ubuntu-latest
    steps:
      - name: Run Gatling load tests against Dev
        run: ./gradlew gatlingRun -Denv=dev

  deploy-uat:
    needs: integration-tests
    environment: uat       # requires approval in GitHub Environments
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to UAT
        run: |
          helm upgrade --install account-service helm/account-service \
            -f helm/account-service/values-uat.yaml \
            --set image.tag=${{ github.event.inputs.image_tag }} \
            --namespace platform-uat \
            --wait --timeout 10m
```

## 4. Promote to Prod (change management gate)

```yaml
# .github/workflows/deploy-prod.yml
name: Deploy Prod

on:
  workflow_dispatch:
    inputs:
      image_tag:
        required: true
      change_ticket:
        description: 'Change management ticket ID'
        required: true

jobs:
  deploy-prod:
    environment: production    # requires 2 approvers in GitHub Environments
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Prod (canary 10%)
        run: |
          helm upgrade --install account-service helm/account-service \
            -f helm/account-service/values-prod.yaml \
            --set image.tag=${{ github.event.inputs.image_tag }} \
            --set canary.enabled=true \
            --set canary.weight=10 \
            --namespace platform-prod \
            --wait --timeout 10m

      - name: Monitor canary for 10 minutes
        run: sleep 600 && ./scripts/check-error-rate.sh

      - name: Promote to 100%
        run: |
          helm upgrade account-service helm/account-service \
            -f helm/account-service/values-prod.yaml \
            --set image.tag=${{ github.event.inputs.image_tag }} \
            --set canary.enabled=false \
            --namespace platform-prod
```

## 5. CI/CD Checklist

- [ ] CI runs on every PR: build, test, coverage, Checkstyle, SpotBugs, SonarQube
- [ ] Coverage gate fails CI if < 80%
- [ ] Docker image tagged with Git SHA — never `latest` in CI
- [ ] Dev auto-deploys on main merge
- [ ] UAT requires integration tests to pass before deploy
- [ ] Prod requires manual approval from 2 reviewers
- [ ] Change management ticket required for prod deployments
- [ ] Canary deployment strategy for prod with automated rollback
- [ ] Secrets stored in GitHub Environments secrets — never in workflow files
