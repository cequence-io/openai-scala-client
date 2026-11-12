# Scala 3 consumer check

A separate sbt build that consumes the library like an application on Scala 3 does: the
published `_3` artifacts (built with Scala 3.2.2; `openai-scala-zio`'s with 3.3) from Scala 3.2.2
with the Akka engine and from Scala 3.3 with Pekko, fs2 and ZIO.

- Compiles the "after" snippets of [Migrating to 2.0](../../docs/migration-2.0.md) with
  `-feature -Werror` - keep the copies in `*/src/main/scala` in step with the page.
- Runs a stream per consumer against a local server and checks its text and bridge count
  (0 on the Akka / Pekko engines, 1 on `jdk` into fs2 / ZIO).

`./run.sh` publishes the library to a private Ivy home first (a temporary directory, or
`IVY_HOME`), so `~/.ivy2/local` is never touched. CI runs it in the job "Scala 3 Consumer".
