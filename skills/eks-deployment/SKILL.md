---
name: eks-deployment
description: |
  **AWS EKS Deployment Skill**: Production-grade deployment of the banking platform to AWS EKS using Docker, Kubernetes manifests, Helm, IRSA (IAM Roles for Service Accounts), ALB Ingress Controller, HPA, PodDisruptionBudget, Network Policies, Secrets Manager integration, and cluster autoscaler. Tailored to the existing infra/k8s/ manifests in this project.

  MANDATORY TRIGGERS: EKS, AWS EKS, Elastic Kubernetes Service, eksctl, aws eks, kubectl apply, Helm, helm install, helm upgrade, ALB, AWS Load Balancer Controller, IRSA, IAM Role for Service Account, ECR, Amazon ECR, ACM, Certificate Manager, ExternalDNS, Cluster Autoscaler, Karpenter, aws-load-balancer-controller, Route53, VPC, eks.amazonaws.com/role-arn, AWS Secrets Manager, External Secrets Operator, ESO, HPA, PDB, Pod Disruption Budget, Network Policy, node group, Fargate, managed node group, EKS add-on, kube-proxy, CoreDNS, VPC CNI, EBS CSI driver, EFS, StorageClass, PVC, persistent volume, AWS deployment, production kubernetes.
---

# AWS EKS Deployment Skill — Banking Platform

You are deploying the **banking platform** to **Amazon EKS** using:
- EKS 1.30+ managed node groups (AL2023, `t3.xlarge` / `m6i.xlarge`)
- Helm 3 for chart-based deployment
- IRSA for pod-level IAM access (Secrets Manager, S3, SES)
- AWS Load Balancer Controller + ACM for TLS
- ExternalDNS for automatic Route53 record management
- Cluster Autoscaler (or Karpenter for next-gen autoscaling)
- Existing manifests in `infra/k8s/`

---

## Cluster Bootstrap (eksctl)

```yaml
# cluster.yaml — create with: eksctl create cluster -f cluster.yaml
apiVersion: eksctl.io/v1alpha5
kind: ClusterConfig

metadata:
  name: banking-platform-production
  region: us-east-1
  version: "1.30"
  tags:
    Environment: production
    Project: banking-platform
    ManagedBy: eksctl

iam:
  withOIDC: true          # Required for IRSA
  serviceAccounts:
    - metadata:
        name: banking-platform-sa
        namespace: banking
      attachPolicyARNs:
        - arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess
      attachPolicy:
        Statement:
          - Effect: Allow
            Action:
              - secretsmanager:GetSecretValue
              - secretsmanager:DescribeSecret
            Resource: "arn:aws:iam::ACCOUNT_ID:secret:banking-platform/*"

managedNodeGroups:
  - name: banking-general
    instanceType: m6i.xlarge    # 4 vCPU, 16GB — good balance for Spring Boot
    minSize: 3
    desiredCapacity: 5
    maxSize: 15
    availabilityZones: [ us-east-1a, us-east-1b, us-east-1c ]
    labels:
      role: general
    tags:
      NodeGroup: banking-general
    updateConfig:
      maxUnavailable: 1         # Rolling node updates
    iam:
      attachPolicyARNs:
        - arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy
        - arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly
        - arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy

  - name: banking-memory
    instanceType: r6i.xlarge    # Memory-optimized — for Redis, reporting pods
    minSize: 0
    desiredCapacity: 2
    maxSize: 5
    labels:
      role: memory-optimized
    taints:
      - key: role
        value: memory-optimized
        effect: NoSchedule

addons:
  - name: vpc-cni
    version: latest
  - name: coredns
    version: latest
  - name: kube-proxy
    version: latest
  - name: aws-ebs-csi-driver
    version: latest
    attachPolicyARNs:
      - arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy

cloudWatch:
  clusterLogging:
    enable: [ api, audit, authenticator, controllerManager, scheduler ]
```

```bash
# Create cluster
eksctl create cluster -f cluster.yaml

# Verify
kubectl get nodes -o wide
kubectl get pods -n kube-system
```

---

## IRSA Setup (Pod-level IAM)

```bash
# 1. Get OIDC provider URL
aws eks describe-cluster --name banking-platform-production \
  --query "cluster.identity.oidc.issuer" --output text

# 2. Create OIDC provider (eksctl does this automatically with withOIDC: true)
eksctl utils associate-iam-oidc-provider \
  --region us-east-1 \
  --cluster banking-platform-production \
  --approve

# 3. Create IAM policy for Secrets Manager
aws iam create-policy \
  --policy-name BankingPlatformSecretsPolicy \
  --policy-document file://iam/secrets-policy.json

# iam/secrets-policy.json:
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret"
    ],
    "Resource": "arn:aws:iam::ACCOUNT_ID:secret:banking-platform/*"
  }]
}

# 4. Create service account with role binding
eksctl create iamserviceaccount \
  --name banking-platform-sa \
  --namespace banking \
  --cluster banking-platform-production \
  --attach-policy-arn arn:aws:iam::ACCOUNT_ID:policy/BankingPlatformSecretsPolicy \
  --approve \
  --override-existing-serviceaccounts
```

---

## External Secrets Operator (AWS Secrets Manager → K8s Secrets)

```bash
# Install ESO via Helm
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets \
  -n external-secrets --create-namespace
```

```yaml
# infra/k8s/external-secret.yaml
apiVersion: external-secrets.io/v1beta1
kind: ClusterSecretStore
metadata:
  name: aws-secretsmanager
spec:
  provider:
    aws:
      service: SecretsManager
      region: us-east-1
      auth:
        jwt:
          serviceAccountRef:
            name: external-secrets-sa
            namespace: external-secrets
---
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: banking-platform-secrets
  namespace: banking
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secretsmanager
    kind: ClusterSecretStore
  target:
    name: banking-platform-secrets    # Creates this K8s Secret
    creationPolicy: Owner
  data:
    - secretKey: db-username
      remoteRef:
        key: banking-platform/production/db
        property: username
    - secretKey: db-password
      remoteRef:
        key: banking-platform/production/db
        property: password
    - secretKey: redis-host
      remoteRef:
        key: banking-platform/production/redis
        property: host
```

---

## AWS Load Balancer Controller + Ingress

```bash
# Install ALB controller
helm repo add eks https://aws.github.io/eks-charts
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=banking-platform-production \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

```yaml
# infra/k8s/ingress-alb.yaml — replaces nginx ingress for AWS deployments
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: banking-platform-alb
  namespace: banking
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip         # Use pod IPs directly
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:ACCOUNT:certificate/CERT-ID
    alb.ingress.kubernetes.io/ssl-redirect: "443"
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTP":80},{"HTTPS":443}]'
    alb.ingress.kubernetes.io/healthcheck-path: /api/actuator/health/readiness
    alb.ingress.kubernetes.io/healthcheck-interval-seconds: "30"
    alb.ingress.kubernetes.io/healthy-threshold-count: "2"
    alb.ingress.kubernetes.io/unhealthy-threshold-count: "3"
    # WAF (Web Application Firewall)
    alb.ingress.kubernetes.io/wafv2-acl-arn: arn:aws:wafv2:us-east-1:ACCOUNT:regional/webacl/banking-waf/ID
    # Deletion protection
    alb.ingress.kubernetes.io/load-balancer-attributes: deletion_protection.enabled=true
    # ExternalDNS annotation
    external-dns.alpha.kubernetes.io/hostname: api.bankingplatform.com
spec:
  rules:
    - host: api.bankingplatform.com
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: banking-platform-service
                port:
                  number: 80
```

---

## Helm Chart Structure

```
helm/banking-platform/
├── Chart.yaml
├── values.yaml               ← defaults
├── values-staging.yaml       ← staging overrides
├── values-production.yaml    ← production overrides
└── templates/
    ├── deployment.yaml
    ├── service.yaml
    ├── ingress.yaml
    ├── configmap.yaml
    ├── hpa.yaml
    ├── pdb.yaml
    ├── serviceaccount.yaml
    ├── networkpolicy.yaml
    └── externalsecret.yaml
```

```yaml
# values.yaml
replicaCount: 3
image:
  repository: ghcr.io/your-org/banking-platform
  tag: latest
  pullPolicy: Always
resources:
  requests:
    cpu: 500m
    memory: 512Mi
  limits:
    cpu: "2"
    memory: 1Gi
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
```

```yaml
# values-production.yaml
replicaCount: 5
resources:
  requests:
    cpu: "1"
    memory: 1Gi
  limits:
    cpu: "4"
    memory: 2Gi
autoscaling:
  minReplicas: 5
  maxReplicas: 20
```

```bash
# Deploy / upgrade
helm upgrade --install banking-platform ./helm/banking-platform \
  --namespace banking \
  --create-namespace \
  -f helm/banking-platform/values-production.yaml \
  --set image.tag=sha-${GITHUB_SHA} \
  --wait \
  --timeout 10m \
  --atomic              # Auto-rollback on failure
```

---

## Deploy Script (CI/CD usage)

```bash
#!/bin/bash
# deploy.sh — called from GitHub Actions cd.yml
set -euo pipefail

ENVIRONMENT=${1:-staging}
IMAGE_TAG=${2:-sha-$(git rev-parse --short HEAD)}
CLUSTER_NAME="banking-platform-${ENVIRONMENT}"
NAMESPACE="banking"

echo "Deploying $IMAGE_TAG to $ENVIRONMENT ($CLUSTER_NAME)"

# 1. Auth
aws eks update-kubeconfig --name "$CLUSTER_NAME" --region us-east-1

# 2. Apply namespace and RBAC (idempotent)
kubectl apply -f infra/k8s/namespace.yml

# 3. Update image via Helm
helm upgrade --install banking-platform ./helm/banking-platform \
  --namespace "$NAMESPACE" \
  -f "helm/banking-platform/values-${ENVIRONMENT}.yaml" \
  --set image.tag="$IMAGE_TAG" \
  --wait --timeout 10m --atomic

# 4. Verify rollout
kubectl rollout status deployment/banking-platform -n "$NAMESPACE" --timeout=300s

# 5. Smoke test
HEALTH_URL="https://${ENVIRONMENT}.bankingplatform.com/api/actuator/health"
for i in {1..5}; do
  STATUS=$(curl -sf "$HEALTH_URL" | jq -r '.status' 2>/dev/null || echo "DOWN")
  [ "$STATUS" = "UP" ] && { echo "Health check passed"; exit 0; }
  echo "Attempt $i: status=$STATUS — retrying in 10s"
  sleep 10
done
echo "Health check failed after 5 attempts — rolling back"
helm rollback banking-platform -n "$NAMESPACE"
exit 1
```

---

## Useful kubectl Commands

```bash
# Current deployment status
kubectl get deployment banking-platform -n banking -o wide
kubectl rollout history deployment/banking-platform -n banking

# View pod logs (structured JSON)
kubectl logs -n banking -l app=banking-platform --tail=100 | jq .

# Exec into pod for jcmd/JFR
kubectl exec -it -n banking $(kubectl get pod -n banking -l app=banking-platform -o jsonpath='{.items[0].metadata.name}') -- /bin/sh

# Port-forward to pod for local debugging
kubectl port-forward -n banking svc/banking-platform-service 8080:80

# Resource usage
kubectl top pods -n banking
kubectl top nodes

# Describe a pod (events, probe failures)
kubectl describe pod -n banking -l app=banking-platform

# Manual rollback
kubectl rollout undo deployment/banking-platform -n banking
kubectl rollout undo deployment/banking-platform -n banking --to-revision=3
```

---

## Critical Rules

1. **Use IRSA (not access keys on nodes)** — pod-level IAM with least privilege; never mount EC2 instance profile keys.
2. **External Secrets Operator > Vault agent > K8s Secrets** for secret management — never commit secrets to Git or ConfigMaps.
3. **Always use `--atomic`** with `helm upgrade` — auto-rollback on failure prevents half-deployed state.
4. **Set `PodDisruptionBudget` `minAvailable: 2`** — prevents node drains from taking down the entire service.
5. **Spread pods across AZs** via `topologySpreadConstraints` or `podAntiAffinity` — single-AZ failure must not take down the service.
6. **Use `preStop: sleep 10`** lifecycle hook — drains in-flight requests during rolling updates.
7. **Set `terminationGracePeriodSeconds: 60`** — Spring Boot graceful shutdown needs time to drain.
8. **Never use `latest` tag in production** — always pin to `sha-<gitsha>` for traceability.
9. **Enable WAF on ALB** for public endpoints — block OWASP Top 10 at the edge, before traffic reaches pods.
10. **Test node drains in staging** — `kubectl drain <node>` should trigger graceful pod migration without downtime.
