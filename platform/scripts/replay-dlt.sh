#!/usr/bin/env bash
# Replays dead-lettered events: copies every new message from <topic>.<group>.dlt back to <topic>.
#   replay-dlt.sh <topic>.<group>.dlt        e.g. trip.raw.trip-processor.dlt
# Key and value (the original envelope: same eventId, same correlationId) are kept as they were.
# Progress is tracked by the consumer group "dlt-replay-<dlt topic>", so a second run only copies
# messages that arrived since the first. Fix the cause of the failure before replaying.
set -euo pipefail

NS=safedrive
KAFKA_BIN=/opt/kafka/bin
BOOTSTRAP=localhost:9092
TAB=$'\t'

[[ $# -eq 1 && "$1" == *.dlt ]] || { echo "usage: replay-dlt.sh <topic>.<group>.dlt" >&2; exit 1; }
DLT="$1"
SRC="${DLT%.*.dlt}"            # strip ".<group>.dlt"
[[ "$SRC" != "$DLT" && -n "$SRC" ]] || { echo "error: cannot derive the source topic from '$DLT'" >&2; exit 1; }
GROUP="dlt-replay-$DLT"

COUNT_FILE="$(mktemp)"; ERR_FILE="$(mktemp)"; trap 'rm -f "$COUNT_FILE" "$ERR_FILE"' EXIT
echo "==> replaying $DLT -> $SRC"
# The consumer stops by idling until --timeout-ms, which Kafka 4 reports as a TimeoutException with exit code 1.
# That is the normal end; any other error is real and fails the script (with the consumer's message).
{ kubectl -n "$NS" exec kafka-0 -- "$KAFKA_BIN/kafka-console-consumer.sh" \
    --bootstrap-server "$BOOTSTRAP" --topic "$DLT" --group "$GROUP" --from-beginning --timeout-ms 10000 \
    --formatter-property print.key=true --formatter-property "key.separator=$TAB" 2>"$ERR_FILE" \
    || { grep -q TimeoutException "$ERR_FILE" || { cat "$ERR_FILE" >&2; exit 1; }; }; } \
  | { grep -F "$TAB" || true; } \
  | tee "$COUNT_FILE" \
  | kubectl -n "$NS" exec -i kafka-0 -- "$KAFKA_BIN/kafka-console-producer.sh" \
      --bootstrap-server "$BOOTSTRAP" --topic "$SRC" \
      --reader-property parse.key=true --reader-property "key.separator=$TAB" >/dev/null
echo "==> replayed $(wc -l < "$COUNT_FILE" | tr -d ' ') message(s)"
