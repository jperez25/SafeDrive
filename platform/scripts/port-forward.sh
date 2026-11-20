#!/usr/bin/env bash
# Forwards the local minikube services, Postgres and Kafka to localhost so tests and tools can reach them.
#   port-forward.sh          runs in the foreground; Ctrl-C stops every forward
#   port-forward.sh down     kills all port-forwards of the safedrive namespace (also -down / --down), for stale ones
# Services that are not deployed yet (scoring, policy-rating) are skipped. Override the service and Postgres local ports with env vars.
#
# Default local ports: telemetry-ingest 8081, trip-processor 8082, scoring 8083, policy-rating 8084,
#                      grafana 3000, prometheus 9090, postgres 5432, kafka 9094.
# Kafka note: the broker has a second listener (EXTERNAL, advertised as localhost:9094), so a Kafka client on the
# host can consume and produce through this forward.
# The local port is fixed at 9094 because the broker advertises localhost:9094; it cannot be overridden. The in-cluster listener (9092) is for the services only.
set -euo pipefail

NS=safedrive
PIDS=()

log() { printf '==> %s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

case "${1:-}" in
  down|-down|--down)
    # Kill every port-forward of this namespace, including stale ones left by a crashed run.
    if pkill -f "kubectl -n $NS port-forward"; then
      log "killed stale port-forwards"
    else
      log "no port-forwards running"
    fi
    trap - EXIT
    exit 0
    ;;
  "") ;;
  *) die "unknown argument '$1' (usage: port-forward.sh [down])" ;;
esac

command -v kubectl >/dev/null || die "kubectl not found"
[[ "$(kubectl config current-context 2>/dev/null || true)" == "minikube" ]] \
  || die "kubectl context is not 'minikube' (run: minikube start)"

stop_all() {
  trap - INT TERM EXIT
  ((${#PIDS[@]})) && kill "${PIDS[@]}" 2>/dev/null || true
  log "port-forwards stopped"
}
trap stop_all INT TERM EXIT

# forward <resource> <local port> <remote port>
forward() {
  local resource="$1" local_port="$2" remote_port="$3"
  if ! kubectl -n "$NS" get "$resource" >/dev/null 2>&1; then
    log "skip $resource (not deployed)"
    return
  fi
  if lsof -iTCP:"$local_port" -sTCP:LISTEN >/dev/null 2>&1; then
    die "local port $local_port is already in use (needed for $resource)"
  fi
  kubectl -n "$NS" port-forward "$resource" "$local_port:$remote_port" >/dev/null &
  PIDS+=("$!")
  log "$resource -> localhost:$local_port"
}

forward svc/telemetry-ingest "${INGEST_PORT:-8081}" 8080
forward svc/trip-processor   "${TRIP_PORT:-8082}" 8080
forward svc/scoring          "${SCORING_PORT:-8083}" 8080
forward svc/policy-rating    "${RATING_PORT:-8084}" 8080
forward svc/grafana          "${GRAFANA_PORT:-3000}" 3000
forward svc/prometheus       "${PROMETHEUS_PORT:-9090}" 9090
forward pod/postgres-0       "${POSTGRES_PORT:-5432}" 5432
forward pod/kafka-0          9094 9094

((${#PIDS[@]})) || die "nothing to forward"
log "ready; press Ctrl-C to stop"
# Exit as soon as any forward dies (e.g. the pod restarted) so a stale setup is noticed.
# Polling, because the stock macOS bash (3.2) has no "wait -n".
while true; do
  for pid in "${PIDS[@]}"; do
    kill -0 "$pid" 2>/dev/null || die "a port-forward exited; restart this script"
  done
  sleep 2
done
