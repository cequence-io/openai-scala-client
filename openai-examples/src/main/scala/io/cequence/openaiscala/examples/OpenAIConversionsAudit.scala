package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.impl.ChatCompletionBodyMaker
import play.api.libs.json.{JsObject, JsValue, Json}

import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

/**
 * Audits the per-model parameter conversions (`ChatCompletionSettingsConversions` and the
 * routing in `ChatCompletionBodyMaker`) against the live chat completions API: for every model
 * and every "hostile" settings case (sampling params, `max_tokens`, every `reasoning_effort`,
 * tools, verbosity, JSON schema, system / developer messages) it records
 *
 *   - the body the client builds AFTER its conversions (`sent`), and
 *   - whether the real client call succeeds (`ok` / the error).
 *
 * A failing case is a missing conversion; comparing `sent` with the raw acceptance of the
 * unconverted parameter (probe it separately) shows over-eager conversions. Writes one JSON
 * line per (model, case) to the file given as the first argument; the models follow.
 *
 * Run: `OpenAIConversionsAudit out.jsonl gpt-5.4 gpt-5.5 o3 ...` (needs
 * `OPENAI_SCALA_CLIENT_API_KEY`; every call is a few tokens; `AUDIT_PARALLELISM` caps the
 * concurrent calls, default 8).
 */
object OpenAIConversionsAudit {

  private object bodyMaker extends ChatCompletionBodyMaker {
    def body(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): JsObject =
      JsObject(
        createBodyParamsForChatCompletion(messages, settings, stream = false).collect {
          case (param, Some(json)) => param.toString -> json
        } ++ settings.extra_params.map { case (k, v) =>
          k -> io.cequence.wsclient.JsonUtil.toJson(v)
        }
      )

    def toolSettings(settings: CreateChatCompletionSettings): CreateChatCompletionSettings =
      settingsForChatToolCompletion(settings)
  }

  private val user = Seq(UserMessage("Say hi."))

  private val weather = FunctionTool(
    name = "get_weather",
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String()),
      required = Seq("city")
    )
  )

  private val schema = JsonSchemaDef(
    "r",
    strict = true,
    JsonSchema.Object(properties = Seq("a" -> JsonSchema.String()), required = Seq("a"))
  )

  // (case, messages, settings(model), tools)
  private def cases(model: String)
    : Seq[(String, Seq[BaseMessage], CreateChatCompletionSettings, Boolean)] = {
    val base = CreateChatCompletionSettings(model, max_tokens = Some(2000))
    def effort(e: ReasoningEffort) = base.copy(reasoning_effort = Some(e))
    Seq(
      ("plain", user, base, false),
      ("system_msg", SystemMessage("Be brief.") +: user, base, false),
      ("developer_msg", DeveloperMessage("Be brief.") +: user, base, false),
      ("temperature_0.2", user, base.copy(temperature = Some(0.2)), false),
      ("top_p_0.5", user, base.copy(top_p = Some(0.5)), false),
      ("presence_0.5", user, base.copy(presence_penalty = Some(0.5)), false),
      ("frequency_0.5", user, base.copy(frequency_penalty = Some(0.5)), false),
      ("logprobs", user, base.copy(logprobs = Some(true)), false),
      ("re_none", user, effort(ReasoningEffort.none), false),
      ("re_minimal", user, effort(ReasoningEffort.minimal), false),
      ("re_low", user, effort(ReasoningEffort.low), false),
      ("re_medium", user, effort(ReasoningEffort.medium), false),
      ("re_high", user, effort(ReasoningEffort.high), false),
      ("re_xhigh", user, effort(ReasoningEffort.xhigh), false),
      ("re_max", user, effort(ReasoningEffort.max), false),
      (
        "temp0.2+re_low",
        user,
        base.copy(temperature = Some(0.2), reasoning_effort = Some(ReasoningEffort.low)),
        false
      ),
      ("tools", Seq(UserMessage("Weather in Oslo?")), base, true),
      (
        "tools+re_low",
        Seq(UserMessage("Weather in Oslo?")),
        effort(ReasoningEffort.low),
        true
      ),
      (
        "tools+parallel_false",
        Seq(UserMessage("Weather in Oslo?")),
        base.copy(parallel_tool_calls = Some(false)),
        true
      ),
      ("verbosity_low", user, base.copy(verbosity = Some(Verbosity.low)), false),
      (
        "json_schema",
        user,
        base.copy(
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(schema)
        ),
        false
      )
    )
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher

    val out = new PrintWriter(args.head)
    val models = args.tail.toSeq
    val service = OpenAIServiceFactory()
    val done = new AtomicInteger(0)

    def record(
      model: String,
      name: String,
      sent: JsValue,
      result: Try[Unit]
    ): Unit = synchronized {
      val line = Json.obj(
        "model" -> model,
        "case" -> name,
        "sent" -> sent,
        "ok" -> result.isSuccess,
        "error" -> result.failed.toOption.map(e =>
          s"${e.getClass.getSimpleName}: ${e.getMessage.take(200)}"
        )
      )
      out.println(Json.stringify(line))
      out.flush()
    }

    // bounded parallelism (the API rate-limits bursts; a 429 would read as a missing conversion)
    val parallelism = sys.env.get("AUDIT_PARALLELISM").map(_.toInt).getOrElse(8)
    val allCases = models.flatMap(model => cases(model).map(model -> _))

    val all = Source(allCases.toList)
      .mapAsyncUnordered(parallelism) { case (model, (name, messages, settings, withTools)) =>
        val sent =
          if (withTools) bodyMaker.body(messages, bodyMaker.toolSettings(settings))
          else bodyMaker.body(messages, settings)
        val call: Future[Unit] =
          if (withTools)
            service
              .createChatToolCompletion(messages, Seq(weather), settings = settings)
              .map(_ => ())
          else service.createChatCompletion(messages, settings).map(_ => ())
        call.transform { result =>
          record(model, name, sent, result)
          if (done.incrementAndGet() % 50 == 0) println(s"${done.get} cases done")
          Success(())
        }
      }
      .runWith(Sink.ignore)

    Try(Await.result(all, 60.minutes))
    out.close()
    service.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(0)
  }
}
