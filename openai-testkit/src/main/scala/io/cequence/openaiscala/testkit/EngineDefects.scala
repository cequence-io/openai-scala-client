package io.cequence.openaiscala.testkit

import org.scalatest.Assertions

/**
 * A check an engine is known to fail, for a defect reported upstream (ws-client): on that
 * engine (`-Dws-client.engine`) the check is PENDING while it fails - and fails once it
 * passes, so the marker goes once the engine is fixed; on every other engine it is asserted as
 * usual.
 */
trait EngineDefects { this: Assertions =>

  protected def knownEngineDefect(
    engineId: String,
    defect: String
  )(
    check: => Any
  ): Unit =
    if (sys.props.get("ws-client.engine").contains(engineId)) {
      withClue(s"[$engineId: $defect] ")(pendingUntilFixed { check; () })
      ()
    } else {
      check
      ()
    }
}
