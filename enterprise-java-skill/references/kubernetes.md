# Kubernetes & Helm Reference

## 1. Deployment Manifest

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: account-service
  namespace: {{ .Values.namespace }}
  labels:
    app: account-service
    version: {{ .Values.image.tag }}
    team: platform
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      app: account-service
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: account-service
        version: {{ .Values.image.tag }}
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: account-service
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
        - name: account-service
          image: {{ .Values.image.repository }}:{{ .Values.image.tag }}
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
              name: http
            - containerPort: 50051
              name: grpc
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: {{ .Values.profile }}
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: account-service-secrets
                  key: db-password
            - name: JAVA_OPTS
              value: "-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "1000m"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 40
            periodSeconds: 20
            failureThreshold: 3
          volumeMounts:
            - name: config
              mountPath: /app/config
              readOnly: true
      volumes:
        - name: config
          configMap:
            name: account-service-config
```

## 2. Service and HPA

```yaml
# k8s/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: account-service
spec:
  selector:
    app: account-service
  ports:
    - name: http
      port: 80
      targetPort: 8080
    - name: grpc
      port: 50051
      targetPort: 50051
---
# k8s/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: account-service
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: account-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

## 3. Helm Chart Structure

```
helm/account-service/
├── Chart.yaml
├── values.yaml           # defaults
├── values-dev.yaml
├── values-uat.yaml
├── values-prod.yaml
└── templates/
    ├── deployment.yaml
    ├── service.yaml
    ├── hpa.yaml
    ├── configmap.yaml
    ├── serviceaccount.yaml
    ├── ingress.yaml
    └── _helpers.tpl
```

```yaml
# values.yaml
replicaCount: 2
namespace: platform
profile: dev
image:
  repository: your-registry/account-service
  tag: latest
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

## 4. Spring Boot Kubernetes-Ready Configuration

```yaml
# application.yml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s   # graceful shutdown

management:
  endpoint:
    health:
      probes:
        enabled: true       # /actuator/health/liveness and /readiness
      group:
        readiness:
          include: db, redis, kafka
  endpoints:
    web:
      exposure:
        include: health, info, prometheus

server:
  shutdown: graceful
```

## 5. K8s Checklist

- [ ] Non-root container user (runAsUser: 1000)
- [ ] Resource requests AND limits set
- [ ] Liveness probe uses /actuator/health/liveness
- [ ] Readiness probe uses /actuator/health/readiness and includes dependencies
- [ ] HPA configured with CPU and memory targets
- [ ] Graceful shutdown enabled (server.shutdown=graceful)
- [ ] Secrets injected via SecretKeyRef — never in configmap or env literals
- [ ] Prometheus annotations on pod template
- [ ] Separate values files per environment
