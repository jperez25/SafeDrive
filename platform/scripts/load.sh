#!/usr/bin/env bash
# Runs Gatling load simulations against the local minikube stack.
#   load.sh <ingest|e2e|duplicates|all> [smoke|baseline|stress] [--mode incluster|nodeport|portforward]
#
# Modes (how the load reaches the services):
#   incluster    (default) Gatling runs as a pod in the cluster and calls the services by DNS: no host tunnel in the path.
#   nodeport     Gatling runs on this machine and calls the services through a NodePort. Where the node IP is not
#                reachable (Docker driver on macOS) the URLs come from `minikube service --url`, which is a tunnel.
#   portforward  Gatling runs on this machine through platform/scripts/port-forward.sh (must be running). Dev and smoke only:
#                the tunnel limits throughput and adds latency of its own.
# Reports: incluster -> integration-tests/load/target/gatling-cluster/<run>/, host modes -> integration-tests/load/target/gatling/.
# Exit code is non-zero when a simulation's assertions fail. Run the same profile through each mode to see what the path costs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NS=safedrive
MODE=incluster
POS=()

# Keeps only the last summary of a Gatling console log (it prints a progress block every few seconds).
summary() { awk '/^---- Global Information/ {buf=""} {buf = buf $0 "\n"} END {printf "%s", buf}' | grep -E "^(> (request count|response time|mean throughput)|> (OK|KO)|Global|[A-Za-z].*: .* is (less|greater)|\[INFO\] BUILD)"; }

log() { printf '==> %s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }
usage() { sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 1; }

while (($#)); do
  case "$1" in
    --mode) [[ $# -ge 2 ]] || usage; MODE="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) POS+=("$1"); shift ;;
  esac
done
SIM_ARG="${POS[0]:-}"; PROFILE="${POS[1]:-smoke}"
[[ -n "$SIM_ARG" ]] || usage
case "$MODE" in incluster|nodeport|portforward) ;; *) die "unknown mode '$MODE'" ;; esac
case "$PROFILE" in smoke|baseline|stress) ;; *) die "unknown profile '$PROFILE'" ;; esac

simulation_class() {
  case "$1" in
    ingest) echo IngestThroughputSimulation ;;
    e2e) echo EndToEndLatencySimulation ;;
    duplicates) echo DuplicatesUnderLoadSimulation ;;
    *) die "unknown simulation '$1' (ingest|e2e|duplicates|all)" ;;
  esac
}
if [[ "$SIM_ARG" == all ]]; then SIMS=(ingest e2e duplicates); else SIMS=("$SIM_ARG"); fi

command -v kubectl >/dev/null || die "kubectl not found"
[[ "$(kubectl config current-context 2>/dev/null || true)" == "minikube" ]] || die "kubectl context is not 'minikube'"
kubectl -n "$NS" get deployment telemetry-ingest policy-rating >/dev/null 2>&1 || die "stack not deployed (run: platform/scripts/deploy.sh all)"

build() {
  log "build load module"
  (cd "$ROOT" && ./mvnw -q -B -ntp -pl integration-tests/load -am install -DskipTests)
}

FAILED=()
record() { # record <sim> <exit code>
  if [[ "$2" == 0 ]]; then log "$1: PASSED"; else log "$1: FAILED (exit $2)"; FAILED+=("$1"); fi
}

run_incluster() {
  build
  log "copy classpath and build image"
  (
    cd "$ROOT"
    ./mvnw -q -B -ntp -pl integration-tests/load dependency:copy-dependencies -DincludeScope=test -DoutputDirectory=target/classpath
    # Relative paths on purpose, like deploy.sh: absolute ones make `minikube image build` build nothing.
    minikube image build -t safedrive/load:dev -f integration-tests/load/Dockerfile .
  )
  local sim cls out code waited
  for sim in "${SIMS[@]}"; do
    cls="$(simulation_class "$sim")"
    log "$sim: run $cls ($PROFILE) in the cluster"
    kubectl -n "$NS" delete pod load --ignore-not-found --wait=true >/dev/null
    sed "s/__SIMULATION__/$cls/; s/__PROFILE__/$PROFILE/" "$ROOT/platform/load/load-pod.yaml" | kubectl apply -f - >/dev/null
    kubectl -n "$NS" wait --for=condition=Ready pod/load --timeout=120s >/dev/null
    waited=0
    until kubectl -n "$NS" exec load -- test -f /results/.done 2>/dev/null; do
      [[ "$(kubectl -n "$NS" get pod load -o jsonpath='{.status.phase}')" != Failed ]] || { kubectl -n "$NS" logs load | tail -30; die "load pod failed"; }
      sleep 5; waited=$((waited + 5)); ((waited < 1800)) || die "timed out waiting for $cls"
    done
    out="$ROOT/integration-tests/load/target/gatling-cluster/$sim-$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$out"
    kubectl -n "$NS" cp load:/results "$out" >/dev/null
    kubectl -n "$NS" logs load | summary || true
    code="$(cat "$out/exit-code")"
    kubectl -n "$NS" delete pod load --wait=false >/dev/null
    log "$sim: report in ${out#"$ROOT"/}/"
    record "$sim" "$code"
  done
}

run_host() { # run_host <env> [extra -D options...]
  local env="$1"; shift
  build
  local sim cls code
  for sim in "${SIMS[@]}"; do
    cls="$(simulation_class "$sim")"
    log "$sim: run $cls ($PROFILE) from this machine, mode $MODE"
    code=0
    (cd "$ROOT" && ./mvnw -B -ntp -pl integration-tests/load gatling:test \
        -Dgatling.simulationClass="com.qnt.safedrive.tests.load.$cls" -Dload.profile="$PROFILE" -Denv="$env" "$@" \
      | summary) || code=$?  # pipefail: mvn's status wins
    record "$sim" "$code"
  done
}

TUNNEL_PIDS=(); TUNNEL_LOGS=()
cleanup() {
  ((${#TUNNEL_PIDS[@]})) && kill "${TUNNEL_PIDS[@]}" 2>/dev/null || true
  ((${#TUNNEL_LOGS[@]})) && rm -f "${TUNNEL_LOGS[@]}" || true
}
trap cleanup EXIT

# tunnel_url <service>: starts `minikube service --url` for one service (it stays in the foreground, so in the background
# here) and prints its URL. One process per service, because with several services the output order is not the argument order.
tunnel_url() {
  local log url i
  log="$(mktemp)"; TUNNEL_LOGS+=("$log")
  minikube service -n "$NS" "$1" --url >"$log" 2>&1 &
  TUNNEL_PIDS+=("$!")
  for i in $(seq 1 30); do
    url="$(grep -m1 '^http://' "$log" || true)"
    [[ -n "$url" ]] && { echo "$url"; return; }
    sleep 1
  done
  die "no URL for $1 from minikube: $(cat "$log")"
}

run_nodeport() {
  kubectl apply -f "$ROOT/platform/load/nodeports.yaml" >/dev/null
  local ingest rating ip
  ip="$(minikube ip)"
  if curl -sf --max-time 3 "http://$ip:30081/actuator/health" >/dev/null 2>&1; then
    log "node $ip is reachable: using the NodePorts directly"
    ingest="http://$ip:30081"; rating="http://$ip:30084"
  else
    log "node $ip is not reachable from here: using 'minikube service --url' (a tunnel, keep this script running)"
    ingest="$(tunnel_url telemetry-ingest-nodeport)"
    rating="$(tunnel_url policy-rating-nodeport)"
  fi
  curl -sf --max-time 5 "$ingest/actuator/health" >/dev/null || die "ingest does not answer at $ingest"
  curl -sf --max-time 5 "$rating/actuator/health" >/dev/null || die "policy-rating does not answer at $rating"
  log "ingest $ingest, rating $rating"
  run_host local "-Dservice.telemetry-ingest.url=$ingest" "-Dservice.policy-rating.url=$rating"
}

run_portforward() {
  curl -sf --max-time 3 "http://localhost:8081/actuator/health" >/dev/null 2>&1 \
    || die "localhost:8081 does not answer: start platform/scripts/port-forward.sh first"
  run_host local
}

case "$MODE" in
  incluster) run_incluster ;;
  nodeport) run_nodeport ;;
  portforward) run_portforward ;;
esac

if ((${#FAILED[@]})); then die "failed: ${FAILED[*]}"; fi
log "all simulations passed"
