package io.cequence.openaiscala.examples.typesafe

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.domain.decisions.{
  CreateDecisionSettings,
  DecisionAnswer,
  DecisionContent,
  DecisionInput,
  DecisionMessage,
  DecisionQuestion
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ImageURLContent,
  JsonSchemaDescription,
  TextContent,
  UserSeqMessage
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.{
  DecisionProviderSettings,
  TypeSafeServiceFactory
}
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import io.cequence.wsclient.service.ws.Timeouts
import play.api.libs.json.{Format, Json}

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Do the decision hosts read an image through both OpenAI-shaped adapters of
 * `TypeSafeServiceFactory`? One red circle on white (a 512 x 512 PNG) and three yes / no
 * questions - is the shape red, blue, a circle - for every target whose variables are set,
 * through:
 *
 *   - the JSON schema chat adapter: `asOpenAI(service)` +
 *     `createChatCompletionWithJSON[Seen]`, the image in the user message, the probabilities
 *     read from the `*_confidence` fields
 *   - OpenAI's Decisions interface: `asOpenAIDecisions(service)` + `createDecision`, the image
 *     as an `input_image`
 *
 * A model reads the image when P(red) >= 0.7, P(blue) <= 0.3 and P(circle) >= 0.7; one that
 * reads it as text answers near 0.5 or at random. Each target has an expectation: `reads` (an
 * image model, so both adapters must see the circle), `refuses` (a provider that reads no
 * images, so the adapter must refuse before sending) or `text-only` (an image-reading host
 * serving a model that does not read them, only recorded: images are allowed per host, not per
 * model). An answer the host gave instead (a 402 without credits, a 403 of a free tier) is
 * reported, not judged.
 *
 * Targets and their variables: TypeSafe's Jev 1.13 and 1.14 (`TYPESAFE_API_KEY`, assets),
 * Perplexity's decider (`PERPLEXITY_API_KEY`, parts in the state), OpenAI's Luna
 * (`OPENAI_SCALA_CLIENT_API_KEY`, `input_image`), Liquid's `d1` and `d1-ultra`
 * (`LIQUID_API_KEY`, the `images` array), SubQ Snap (`SUBQ_API_KEY`, image objects),
 * ScaleDown's Classify-1 (`SCALEDOWN_API_KEY`, the state's document), OpenRouter's Mercury
 * Decide and Span-01 Lite free tiers (`OPENROUTER_API_KEY`), Vercel's AI Gateway with
 * Perplexity's decider, ScaleDown and Laya (`AI_GATEWAY_API_KEY`, paced 13 s apart for a free
 * tier) and Microsoft-Decision-1 (`FOUNDRY_API_KEY` + `FOUNDRY_BASE_URL` + `FOUNDRY_MODEL`, no
 * images). Arguments: `only=<host,...>`, `models=<substring,...>`. Exit code 1 if an
 * expectation is not met.
 */
object DecisionImageAdaptersSmokeTest {

  // what the JSON schema adapter is asked: a noul per boolean, its confidence filled in
  final case class Seen(
    @JsonSchemaDescription("Is the shape in the image red?") red: Boolean,
    red_confidence: Double,
    @JsonSchemaDescription("Is the shape in the image blue?") blue: Boolean,
    blue_confidence: Double,
    @JsonSchemaDescription("Is the shape in the image a circle?") circle: Boolean,
    circle_confidence: Double
  )
  private implicit val seenFormat: Format[Seen] = Json.format[Seen]

  private sealed trait Expect
  private case object Reads extends Expect
  private case object TextOnly extends Expect
  private case object Refuses extends Expect

  private final case class Target(
    host: String,
    model: String,
    expect: Expect,
    env: Seq[String],
    provider: () => DecisionProvider,
    paceMs: Long = 0L
  )

  private final case class Probabilities(
    red: Double,
    blue: Double,
    circle: Double
  ) {
    def readsImage: Boolean = red >= 0.7 && blue <= 0.3 && circle >= 0.7
    override def toString: String = f"red $red%.2f, blue $blue%.2f, circle $circle%.2f"
  }

  private sealed trait Outcome
  private final case class Answered(probabilities: Probabilities) extends Outcome
  private final case class Refused(message: String) extends Outcome
  private final case class Failed(error: Throwable) extends Outcome

  private val RefusalMarkers =
    Seq("Image content goes only", "reads no images", "Images go only")

  private val redCircle: String = {
    val image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setColor(Color.WHITE)
    graphics.fillRect(0, 0, 512, 512)
    graphics.setColor(Color.RED)
    graphics.fillOval(128, 128, 256, 256)
    graphics.dispose()
    val bytes = new ByteArrayOutputStream()
    ImageIO.write(image, "png", bytes)
    "data:image/png;base64," + Base64.getEncoder.encodeToString(bytes.toByteArray)
  }

  private val targets: Seq[Target] = {
    val typeSafeKey = Seq("TYPESAFE_API_KEY")
    val openRouterKey = Seq("OPENROUTER_API_KEY")
    val vercelKey = Seq("AI_GATEWAY_API_KEY")
    Seq(
      Target(
        "typesafe",
        "jev-latest",
        Reads,
        typeSafeKey,
        () => DecisionProviderSettings.typeSafe
      ),
      Target(
        "typesafe",
        "jev-1.14.0",
        Reads,
        typeSafeKey,
        () => DecisionProviderSettings.typeSafe
      ),
      Target(
        "perplexity",
        "pplx-decider-v1.1-27b",
        Reads,
        Seq("PERPLEXITY_API_KEY"),
        () => DecisionProviderSettings.perplexity
      ),
      Target(
        "openai",
        "gpt-6-luna",
        Reads,
        Seq("OPENAI_SCALA_CLIENT_API_KEY"),
        () => DecisionProviderSettings.openAI
      ),
      Target(
        "liquid",
        "d1",
        Reads,
        Seq("LIQUID_API_KEY"),
        () => DecisionProviderSettings.liquid
      ),
      Target(
        "liquid",
        "d1-ultra",
        Reads,
        Seq("LIQUID_API_KEY"),
        () => DecisionProviderSettings.liquid
      ),
      Target(
        "subq",
        "subq-snap-preview",
        Reads,
        Seq("SUBQ_API_KEY"),
        () => DecisionProviderSettings.subq
      ),
      Target(
        "scaledown",
        "classify-1",
        Reads,
        Seq("SCALEDOWN_API_KEY"),
        () => DecisionProviderSettings.scaleDown
      ),
      Target(
        "openrouter",
        "inception/mercury-decide:free",
        Reads,
        openRouterKey,
        () => DecisionProviderSettings.openRouter
      ),
      Target(
        "openrouter",
        "respan/span-01-lite:free",
        TextOnly,
        openRouterKey,
        () => DecisionProviderSettings.openRouter
      ),
      Target(
        "vercel",
        "perplexity/pplx-decider-v1.1-27b",
        Reads,
        vercelKey,
        () => DecisionProviderSettings.vercel,
        paceMs = 13000L
      ),
      Target(
        "vercel",
        "scaledown/scaledown-classify-1",
        Reads,
        vercelKey,
        () => DecisionProviderSettings.vercel,
        paceMs = 13000L
      ),
      Target(
        "vercel",
        "convaiinnovations/laya-free",
        TextOnly,
        vercelKey,
        () => DecisionProviderSettings.vercel,
        paceMs = 13000L
      ),
      Target(
        "microsoft-foundry",
        sys.env.getOrElse("FOUNDRY_MODEL", DecisionProviderSettings.FoundryDefaultDeployment),
        Refuses,
        Seq("FOUNDRY_API_KEY", "FOUNDRY_BASE_URL"),
        () => DecisionProviderSettings.microsoftFoundry(sys.env("FOUNDRY_BASE_URL"))
      )
    )
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val scheduler: Scheduler = system.scheduler

    def arg(name: String): Option[String] =
      args.find(_.startsWith(name + "=")).map(_.drop(name.length + 1))
    def list(name: String): Seq[String] =
      arg(name).toSeq.flatMap(_.split(',')).map(_.trim).filter(_.nonEmpty)

    val only = list("only")
    val models = list("models")
    val engine = WSClientEngineRegistry(
      TransportSettings(timeouts =
        Timeouts(requestTimeout = Some(90000), readTimeout = Some(90000))
      )
    )

    def outcome(call: => Future[Probabilities]): Outcome =
      Try(Await.result(Try(call).fold(Future.failed, identity), 3.minutes)) match {
        case Success(probabilities) => Answered(probabilities)
        case Failure(e)
            if RefusalMarkers
              .exists(marker => Option(e.getMessage).exists(_.contains(marker))) =>
          Refused(e.getMessage)
        case Failure(e) => Failed(e)
      }

    def describe(result: Outcome): String = result match {
      case Answered(p) =>
        s"${if (p.readsImage) "reads the image" else "does NOT read it"} ($p)"
      case Refused(_) => "refused before sending"
      case Failed(e) =>
        s"failed: ${e.getClass.getSimpleName}: " +
          Option(e.getMessage).getOrElse("").replaceAll("\\s+", " ").take(140)
    }

    // an expectation not met is a failure; an answer the host gave instead (credits, a free
    // tier, an outage) is reported but not judged
    def met(
      expect: Expect,
      result: Outcome
    ): Option[Boolean] = (expect, result) match {
      case (Reads, Answered(p))   => Some(p.readsImage)
      case (Reads, Refused(_))    => Some(false)
      case (Refuses, Refused(_))  => Some(true)
      case (Refuses, Answered(_)) => Some(false)
      case (TextOnly, _)          => None
      case (_, Failed(_))         => None
    }

    val selected = targets.filter { target =>
      (only.isEmpty || only.contains(target.host)) &&
      (models.isEmpty || models.exists(target.model.contains(_)))
    }

    var unmet = 0
    selected.foreach { target =>
      val missing = target.env.filterNot(name => sys.env.get(name).exists(_.trim.nonEmpty))
      if (missing.nonEmpty)
        println(s"[SKIP] ${target.host} / ${target.model}: ${missing.mkString(", ")} not set")
      else {
        val service = TypeSafeServiceFactory.withEngine(
          engine,
          target.provider().copy(defaultModel = target.model)
        )

        def paced[T](f: => T): T = {
          if (target.paceMs > 0) Thread.sleep(target.paceMs)
          f
        }

        val chat = paced(outcome {
          TypeSafeServiceFactory
            .asOpenAI(service)
            .createChatCompletionWithJSON[Seen](
              Seq(UserSeqMessage(Seq(TextContent("A shape."), ImageURLContent(redCircle)))),
              CreateChatCompletionSettings(target.model)
            )
            .map { seen =>
              def probability(
                answer: Boolean,
                confidence: Double
              ) = if (answer) confidence else 1 - confidence
              Probabilities(
                probability(seen.red, seen.red_confidence),
                probability(seen.blue, seen.blue_confidence),
                probability(seen.circle, seen.circle_confidence)
              )
            }
        })

        val decisions = paced(outcome {
          TypeSafeServiceFactory
            .asOpenAIDecisions(service)
            .createDecision(
              DecisionInput.Messages(
                Seq(
                  DecisionMessage(
                    Seq(
                      DecisionContent.InputText("A shape."),
                      DecisionContent.InputImage(redCircle)
                    )
                  )
                )
              ),
              Seq(
                DecisionQuestion.Predicate("Is the shape in the image red?", Some("red")),
                DecisionQuestion.Predicate("Is the shape in the image blue?", Some("blue")),
                DecisionQuestion
                  .Predicate("Is the shape in the image a circle?", Some("circle"))
              ),
              CreateDecisionSettings(model = Some(target.model))
            )
            .map { decision =>
              def probability(name: String) = decision.answer(name) match {
                case Some(DecisionAnswer.Predicate(_, p)) => p
                case other => throw new IllegalStateException(s"$name answered $other")
              }
              Probabilities(probability("red"), probability("blue"), probability("circle"))
            }
        })

        val verdicts = Seq(met(target.expect, chat), met(target.expect, decisions))
        val status =
          if (verdicts.contains(Some(false))) { unmet += 1; "FAIL" }
          else if (verdicts.forall(_.contains(true))) "PASS"
          else "INFO"
        val expectation = target.expect match {
          case Reads    => "an image model"
          case TextOnly => "a text-only model on an image host"
          case Refuses  => "a host without images"
        }
        println(s"[$status] ${target.host} / ${target.model} ($expectation)")
        println(s"    chat adapter (asOpenAI):          ${describe(chat)}")
        println(s"    decisions (asOpenAIDecisions):    ${describe(decisions)}")
        service.close()
      }
    }

    println(s"Summary: ${selected.size} targets, $unmet with an expectation not met")
    engine.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (unmet > 0) 1 else 0)
  }
}
