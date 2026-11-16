package io.cequence.openaiscala.service

import com.typesafe.config.Config
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.{URL, URLClassLoader}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.Try

// The client config is loaded on whichever thread first needs it. A pool thread of an in-process
// sbt `run` carries a context class loader that sees neither the library nor its config file, and
// a thread may have none at all (ConfigFactory.load then throws).
class HasOpenAIConfigSpec extends AnyWordSpec with Matchers {

  private val shippedKey = "openai-scala-client.models-supporting-json-schema"

  // a new holder per load - clientConfig is a lazy val
  private final class ConfigHolder extends HasOpenAIConfig {
    def config: Config = clientConfig
  }

  private def loadOn(contextClassLoader: ClassLoader): Config = {
    val loaded = Promise[Config]()
    val thread = new Thread(() => {
      loaded.complete(Try(new ConfigHolder().config))
      ()
    })
    thread.setContextClassLoader(contextClassLoader)
    thread.start()
    Await.result(loaded.future, 10.seconds)
  }

  "The client config" should {

    "load on a thread without a context class loader" in {
      loadOn(null).hasPath(shippedKey) shouldBe true
    }

    "load on a thread whose context class loader cannot see the library" in {
      val foreign = new URLClassLoader(Array.empty[URL], null)
      try loadOn(foreign).hasPath(shippedKey) shouldBe true
      finally foreign.close()
    }

    "read the context class loader's own config file when it sees the library" in {
      val dir = Files.createTempDirectory("has-openai-config-spec")
      val file = dir.resolve("openai-scala-client.conf")
      Files.write(
        file,
        "openai-scala-client.specMarker = child".getBytes(StandardCharsets.UTF_8)
      )
      val child = new URLClassLoader(Array(dir.toUri.toURL), getClass.getClassLoader)
      try {
        val config = loadOn(child)
        config.getString("openai-scala-client.specMarker") shouldBe "child"
        config.hasPath(shippedKey) shouldBe true
      } finally {
        child.close()
        Files.delete(file)
        Files.delete(dir)
      }
    }
  }
}
