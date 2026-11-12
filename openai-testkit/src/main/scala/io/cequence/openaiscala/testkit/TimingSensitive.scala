package io.cequence.openaiscala.testkit

import org.scalatest.Tag

/**
 * A test that asserts a time bound (e.g. a decoder's linear time). Coverage instrumentation
 * slows hot loops several times over, so coverage runs leave these out (build.sbt,
 * commonSettings); every other run keeps them.
 */
object TimingSensitive extends Tag("io.cequence.openaiscala.testkit.TimingSensitive")
