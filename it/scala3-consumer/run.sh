#!/usr/bin/env bash
# Publishes the library's Scala 3 artifacts the consumers use to a private Ivy home (never
# ~/.ivy2/local), then compiles the migration snippets with -feature -Werror and runs a stream per
# consumer.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
IVY_HOME="${IVY_HOME:-$(mktemp -d)}"
echo "Private Ivy home: $IVY_HOME"

# a ws-client SNAPSHOT exists only in the local Ivy repository: copy it in (read-only there)
WS_CLIENT="$(sed -n 's/.*val wsClient = "\([^"]*\)".*/\1/p' "$ROOT/project/Dependencies.scala")"
if [[ "$WS_CLIENT" == *-SNAPSHOT ]]; then
  for module in "$HOME"/.ivy2/local/io.cequence/*/"$WS_CLIENT"; do
    [[ -d "$module" ]] || continue
    target="$IVY_HOME/local/io.cequence/$(basename "$(dirname "$module")")"
    mkdir -p "$target"
    cp -r "$module" "$target/"
  done
fi

# only what the consumers depend on: client-stream (with client and core, which it aggregates)
# built with Scala 3.2.2, and the ZIO module built with 3.3
cd "$ROOT"
sbt -Dsbt.ivy.home="$IVY_HOME" \
  "++3.2.2" client_stream/publishLocal \
  "++3.3.8" zio/publishLocal

# clean: a snapshot republished under the same version would otherwise keep a cached classpath
cd "$ROOT/it/scala3-consumer"
sbt -Dsbt.ivy.home="$IVY_HOME" \
  clean \
  compile \
  "akka/runMain consumer.AkkaRoundTrip" \
  "pekko/runMain consumer.PekkoRoundTrip" \
  "fs2/runMain consumer.Fs2RoundTrip" \
  "zio/runMain consumer.ZioRoundTrip"
