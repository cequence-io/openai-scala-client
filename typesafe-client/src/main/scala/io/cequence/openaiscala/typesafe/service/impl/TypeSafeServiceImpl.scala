package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.ResponseHeaders
import io.cequence.openaiscala.domain.decisions.JsonFormats.{createDecisionBody, decisionReads}
import io.cequence.openaiscala.domain.decisions.{
  DecisionContent,
  CreateDecisionSettings,
  Decision,
  DecisionInput,
  DecisionQuestion
}
import io.cequence.openaiscala.service.OpenAIDecisionsService
import io.cequence.openaiscala.typesafe.JsonFormats._
import io.cequence.openaiscala.typesafe.domain.{
  BoxQuestion,
  DecisionImage,
  DecisionImages,
  DecisionModelListing,
  DecisionProtocol,
  DecisionProvider,
  ModelMetadata,
  Question,
  SystemOneRequest,
  SystemOneResponse
}
import io.cequence.openaiscala.typesafe.service.{
  DecisionProviderSettings,
  HandleTypeSafeErrorCodes,
  TypeSafeScalaApiUsageException,
  TypeSafeScalaClientTimeoutException,
  TypeSafeScalaClientUnknownHostException,
  TypeSafeService
}
import io.cequence.wsclient.JsonUtil.JsonOps
import io.cequence.wsclient.domain.{
  CequenceWSTimeoutException,
  CequenceWSUnknownHostException,
  Response,
  RichResponse,
  SiteBinding,
  WsRequestContext
}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.WSClientWithEngineTypes.WSClientWithEngine
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import io.cequence.wsclient.service.ws.Timeouts
import play.api.libs.json.{JsObject, JsValue, Json}

import java.net.UnknownHostException
import java.time.{Instant, ZoneOffset}
import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}

/**
 * @param baseUrl
 *   the API host (`https://api.typesafe.ai`); the `v1/...` paths are appended here
 * @param timeouts
 *   client-level timeouts for the PRIVATELY-created engine - mutually exclusive with
 *   `externalEngine` (the factory passes one or the other, never both); with a caller-supplied
 *   engine use `engine.copy(TransportSettings(timeouts = ...))` instead
 * @param externalEngine
 *   a caller-supplied, site-stateless engine; not closed by this service
 * @param apiKey
 *   the key - empty for a host that needs none (no `Authorization` header is sent then)
 * @param provider
 *   the host - its decisions path, model listing, question cap, images, request id headers
 *   (the key, base URL and default model come separately, so they can be overridden)
 *
 * It also serves OpenAI's Decisions API interface ([[OpenAIDecisionsService]]) - natively on a
 * host of that protocol, translated ([[OpenAIToSystemOne]]) on a System One host - failing
 * with the `OpenAIScala*` exceptions there, the native one as the cause.
 */
private[service] class TypeSafeServiceImpl(
  apiKey: String,
  baseUrl: String,
  override val defaultModel: String,
  timeouts: Option[Timeouts] = None,
  externalEngine: Option[WSClientEngine] = None,
  provider: DecisionProvider = DecisionProviderSettings.typeSafe
)(
  implicit val ec: ExecutionContext
) extends TypeSafeService
    with OpenAIDecisionsService
    with WSClientWithEngine {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override def readsImages: Boolean = provider.readsImages

  // a caller-supplied, site-stateless engine (e.g. one shared with other providers), or a
  // privately-owned classpath-discovered engine
  override protected val engine: WSClientEngine =
    externalEngine.getOrElse(
      WSClientEngineRegistry(TransportSettings(timeouts = timeouts.getOrElse(Timeouts())))
    )

  // the engine is only closed by this service when it was privately created here - a
  // caller-supplied engine is closed by its creator
  override protected def ownsEngine: Boolean = externalEngine.isEmpty

  override protected val site: SiteBinding =
    SiteBinding(
      TypeSafeServiceImpl.normalizeBaseUrl(baseUrl),
      // a local server (llama.cpp) runs without a key
      WsRequestContext(authHeaders = Seq(apiKey).filter(_.nonEmpty).map { key =>
        provider.apiKeyHeader.fold("Authorization" -> s"Bearer $key")(_ -> key)
      }),
      label = Some(provider.label)
    )

  override def systemOne(
    state: JsValue,
    questions: Map[String, Question],
    model: String
  ): Future[SystemOneResponse] = {
    // fails fast (IllegalArgumentException) before any I/O
    val request = SystemOneRequest(state, model, questions)
    checkQuestionCount(questions.size)
    checkBoxQuestions(questions)

    checkImages(DecisionImage.imageUrls(state), "state")
    // TypeSafe's assets take images in the questions too (a reference picture, options by
    // example)
    if (provider.images == DecisionImages.Assets)
      checkImages(DecisionImage.imageUrls(Json.toJson(questions)), "questions")

    // one order of the questions for the request and for reading the answers back
    val asked = questions.toSeq
    val codec = DecisionCodec(provider, model)
    val body = codec.body(request, asked)

    execPOSTBodyRich(
      EndPoint.custom(provider.decisionsPath),
      body = body,
      extraHeaders = provider.extraHeaders ++ codec.headers(body)
    ).map { rich =>
      val response = codec.response(responseOrError(rich).json, asked)
      response.copy(requestId = requestId(rich))
    }.recoverWith(transportErrors).recoverWith(boxHint(asked))
  }

  // box questions are TypeSafe's (Jev 1.14+): no other host answers one, so it is refused here,
  // naming the questions, rather than by the host in its own words (OpenRouter: "No matching
  // discriminator: noul, choice, score")
  private def checkBoxQuestions(questions: Map[String, Question]): Unit = {
    val boxes = questions.collect { case (name, _: BoxQuestion) => name }.toSeq.sorted
    require(
      boxes.isEmpty || provider.boxQuestions,
      s"${provider.label} answers no box questions (${boxes.mkString(", ")}) - they are " +
        "TypeSafe's (Jev 1.14 and later, DecisionProviderSettings.typeSafe); a provider whose " +
        "host answers them sets boxQuestions = true."
    )
  }

  // a Jev before 1.14 (`jev-latest` until it moves on) answers a box question with a bare 400
  // "Invalid request." - say why
  private def boxHint[T](
    questions: Seq[(String, Question)]
  ): PartialFunction[Throwable, Future[T]] = {
    case e: TypeSafeScalaApiUsageException
        if e.httpCode.contains(400) &&
          Option(e.getMessage).exists(_.contains("Invalid request.")) &&
          questions.exists(_._2.isInstanceOf[BoxQuestion]) =>
      Future.failed(
        new TypeSafeScalaApiUsageException(
          s"${e.getMessage} - box questions need Jev 1.14 or later (e.g. jev-1.14.0), an " +
            "older model answers them so",
          e,
          e.httpCode,
          e.errorType,
          e.requestId
        )
      )
  }

  override def createDecision(
    input: DecisionInput,
    questions: Seq[DecisionQuestion],
    settings: CreateDecisionSettings
  ): Future[Decision] =
    (provider.protocolFor(settings.model.getOrElse(defaultModel)) match {
      case DecisionProtocol.OpenAI =>
        Future.unit.flatMap { _ =>
          checkQuestionCount(questions.size)
          checkImages(imageUrls(input), "input")
          execPOSTBodyRich(
            EndPoint.custom(provider.decisionsPath),
            body = createDecisionBody(
              input,
              questions,
              settings.copy(model = settings.model.orElse(Some(defaultModel)))
            ),
            extraHeaders = provider.extraHeaders
          ).map(rich =>
            responseOrError(rich).json.asSafe[Decision].copy(requestId = requestId(rich))
          ).recoverWith(transportErrors)
        }

      case DecisionProtocol.SystemOne | DecisionProtocol.ScaleDown =>
        val imageRefusal =
          if (provider.readsImages) None
          else Some(s"${provider.label} reads no images - send them to a host that does.")
        OpenAIToSystemOne.createDecision(this, imageRefusal)(input, questions, settings)
    }).recoverWith(repackAsOpenAIException)

  // the image checks - the format, a host's tile cap, TypeSafe's size bounds - before any I/O,
  // on both entry points
  private def checkImages(
    urls: Seq[String],
    what: String
  ): Unit =
    if (provider.readsImages) {
      val problems = urls.flatMap { url =>
        DecisionImage
          .problem(url, provider.maxImageTiles)
          .orElse(
            if (provider.images == DecisionImages.Assets) DecisionImage.assetProblem(url)
            else None
          )
      }
      require(
        problems.isEmpty,
        s"The $what carries an image the API cannot take: ${problems.mkString("; ")}."
      )
    }

  private def imageUrls(input: DecisionInput): Seq[String] =
    input match {
      case DecisionInput.Messages(messages) =>
        messages.flatMap(_.content).collect { case DecisionContent.InputImage(url, _) => url }
      case _ => Nil
    }

  private def checkQuestionCount(count: Int): Unit =
    provider.maxQuestions.foreach { max =>
      require(
        count <= max,
        s"${provider.label} takes at most $max questions per request (got $count) - " +
          "split them over several requests."
      )
    }

  override def listModels: Future[Seq[ModelMetadata]] =
    provider.models match {
      case DecisionModelListing.TypeSafe =>
        execGETRich(EndPoint.models, extraHeaders = provider.extraHeaders).map { rich =>
          (responseOrError(rich).json \ "models").get.asSafe[Seq[ModelMetadata]]
        }.recoverWith(transportErrors)

      case DecisionModelListing.OpenAIStyle(query) =>
        execGETRich(
          EndPoint.models,
          params = query.map { case (name, value) => Param.query(name) -> Some(value) },
          extraHeaders = provider.extraHeaders
        ).map { rich =>
          (responseOrError(rich).json \ "data")
            .asOpt[Seq[JsObject]]
            .getOrElse(Nil)
            .flatMap(TypeSafeServiceImpl.openAIStyleModel)
        }.recoverWith(transportErrors)

      case DecisionModelListing.Fixed(models) =>
        Future.successful(models)

      case DecisionModelListing.AzureDeployments(modelPrefix) =>
        execGETRich(
          EndPoint.custom(TypeSafeServiceImpl.AzureDeploymentsPath),
          params = Seq(
            Param.query("api-version") -> Some(TypeSafeServiceImpl.AzureDeploymentsApiVersion)
          ),
          extraHeaders = provider.extraHeaders
        ).map { rich =>
          (responseOrError(rich).json \ "data")
            .asOpt[Seq[JsObject]]
            .getOrElse(Nil)
            .flatMap(TypeSafeServiceImpl.azureDeployment(_, modelPrefix))
        }.recoverWith(transportErrors)
    }

  // like ws-client's getResponseOrError, but the exception also carries the request id
  private def responseOrError(rich: RichResponse): Response =
    rich.response.getOrElse(
      throw HandleTypeSafeErrorCodes.toException(
        rich.status.code,
        rich.status.message,
        requestId(rich)
      )
    )

  // the host's request id headers by priority, not by their order in the response
  private def requestId(rich: RichResponse): Option[String] =
    TypeSafeServiceImpl.requestId(rich, provider.requestIdHeaders)

  private def transportErrors[T]: PartialFunction[Throwable, Future[T]] = {
    case e @ (_: CequenceWSTimeoutException | _: TimeoutException) =>
      Future.failed(new TypeSafeScalaClientTimeoutException(e.getMessage, e))
    case e @ (_: CequenceWSUnknownHostException | _: UnknownHostException) =>
      Future.failed(new TypeSafeScalaClientUnknownHostException(e.getMessage, e))
  }
}

private[service] object TypeSafeServiceImpl {

  /**
   * `SiteBinding` joins `coreUrl` and the endpoint verbatim, so the host must end with `/`.
   */
  def normalizeBaseUrl(baseUrl: String): String =
    baseUrl.trim.stripSuffix("/") + "/"

  /**
   * Azure's data-plane deployment list on a Foundry resource
   * (`DecisionModelListing.AzureDeployments`).
   */
  val AzureDeploymentsPath = "openai/deployments"

  /**
   * The only api-version that list answers (live 2026-10-09; `2024-10-21` and
   * `2025-04-01-preview` are 404s).
   */
  val AzureDeploymentsApiVersion = "2023-03-15-preview"

  // an entry of Azure's deployment list (`{"id", "model", "status", "created_at"}`; the model a
  // name, or `{"name"}` in other api-versions) as TypeSafe's metadata: the deployment id as the
  // name - what a request sends - and the model as the description; None for a deployment of
  // another model
  private[impl] def azureDeployment(
    json: JsObject,
    modelPrefix: String
  ): Option[ModelMetadata] =
    for {
      id <- (json \ "id").asOpt[String]
      model <- (json \ "model").asOpt[String].orElse((json \ "model" \ "name").asOpt[String])
      if model.toLowerCase.startsWith(modelPrefix.toLowerCase)
    } yield ModelMetadata(
      id,
      (json \ "status").asOpt[String].filterNot(_ == "succeeded") match {
        case Some(status) => s"$model deployment ($status)"
        case None         => s"$model deployment"
      },
      (json \ "created_at")
        .asOpt[Long]
        .map(Instant.ofEpochSecond(_).atZone(ZoneOffset.UTC).toLocalDate.toString)
        .getOrElse("")
    )

  private[impl] def requestId(
    rich: RichResponse,
    headers: Seq[String]
  ): Option[String] =
    ResponseHeaders.first(rich.headers, headers)

  // an entry of an OpenAI-style model list (`{"id", "description", "created", "architecture":
  // {"input_modalities", "output_modalities"}}`, or the modalities at the top as names or
  // `{"type"}` objects - ScaleDown's) as TypeSafe's metadata - the release date from the
  // creation time, if any; None for a model whose output modalities lack `decisions` (a chat
  // model next to the decision models of a llama.cpp router, ScaleDown's extract)
  private[impl] def openAIStyleModel(json: JsObject): Option[ModelMetadata] = {
    val decides = modalities(json, "output_modalities").forall(_.contains("decisions"))

    (json \ "id").asOpt[String].filter(_ => decides).map { id =>
      ModelMetadata(
        id,
        (json \ "description").asOpt[String].getOrElse(""),
        (json \ "created")
          .asOpt[Long]
          .map(Instant.ofEpochSecond(_).atZone(ZoneOffset.UTC).toLocalDate.toString)
          .getOrElse(""),
        modalities(json, "input_modalities")
      )
    }
  }

  private def modalities(
    json: JsObject,
    name: String
  ): Option[Seq[String]] =
    (json \ "architecture" \ name)
      .asOpt[Seq[String]]
      .orElse(
        (json \ name)
          .asOpt[Seq[JsValue]]
          .map(
            _.flatMap(modality =>
              modality.asOpt[String].orElse((modality \ "type").asOpt[String])
            )
          )
      )
}
