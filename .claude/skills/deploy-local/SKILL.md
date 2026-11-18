---
name: deploy-local
description: Deploy SafeDrive infra and services to local minikube and check status. Use when asked to deploy, redeploy, tear down, or verify the local environment.
---
# Deploy local
1. Check the runtime: Docker runs through Colima (`colima start`, profile default: 4 CPUs, 6 GiB). Then `minikube status`; if down: `minikube start --driver=docker --cpus 4 --memory 4096`.
2. Deploy: `platform/scripts/deploy.sh infra` first, then `deploy.sh <service>` or `deploy.sh all`.
3. Verify: `platform/scripts/deploy.sh status`; each service must report ready on its readiness endpoint.
4. Tear down: `platform/scripts/deploy.sh down`.
`deploy.sh` supports `infra`, `<service>`, `all`, `down`, `status`. It refuses to run unless the kubectl context is `minikube`. Services deploy only once they have a manifest in `platform/services/<name>/`.
