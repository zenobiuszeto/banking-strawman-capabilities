---
name: gke-deployment
description: |
  **GKE Deployment Skill**: Production-grade deployment of the banking platform to Google Kubernetes Engine using Docker, Kubernetes manifests, Helm, Workload Identity, GCLB/NEG Ingress, Artifact Registry, GCP Secret Manager, Cloud Armor WAF, HPA with custom metrics, and GKE Autopilot vs Standard guidance.

  MANDATORY TRIGGERS: GKE, Google Kubernetes Engine, gcloud, gcloud container clusters, gcloud auth, Artifact Registry, GCR, gcr.io, Workload Identity, workloadIdentityUser, gcloud iam, GCP Secret Manager, External Secrets Operator GCP, Cloud Armor, BackendConfig, FrontendConfig, GCLB, Google Cloud Load Balancer, NEG, Network Endpoint Group, Cloud DNS, managed certificate, ManagedCertificate, Cloud NAT, GKE Autopilot, GKE Standard, node pool, GKE add-on, Config Connector, Cloud Monitoring, Cloud Logging, GKE deployment, GCP deployment, Google Cloud deployment, helm GKE, kubectl GKE, gar.io.
---

# GKE Deployment Skill — Banking Platform on Google Cloud

You are deploying the **banking platform** to **Google Kubernetes Engine (GKE)** using:
- GKE Standard (1.30+) — managed node pools on `n2-standard-4`
- Workload Identity for pod-level GCP IAM (no service account keys)
- Artifact Registry for Docker images
- GCP Secret Manager + External Secrets Operator for secret injection
- Google Cloud Load Balancer (GCLB) with Cloud Armor WAF
- GKE's built-in Cloud Monitoring + Cloud Logging

---

## Cluster Creation

```bash
# Variables
PROJECT_ID="banking-platform-prod"
CLUSTER_NAME="banking-platform-production"
REGION="us-central1"
NETWORK="banking-vpc"
SUBNETWORK="banking-gke-subnet"

# 1. Enable APIs
gcloud services enable \
  container.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  cloudresourcemanager.googleapis.com \
  iam.googleapis.com \
  --project=$PROJECT_ID

# 2. Create GKE Standard cluster with Workload Identity
gcloud container clusters create $CLUSTER_NAME \
  --project=$PROJECT_ID \
  --region=$REGION \
  --num-nodes=2 \
  --node-locations=us-central1-a,us-central1-b,us-central1-c \
  --machine-type=n2-standard-4 \          # 4 vCPU, 16GB
  --disk-type=pd-ssd \
  --disk-size=100GB \
  --network=$NETWORK \
  --subnetwork=$SUBNETWORK \
  --enable-ip-alias \                     # VPC-native — required for NEG ingress
  --workload-pool=$PROJECT_ID.svc.id.goog \  # Workload Identity
  --enable-autoscaling \
  --min-nodes=3 \
  --max-nodes=15 \
  --enable-autorepair \
  --enable-autoupgrade \
  --release-channel=regular \             # Managed GKE version updates
  --logging=SYSTEM,WORKLOAD \
  --monitoring=SYSTEM,WORKLOAD \
  --addons=HorizontalPodAutoscaling,HttpLoadBalancing,GcePersistentDiskCsiDriver

# 3. Get credentials
gcloud container clusters get-credentials $CLUSTER_NAME \
  --region=$REGION \
  --project=$PROJECT_ID
```

---

## Artifact Registry — Build & Push

```bash
# 1. Create Artifact Registry repository
gcloud artifacts repositories create banking-platform \
  --repository-format=docker \
  --location=us-central1 \
  --description="Banking Platform Docker images" \
  --project=$PROJECT_ID

# Registry URL: us-central1-docker.pkg.dev/$PROJECT_ID/banking-platform/banking-platform

# 2. Authenticate Docker with GAR
gcloud auth configure-docker us-central1-docker.pkg.dev

# 3. Build and push
IMAGE="us-central1-docker.pkg.dev/$PROJECT_ID/banking-platform/banking-platform"
docker build -t "$IMAGE:sha-$(git rev-parse --short HEAD)" -t "$IMAGE:latest" .
docker push "$IMAGE" --all-tags

# Or via CI — GitHub Actions GCP auth
- uses: google-github-actions/auth@v2
  with:
    workload_identity_provider: 'projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/providers/github-provider'
    service_account: 'github-actions@${{ env.PROJECT_ID }}.iam.gserviceaccount.com'

- uses: docker/login-action@v3
  with:
    registry: us-central1-docker.pkg.dev
    username: oauth2accesstoken
    password: ${{ steps.auth.outputs.access_token }}
```

---

## Workload Identity (Pod-level GCP IAM)

```bash
PROJECT_ID="banking-platform-prod"
GSA_NAME="banking-platform-gsa"         # GCP Service Account
KSA_NAME="banking-platform-sa"          # Kubernetes Service Account
NAMESPACE="banking"

# 1. Create GCP Service Account
gcloud iam service-accounts create $GSA_NAME \
  --display-name="Banking Platform Service Account" \
  --project=$PROJECT_ID

GSA_EMAIL="$GSA_NAME@$PROJECT_ID.iam.gserviceaccount.com"

# 2. Grant GCP permissions to the GSA
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$GSA_EMAIL" \
  --role="roles/secretmanager.secretAccessor"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$GSA_EMAIL" \
  --role="roles/storage.objectViewer"  # If reading from GCS

# 3. Allow KSA to impersonate GSA (Workload Identity binding)
gcloud iam service-accounts add-iam-policy-binding $GSA_EMAIL \
  --role="roles/iam.workloadIdentityUser" \
  --member="serviceAccount:$PROJECT_ID.svc.id.goog[$NAMESPACE/$KSA_NAME]"

# 4. Annotate Kubernetes Service Account
kubectl annotate serviceaccount $KSA_NAME \
  --namespace=$NAMESPACE \
  "iam.gke.io/gcp-service-account=$GSA_EMAIL"
```

```yaml
# infra/k8s/serviceaccount-gke.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: banking-platform-sa
  namespace: banking
  annotations:
    iam.gke.io/gcp-service-account: "banking-platform-gsa@banking-platform-prod.iam.gserviceaccount.com"
```

---

## GCP Secret Manager + External Secrets

```bash
# Create secrets in GCP Secret Manager
gcloud secrets create banking-platform-db-password \
  --replication-policy=automatic \
  --project=$PROJECT_ID

echo -n "super-secret-password" | \
  gcloud secrets versions add banking-platform-db-password --data-file=- \
  --project=$PROJECT_ID

# Install External Secrets Operator
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets \
  -n external-secrets --create-namespace
```

```yaml
# infra/k8s/externalsecret-gke.yaml
apiVersion: external-secrets.io/v1beta1
kind: ClusterSecretStore
metadata:
  name: gcp-secretmanager
spec:
  provider:
    gcpsm:
      projectID: banking-platform-prod
      auth:
        workloadIdentity:
          clusterLocation: us-central1
          clusterName: banking-platform-production
          serviceAccountRef:
            name: banking-platform-sa
            namespace: banking
---
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: banking-platform-secrets
  namespace: banking
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: gcp-secretmanager
    kind: ClusterSecretStore
  target:
    name: banking-platform-secrets
    creationPolicy: Owner
  data:
    - secretKey: db-password
      remoteRef:
        key: banking-platform-db-password
    - secretKey: db-username
      remoteRef:
        key: banking-platform-db-username
    - secretKey: redis-password
      remoteRef:
        key: banking-platform-redis-password
```

---

## GKE Ingress — GCLB + Cloud Armor + Managed Certificate

```yaml
# infra/k8s/managed-certificate.yaml
apiVersion: networking.gke.io/v1
kind: ManagedCertificate
metadata:
  name: banking-platform-cert
  namespace: banking
spec:
  domains:
    - api.bankingplatform.com
    - staging.bankingplatform.com
---
# infra/k8s/cloud-armor-backendconfig.yaml
apiVersion: cloud.google.com/v1
kind: BackendConfig
metadata:
  name: banking-platform-backend-config
  namespace: banking
spec:
  securityPolicy:
    name: banking-platform-armor         # Cloud Armor WAF policy
  healthCheck:
    checkIntervalSec: 30
    timeoutSec: 10
    type: HTTP
    requestPath: /api/actuator/health/readiness
  connectionDraining:
    drainingTimeoutSec: 60              # Matches terminationGracePeriodSeconds
  sessionAffinity:
    affinityType: "NONE"                # Stateless — no sticky sessions needed
  logging:
    enable: true
    sampleRate: 0.1                     # Log 10% of requests
---
# infra/k8s/ingress-gke.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: banking-platform-ingress
  namespace: banking
  annotations:
    kubernetes.io/ingress.class: gce
    kubernetes.io/ingress.global-static-ip-name: banking-platform-ip   # Pre-reserved IP
    networking.gke.io/managed-certificates: banking-platform-cert
    networking.gke.io/v1beta1.FrontendConfig: banking-platform-frontend
spec:
  rules:
    - host: api.bankingplatform.com
      http:
        paths:
          - path: /api/*
            pathType: ImplementationSpecific
            backend:
              service:
                name: banking-platform-service
                port:
                  number: 80
---
apiVersion: networking.gke.io/v1beta1
kind: FrontendConfig
metadata:
  name: banking-platform-frontend
  namespace: banking
spec:
  sslPolicy: banking-tls-policy          # TLS 1.2+ only
  redirectToHttps:
    enabled: true
    responseCodeName: MOVED_PERMANENTLY
```

```bash
# 1. Reserve static IP
gcloud compute addresses create banking-platform-ip \
  --global \
  --project=$PROJECT_ID

# 2. Create Cloud Armor WAF policy
gcloud compute security-policies create banking-platform-armor \
  --description="WAF for Banking Platform" \
  --project=$PROJECT_ID

# Enable OWASP Top 10 preconfigured rules
gcloud compute security-policies rules create 1000 \
  --security-policy=banking-platform-armor \
  --expression="evaluatePreconfiguredExpr('xss-stable')" \
  --action=deny-403

gcloud compute security-policies rules create 1001 \
  --security-policy=banking-platform-armor \
  --expression="evaluatePreconfiguredExpr('sqli-stable')" \
  --action=deny-403

# Rate limiting (100 req/min per IP)
gcloud compute security-policies rules create 2000 \
  --security-policy=banking-platform-armor \
  --expression="true" \
  --action=rate-based-ban \
  --rate-limit-threshold-count=100 \
  --rate-limit-threshold-interval-sec=60 \
  --ban-duration-sec=600
```

---

## Helm Deploy to GKE

```bash
# values-gke-production.yaml
IMAGE_REPO="us-central1-docker.pkg.dev/banking-platform-prod/banking-platform/banking-platform"

helm upgrade --install banking-platform ./helm/banking-platform \
  --namespace banking \
  --create-namespace \
  -f helm/banking-platform/values-gke-production.yaml \
  --set image.repository=$IMAGE_REPO \
  --set image.tag=sha-${GITHUB_SHA} \
  --wait \
  --timeout 10m \
  --atomic
```

---

## GitHub Actions — GKE Deploy Step

```yaml
# In cd.yml — GKE deploy job
deploy-gke-staging:
  name: Deploy → GKE Staging
  runs-on: ubuntu-latest
  environment: staging
  steps:
    - uses: actions/checkout@v4

    - name: Authenticate to GCP
      uses: google-github-actions/auth@v2
      with:
        workload_identity_provider: ${{ secrets.GCP_WORKLOAD_IDENTITY_PROVIDER }}
        service_account: ${{ secrets.GCP_SERVICE_ACCOUNT }}

    - name: Set up Cloud SDK
      uses: google-github-actions/setup-gcloud@v2

    - name: Get GKE credentials
      uses: google-github-actions/get-gke-credentials@v2
      with:
        cluster_name: banking-platform-staging
        location: us-central1
        project_id: ${{ secrets.GCP_PROJECT_ID }}

    - name: Helm upgrade
      run: |
        IMAGE="us-central1-docker.pkg.dev/${{ secrets.GCP_PROJECT_ID }}/banking-platform/banking-platform"
        helm upgrade --install banking-platform ./helm/banking-platform \
          --namespace banking --create-namespace \
          -f helm/banking-platform/values-gke-staging.yaml \
          --set image.repository=$IMAGE \
          --set image.tag=sha-${{ github.sha }} \
          --wait --timeout 10m --atomic
```

---

## Cloud Monitoring — Custom Dashboards

```bash
# Push JVM metrics to Cloud Monitoring via Prometheus (auto-collected by GKE)
# Spring Boot Actuator already exposes Prometheus metrics at /actuator/prometheus
# GKE + Cloud Monitoring scrapes these automatically with the Prometheus add-on

# Enable Managed Prometheus on cluster
gcloud container clusters update banking-platform-production \
  --enable-managed-prometheus \
  --region=us-central1 \
  --project=$PROJECT_ID

# Add PodMonitoring resource
kubectl apply -f - <<EOF
apiVersion: monitoring.googleapis.com/v1
kind: PodMonitoring
metadata:
  name: banking-platform-metrics
  namespace: banking
spec:
  selector:
    matchLabels:
      app: banking-platform
  endpoints:
    - port: 8080
      path: /api/actuator/prometheus
      interval: 30s
EOF
```

---

## Useful GKE kubectl Commands

```bash
# Cluster info
kubectl cluster-info
gcloud container clusters list --project=$PROJECT_ID

# Rolling deploy
kubectl set image deployment/banking-platform \
  banking-platform=us-central1-docker.pkg.dev/$PROJECT_ID/banking-platform/banking-platform:sha-$COMMIT \
  -n banking
kubectl rollout status deployment/banking-platform -n banking

# View logs in Cloud Logging
gcloud logging read 'resource.type="k8s_container" resource.labels.cluster_name="banking-platform-production" resource.labels.namespace_name="banking"' \
  --limit=50 --format=json | jq '.[].jsonPayload'

# Autoscaler status
kubectl get hpa -n banking
kubectl describe hpa banking-platform-hpa -n banking

# Node pool scaling
gcloud container node-pools update banking-general \
  --cluster=banking-platform-production \
  --max-nodes=20 \
  --region=us-central1

# Pod disruption budget status
kubectl get pdb -n banking
```

---

## Critical Rules

1. **Always use Workload Identity** — never create or mount GCP service account key files in pods.
2. **Use Artifact Registry, not GCR** — Container Registry (`gcr.io`) is deprecated; use `*.pkg.dev`.
3. **Reserve a static global IP before creating the Ingress** — dynamic IPs change and break DNS.
4. **Managed Certificates can take 15–60 minutes to provision** — don't assume HTTPS works immediately after creation.
5. **Enable Cloud Armor before go-live** — configure WAF preconfigured rules at minimum (XSS, SQLi).
6. **Use `--atomic` with Helm** — automatically rolls back if the new deployment fails health checks.
7. **Enable GKE Managed Prometheus** for metrics — integrates natively with Cloud Monitoring at no extra setup.
8. **Set `PodDisruptionBudget`** — prevents GKE node upgrades from killing all pods simultaneously.
9. **Use `release-channel: regular`** for GKE version management — automatic, tested upgrades without manual patching.
10. **Test Workload Identity locally** with `gcloud auth print-identity-token` before deploying — misconfigured WI is the #1 GKE auth issue.
