#!/usr/bin/env bash
# Publishes the library's Scala 3 artifacts to a private Ivy home (never ~/.ivy2/local), then
# compiles the migration snippets with -feature -Werror and runs a stream per consumer.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
IVY_HOME="${IVY_HOME:-$(mktemp -d)}"
echo "Private Ivy home: $IVY_HOME"

cd "$ROOT"
sbt -Dsbt.ivy.home="$IVY_HOME" \
  "++3.2.2" "project ci_scala3" publishLocal \
  "++3.3.8" "project ci_scala33" publishLocal

cd "$ROOT/it/scala3-consumer"
# clean: a snapshot republished under the same version would otherwise keep a cached classpath
sbt -Dsbt.ivy.home="$IVY_HOME" \
  clean \
  compile \
  "akka/runMain consumer.AkkaRoundTrip" \
  "pekko/runMain consumer.PekkoRoundTrip" \
  "fs2/runMain consumer.Fs2RoundTrip" \
  "zio/runMain consumer.ZioRoundTrip"
