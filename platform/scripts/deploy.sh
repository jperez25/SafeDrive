#!/usr/bin/env bash
# SafeDrive local deploy (minikube).
#   deploy.sh infra | <service> | all | down | status
# Infra (Postgres, Kafka, topics) first, then services. Every step waits for readiness.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INFRA_DIR="$ROOT/platform/infra"
SERVICES_DIR="$ROOT/platform/services"
NS=safedrive
SERVICES=(telemetry-ingest trip-processor scoring policy-rating)
TIMEOUT=240s

log() { printf '==> %s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

usage() {
  echo "usage: deploy.sh infra | <service> | all | down | status"
  echo "services: ${SERVICES[*]}"
  exit 1
}

require_minikube() {
  command -v kubectl >/dev/null || die "kubectl not found"
  local ctx
  ctx="$(kubectl config current-context 2>/dev/null || true)"
  [[ "$ctx" == "minikube" ]] || die "kubectl context is '${ctx:-none}', expected 'minikube' (run: minikube start)"
  minikube status >/dev/null 2>&1 || die "minikube is not running (run: minikube start --cpus 4 --memory 8192)"
}

# Dummy local credentials. Override with the gitignored .env at the repo root.
load_env() {
  if [[ -f "$ROOT/.env" ]]; then
    set -a; . "$ROOT/.env"; set +a
  fi
  : "${POSTGRES_PASSWORD:=changeme}"
  : "${INGEST_DB_PASSWORD:=changeme}"
  : "${TRIP_DB_PASSWORD:=changeme}"
  : "${SCORING_DB_PASSWORD:=changeme}"
  : "${RATING_DB_PASSWORD:=changeme}"
  : "${SAFEDRIVER_DB_PASSWORD:=changeme}"
}

apply_secrets() {
  load_env
  kubectl -n "$NS" create secret generic postgres-credentials \
    --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
    --from-literal=INGEST_DB_PASSWORD="$INGEST_DB_PASSWORD" \
    --from-literal=TRIP_DB_PASSWORD="$TRIP_DB_PASSWORD" \
    --from-literal=SCORING_DB_PASSWORD="$SCORING_DB_PASSWORD" \
    --from-literal=RATING_DB_PASSWORD="$RATING_DB_PASSWORD" \
    --from-literal=SAFEDRIVER_DB_PASSWORD="$SAFEDRIVER_DB_PASSWORD" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
}

deploy_infra() {
  log "namespace"
  kubectl apply -f "$INFRA_DIR/00-namespace.yaml"
  log "secrets"
  apply_secrets
  log "postgres"
  kubectl apply -f "$INFRA_DIR/10-postgres.yaml"
  log "kafka"
  kubectl apply -f "$INFRA_DIR/20-kafka.yaml"
  kubectl -n "$NS" rollout status statefulset/postgres --timeout="$TIMEOUT"
  kubectl -n "$NS" rollout status statefulset/kafka --timeout="$TIMEOUT"
  log "kafka topics"
  kubectl -n "$NS" delete job kafka-topics --ignore-not-found >/dev/null
  kubectl apply -f "$INFRA_DIR/30-kafka-topics-job.yaml"
  kubectl -n "$NS" wait --for=condition=complete job/kafka-topics --timeout="$TIMEOUT"
  log "infra ready"
}

has_manifest() { [[ -d "$SERVICES_DIR/$1" ]] && compgen -G "$SERVICES_DIR/$1/*.yaml" >/dev/null; }

# Builds the jar and the image straight into minikube's image store (no registry).
build_image() {
  local svc="$1"
  [[ -f "$ROOT/services/$svc/Dockerfile" ]] || return 0
  log "$svc: build image"
  # Relative paths on purpose: with absolute ones `minikube image build` silently builds nothing.
  (
    cd "$ROOT"
    ./mvnw -q -pl "services/$svc" -am package -DskipTests
    minikube image build -t "safedrive/$svc:dev" -f "services/$svc/Dockerfile" .
  )
  minikube image ls | grep -q "safedrive/$svc:dev" || die "image safedrive/$svc:dev was not built"
}

deploy_service() {
  local svc="$1"
  has_manifest "$svc" || die "no manifest for '$svc' in platform/services/$svc (service not implemented yet)"
  log "$svc"
  build_image "$svc"
  kubectl apply -f "$SERVICES_DIR/$svc"
  kubectl -n "$NS" rollout restart "deployment/$svc" >/dev/null
  kubectl -n "$NS" rollout status "deployment/$svc" --timeout="$TIMEOUT"
}

deploy_all() {
  deploy_infra
  local svc
  for svc in "${SERVICES[@]}"; do
    if has_manifest "$svc"; then deploy_service "$svc"; else log "skip $svc (no manifest yet)"; fi
  done
}

down() {
  log "deleting namespace $NS (including volumes)"
  kubectl delete namespace "$NS" --ignore-not-found --wait=true
}

status() {
  kubectl get namespace "$NS" >/dev/null 2>&1 || { echo "namespace $NS not deployed"; return 0; }
  kubectl -n "$NS" get pods -o wide
  echo
  local sts
  for sts in postgres kafka; do
    kubectl -n "$NS" get statefulset "$sts" -o jsonpath="$sts: {.status.readyReplicas}/{.spec.replicas} ready{'\n'}" 2>/dev/null \
      || echo "$sts: not deployed"
  done
  local svc
  for svc in "${SERVICES[@]}"; do
    kubectl -n "$NS" get deployment "$svc" -o jsonpath="$svc: {.status.readyReplicas}/{.spec.replicas} ready{'\n'}" 2>/dev/null \
      || echo "$svc: not deployed"
  done
}

[[ $# -eq 1 ]] || usage
require_minikube
case "$1" in
  infra)  deploy_infra ;;
  all)    deploy_all ;;
  down)   down ;;
  status) status ;;
  *)
    for s in "${SERVICES[@]}"; do
      if [[ "$s" == "$1" ]]; then deploy_service "$1"; exit 0; fi
    done
    usage
    ;;
esac
