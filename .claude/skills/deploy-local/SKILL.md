---
name: deploy-local
description: Deploy SafeDrive infra and services to local minikube and check status. Use when asked to deploy, redeploy, tear down, or verify the local environment.
---
# Deploy local
1. Check minikube: `minikube status` (start with `minikube start --cpus 4 --memory 8192` if down).
2. Deploy: `platform/scripts/deploy.sh infra` first, then `deploy.sh <service>` or `deploy.sh all`.
3. Verify: `platform/scripts/deploy.sh status`; each service must report ready on its readiness endpoint.
4. Tear down: `platform/scripts/deploy.sh down`.
Placeholder: scripts do not exist yet; create them per plan.md section 4 before using this skill.
