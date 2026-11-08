# Name: k8s-java-polyrepo-delivery
# Version: 1.2.0
# Description: Automates end-to-end development, isolation-safe git staging, container creation, and Kubernetes CD pipeline orchestration for individual Java microservice polyrepos.
# Triggers: "run deployment", "commit changes", "execute cd pipeline", "deploy to k8s", "push microservice", "trigger rollout"

## Core Capabilities
1. **Isolated Microservice Validation:** Validates local Java compilation, unit testing, and Maven validation targets strictly bounded within this individual service repo.
2. **Git Lifecycle & Image Version Alignment:** Manages branch checkouts, drafts precise contextual conventional commits, pushes to origin, and locks image versions using the Git SHA.
3. **Kubernetes GitOps & CD Orchestration:** Verifies manifest compliance, generates Docker containers, handles container registries, and updates target Kubernetes orchestrators using rolling updates.

## Execution Rules & Guidelines

### Phase 1: Pre-flight Verification & Testing
* **Local Quality Gate:** Before allowing any git operations, execute `./mvnw clean verify` (or `mvn clean verify`) to catch compilation errors, failing unit tests, or code quality blocks.
* **Fail Fast:** If the local build or checkstyle verification fails, immediately abort the automation loop and display the error traceback to the developer.

### Phase 2: Autonomous Workspace Commits
* **Conventional Scopes:** Enforce the Conventional Commits specification. The scope must reflect the microservice component (e.g., `feat(api): add transactional retry policy`).
* **Clean State Check:** Run `git pull --rebase origin <active-branch>` before execution to resolve transient remote updates and avoid merge conflicts.

### Phase 3: Containerization & Kubernetes CD Execution
* **Multi-Stage Builds:** Require multi-stage Dockerfiles utilizing distroless or lightweight JRE runtimes (e.g., Eclipse Temurin Alpine) to minimize attack vectors.
* **Manifest Manifestation:** Track the location of the deployment files (e.g., Helm Chart, Kustomize overlay, or vanilla K8s manifests under a `/k8s` directory).
* **Safe Rolling Restarts:** Ensure the CD pipeline handles seamless production transition without downtime by ensuring proper Kubernetes Probes (Liveness/Readiness) are mapped.

---

## Technical Implementations & Templates

### 1. Optimized Multi-Stage Dockerfile
Use this optimized structure to cleanly isolate build tools from the production execution layer:

```dockerfile
# Stage 1: Build Package
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Hardened Runtime Environment
FROM eclipse-temurin:21-jre-alpine
WORKDIR /deployments
RUN addgroup -S appuser && adduser -S appuser -G appuser
USER appuser:appuser
COPY --from=builder /workspace/target/*.jar microservice.jar
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "java \$JAVA_OPTS -jar microservice.jar"]
```

### 2. Kubernetes Deployment Manifest Configuration (`k8s/deployment.yaml`)
Maintain this cloud-native manifest template within your code structure to allow automated zero-downtime updates:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: java-microservice
  labels:
    app: java-microservice
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: java-microservice
  template:
    metadata:
      labels:
        app: java-microservice
    spec:
      containers:
      - name: application
        image: ghcr.io/your-org/java-microservice:latest
        ports:
        - containerPort: 8080
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 15
        resources:
          limits:
            cpu: "1"
            memory: 1Gi
          requests:
            cpu: "500m"
            memory: 512Mi
```

### 3. Integrated Polyrepo CD Pipeline Blueprint (GitHub Actions Reference)
When writing or amending `.github/workflows/deploy.yml`, utilize this automated execution script:

```yaml
name: K8s Polyrepo Production Deployment

on:
  push:
    branches: [ main ]

permissions:
  contents: read
  packages: write

jobs:
  verify-and-release:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Source Code
        uses: actions/checkout@v4

      - name: Initialize Java Toolchain
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'

      - name: Verify Code Base
        run: mvn clean verify

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Authenticate Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: \${{ github.actor }}
          password: \${{ secrets.GITHUB_TOKEN }}

      - name: Build and Push Container Image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: |
            ghcr.io/\${{ github.repository }}:\${{ github.sha }}
            ghcr.io/\${{ github.repository }}:latest

      - name: Install and Setup kubectl
        uses: azure/setup-kubectl@v4

      - name: Set Kubernetes Context
        uses: azure/k8s-set-context@v4
        with:
          method: kubeconfig
          kubeconfig: \${{ secrets.KUBECONFIG }}

      - name: Update Manifest and Apply Rolling Deployment
        run: |
          # Dynamically inject the unique Git SHA into the manifest deployment
          sed -i "s|image: ghcr.io/your-org/java-microservice:latest|image: ghcr.io/\${{ github.repository }}:\${{ github.sha }}|g" k8s/deployment.yaml
          kubectl apply -f k8s/deployment.yaml
          kubectl rollout status deployment/java-microservice --timeout=300s
```

## User Interactive Workflows

### Command: `/deploy-to-k8s`
1. Review local polyrepo status (`git status`, active files).
2. Execute target testing cycles (`mvn clean verify`).
3. Generate conventional commits tracking features/fixes.
4. Issue updates to the origin repository branch.
5. Watch the active CD logs or return the live endpoint deployment tracking links directly to the terminal workspace.
