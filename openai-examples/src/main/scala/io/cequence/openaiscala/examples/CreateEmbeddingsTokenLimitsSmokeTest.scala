package io.cequence.openaiscala.examples

import io.cequence.openaiscala.OpenAIScalaTokenCountExceededException
import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.settings.CreateEmbeddingsSettings
import io.cequence.openaiscala.service.OpenAIServiceFactory

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live check that both of the embeddings endpoint's token limits fail as the typed
 * `OpenAIScalaTokenCountExceededException` (a batch splitter relies on the type, not on the
 * message): one input over 8,192 tokens ("maximum input length is 8192 tokens"), and a request
 * over 300,000 tokens in all (`max_tokens_per_request`, typed since 1.5.0). Both are 400s, so
 * nothing is billed; a small request at the end succeeds. Prints the whole error bodies.
 *
 * Every section prints PASS/FAIL; the exit code is 1 if any failed. Requires
 * `OPENAI_SCALA_CLIENT_API_KEY`.
 */
object CreateEmbeddingsTokenLimitsSmokeTest {

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val service = OpenAIServiceFactory()
    val settings = CreateEmbeddingsSettings(ModelId.text_embedding_3_small)
    val failures = new AtomicInteger(0)

    def section(
      name: String
    )(
      f: => Future[String]
    ): Future[Unit] =
      Try(f).fold(Future.failed, identity).transform {
        case Success(msg) =>
          println(s"[PASS] $name: $msg")
          Success(())
        case Failure(e) =>
          failures.incrementAndGet()
          println(s"[FAIL] $name: ${e.getClass.getSimpleName}: ${e.getMessage}")
          Success(())
      }

    // " hello" is one token: ~9,000 tokens in one input, ~4,000 tokens per input in a batch.
    // The per-input limit's wording depends on the input's shape (live 2026-10-09): a lone
    // string gets "Invalid 'input': maximum context length is 8192 tokens.", an array
    // "Invalid 'input[0]': maximum input length is 8192 tokens." - both typed
    val oneInputTooLong = Seq("hello " * 9000)
    val oneOfTwoTooLong = Seq("hello " * 9000, "hello")
    val requestTooLong = Seq.fill(100)("hello " * 4000) // ~400k tokens in all

    def expectTokenLimit(
      inputs: Seq[String],
      mustMention: String
    ): Future[String] =
      service.createEmbeddings(inputs, settings).transform {
        case Failure(e: OpenAIScalaTokenCountExceededException)
            if e.getMessage.contains(mustMention) =>
          Success(s"${e.getClass.getSimpleName}: ${e.getMessage}")
        case Failure(e) =>
          Failure(
            new RuntimeException(
              s"not the typed exception: ${e.getClass.getName}: ${e.getMessage}"
            )
          )
        case Success(response) =>
          Failure(
            new RuntimeException(s"unexpectedly succeeded: ${response.data.size} embeddings")
          )
      }

    val all = for {
      _ <- section("one input over 8,192 tokens -> OpenAIScalaTokenCountExceededException")(
        expectTokenLimit(oneInputTooLong, "8192 tokens")
      )
      _ <- section(
        "one of two inputs over 8,192 tokens -> OpenAIScalaTokenCountExceededException"
      )(
        expectTokenLimit(oneOfTwoTooLong, "8192 tokens")
      )
      _ <- section("request over 300k tokens -> OpenAIScalaTokenCountExceededException")(
        expectTokenLimit(requestTooLong, "max_tokens_per_request")
      )
      _ <- section("a small request succeeds") {
        service.createEmbeddings(Seq("hello"), settings).map { response =>
          s"${response.data.size} embedding of ${response.data.head.embedding.size} dimensions"
        }
      }
    } yield ()

    Try(Await.result(all, 3.minutes)).failed.foreach { e =>
      failures.incrementAndGet()
      println(s"[FAIL] the run did not complete: $e")
    }
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    service.close()
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
