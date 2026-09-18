package io.cequence.openaiscala.examples.typesafe

import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, JsonSchemaDef}
import io.cequence.openaiscala.domain.{JsonSchema, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.typesafe.domain.{SystemOneResponse, TypeSafeModelId}
import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory
import play.api.libs.json.{Format, Json}

import java.net.{HttpURLConnection, URL}
import scala.concurrent.Future
import scala.io.Source

/**
 * Line-by-line semantic search over GitHub's Terms of Service with Jev, after the TypeSafe
 * cookbook https://docs.typesafe.ai/cookbooks/semantic_find - through the OpenAI
 * chat-completion adapter.
 *
 * The 218 lines of the document are tagged with ids (`L000| ...`) and sent ONCE as the state;
 * two questions score them all in a single ~100 ms request:
 *
 *   - `where` - a string enum over the 218 line ids (a choice question): "which line contains
 *     the answer?" - its probabilities rank every line by relevance
 *   - `exists` - a boolean (a noul question): "does any line address the query at all?"
 *
 * Choice probabilities always sum to 1, so the top line is the CLOSEST one even when the
 * document does not answer the query - the independent `exists` probability tells the two
 * apart (answered / partially addressed / not in this document).
 *
 * The typed answer comes back as `Find`; the calibrated probabilities behind it ride in
 * `originalResponse` as the raw `SystemOneResponse`. Requires `TYPESAFE_API_KEY`.
 */
object TypeSafeSemanticFind extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = TypeSafeServiceFactory.asOpenAI()

  private val documentUrl =
    "https://gist.githubusercontent.com/eugene-shvarts/900632789a24983d5678ffd508dd01f6/raw"

  private val queries = Seq(
    "who owns the code I upload?",
    "can GitHub kick me off without warning?",
    "do I have to take disputes to arbitration?",
    "can minors use GitHub with parental permission?"
  )

  // exists >= Found -> answered, < Absent -> not in the document, in between -> partial
  private val Found = 0.7
  private val Absent = 0.35

  private case class Find(
    where: String,
    exists: Boolean
  )

  private implicit val findFormat: Format[Find] = Json.format[Find]

  private lazy val lines: IndexedSeq[String] = fetch(documentUrl).linesIterator.toIndexedSeq

  private lazy val lineIds: IndexedSeq[String] = lines.indices.map(lineId)

  // "L052| You own Your Content. ..." - the ids the choice question refers to
  private lazy val document: String =
    lines.zip(lineIds).map { case (line, id) => s"$id| $line" }.mkString("\n")

  private def lineId(i: Int) = f"L$i%03d"

  private def schema(query: String) = JsonSchemaDef(
    name = "find",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "where" -> JsonSchema.String(
            description =
              Some(s"""Which line of the document contains the answer to: "$query"?"""),
            `enum` = lineIds
          ),
          "exists" -> JsonSchema.Boolean(
            Some(
              s"""Does any line of the document address or answer: "$query"? """ +
                "True when at least one line states or directly implies the answer, " +
                "false when no line addresses this."
            )
          )
        ),
        required = Seq("where", "exists")
      )
    )
  )

  override protected def run: Future[_] = {
    println(s"document : ${lines.size} lines, ${document.length} chars")

    queries.foldLeft(Future.successful(())) {
      (
        acc,
        query
      ) =>
        acc.flatMap(_ => find(query))
    }
  }

  private def find(query: String): Future[Unit] =
    service
      .createChatCompletionWithJSONFullResponse[Find](
        Seq(UserMessage(document)),
        CreateChatCompletionSettings(model = TypeSafeModelId.jev_latest)
          .withJsonSchema(schema(query))
      )
      .map { case (find, response) =>
        val systemOne = response.originalResponse.collect { case r: SystemOneResponse =>
          r
        }.get
        val exists = systemOne.noul("exists").noul
        val relevance = systemOne.choice("where").probabilities

        println(s"""\n"$query"""")
        println(f"  exists $exists%.2f -> ${verdict(exists)}  (typed: $find)")

        relevance.toSeq.sortBy(-_._2).take(4).foreach { case (id, p) =>
          val bar = "#" * math.max(1, math.round(p * 12).toInt)
          val preview = lines(id.drop(1).toInt).take(58).trim
          println(f"  $id  $p%.2f  $bar%-12s  $preview")
        }
      }

  private def verdict(exists: Double): String =
    if (exists >= Found) "answered in this document"
    else if (exists < Absent) "not in this document"
    else "partially addressed"

  private def fetch(url: String): String = {
    val connection = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    connection.setRequestProperty("User-Agent", "openai-scala-client-example/1.0")
    val source = Source.fromInputStream(connection.getInputStream, "UTF-8")
    try source.mkString
    finally source.close()
  }
}
