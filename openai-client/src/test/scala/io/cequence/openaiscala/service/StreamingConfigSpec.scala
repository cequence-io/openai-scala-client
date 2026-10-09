package io.cequence.openaiscala.service

import com.typesafe.config.{ConfigFactory, ConfigResolveOptions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class StreamingConfigSpec extends AnyWordSpec with Matchers {

  // the config file shipped in this module (the test resources carry one of their own)
  private lazy val shippedFile = {
    val urls = getClass.getClassLoader.getResources("openai-scala-client.conf")
    Iterator
      .continually(urls)
      .takeWhile(_.hasMoreElements)
      .map(_.nextElement())
      .find(!_.toString.contains("test-classes"))
      .getOrElse(fail("The shipped openai-scala-client.conf is not on the classpath."))
  }

  // the shipped config, resolved against the given env variables only
  private def shipped(env: (String, String)*) =
    ConfigFactory
      .parseURL(shippedFile)
      .resolveWith(
        ConfigFactory.parseString(
          env.map { case (name, value) => s"""$name = "$value"""" }.mkString("\n")
        ),
        ConfigResolveOptions.defaults().setUseSystemEnvironment(false)
      )

  "The shipped config" should {

    "leave the stream frame cap at its default" in {
      StreamingConsts.maxFrameLengthFrom(
        shipped()
      ) shouldBe StreamingConsts.DefaultMaxFrameLength
    }

    "take the cap from the env variable" in {
      StreamingConsts.maxFrameLengthFrom(
        shipped(StreamingConsts.MaxFrameLengthEnvVariable -> "128MiB")
      ) shouldBe 128 * 1024 * 1024
    }
  }
}
