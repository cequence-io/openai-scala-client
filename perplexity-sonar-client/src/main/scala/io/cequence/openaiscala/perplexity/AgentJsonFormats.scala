package io.cequence.openaiscala.perplexity

import io.cequence.openaiscala.JsonFormats.{jsonSchemaWrites, reasoningEffortFormat}
import io.cequence.openaiscala.domain.settings.JsonSchemaDef
import io.cequence.openaiscala.perplexity.domain.agent._
import io.cequence.wsclient.JsonUtil
import play.api.libs.functional.syntax._
import play.api.libs.json._

/**
 * JSON of the Perplexity Agent API (`/v1/agent`, `/v1/models`) - written against the published
 * OpenAPI document (`https://docs.perplexity.ai/openapi.json`, vendored in the tests) and the
 * API guides. Reads are lenient: unknown output items and stream events are kept as raw JSON,
 * statuses stay strings, and every property the spec marks optional may be absent.
 */
object AgentJsonFormats extends AgentJsonFormats

trait AgentJsonFormats {

  // ---- request ----

  implicit lazy val agentRoleFormat: Format[AgentRole] =
    JsonUtil.enumFormat[AgentRole](AgentRole.values: _*)

  implicit lazy val agentContentPartWrites: Writes[AgentContentPart] = {
    case AgentContentPart.InputText(text) =>
      Json.obj("type" -> "input_text", "text" -> text)
    case AgentContentPart.InputImage(imageUrl) =>
      Json.obj("type" -> "input_image", "image_url" -> imageUrl)
  }

  private def textOrParts(content: Either[String, Seq[AgentContentPart]]): JsValue =
    content.fold(JsString(_), parts => Json.toJson(parts))

  implicit lazy val agentInputItemWrites: Writes[AgentInputItem] = {
    case AgentInputItem.Message(role, content) =>
      Json.obj("type" -> "message", "role" -> role, "content" -> textOrParts(content))

    case AgentInputItem.FunctionCall(callId, name, arguments, thoughtSignature) =>
      withOptional(
        Json.obj(
          "type" -> "function_call",
          "call_id" -> callId,
          "name" -> name,
          "arguments" -> arguments
        ),
        "thought_signature" -> thoughtSignature.map(JsString)
      )

    case AgentInputItem.FunctionCallOutput(callId, output, name, thoughtSignature) =>
      withOptional(
        Json.obj(
          "type" -> "function_call_output",
          "call_id" -> callId,
          "output" -> textOrParts(output)
        ),
        "name" -> name.map(JsString),
        "thought_signature" -> thoughtSignature.map(JsString)
      )
  }

  implicit lazy val agentInputWrites: Writes[AgentInput] = {
    case AgentInput.Text(text)   => JsString(text)
    case AgentInput.Items(items) => Json.toJson(items)
  }

  implicit lazy val webSearchFiltersWrites: Writes[WebSearchFilters] = (f: WebSearchFilters) =>
    withOptional(
      Json.obj(),
      "search_domain_filter" -> nonEmpty(f.searchDomainFilter),
      "search_recency_filter" -> f.searchRecencyFilter.map(JsString),
      "search_after_date_filter" -> f.searchAfterDateFilter.map(JsString),
      "search_before_date_filter" -> f.searchBeforeDateFilter.map(JsString),
      "last_updated_after_filter" -> f.lastUpdatedAfterFilter.map(JsString),
      "last_updated_before_filter" -> f.lastUpdatedBeforeFilter.map(JsString)
    )

  implicit lazy val userLocationWrites: Writes[UserLocation] = (l: UserLocation) =>
    withOptional(
      Json.obj(),
      "city" -> l.city.map(JsString),
      "region" -> l.region.map(JsString),
      "country" -> l.country.map(JsString),
      "latitude" -> l.latitude.map(JsNumber(_)),
      "longitude" -> l.longitude.map(JsNumber(_))
    )

  implicit lazy val agentToolWrites: Writes[AgentTool] = {
    case t: AgentTool.WebSearch =>
      withOptional(
        Json.obj("type" -> "web_search"),
        "filters" -> t.filters.map(Json.toJson(_)),
        "search_type" -> t.searchType.map(JsString),
        "search_context_size" -> t.searchContextSize.map(JsString),
        "max_results" -> t.maxResults.map(JsNumber(_)),
        "max_tokens" -> t.maxTokens.map(JsNumber(_)),
        "max_tokens_per_page" -> t.maxTokensPerPage.map(JsNumber(_)),
        "user_location" -> t.userLocation.map(Json.toJson(_))
      )

    case AgentTool.FetchUrl(maxUrls) =>
      withOptional(Json.obj("type" -> "fetch_url"), "max_urls" -> maxUrls.map(JsNumber(_)))

    case AgentTool.FinanceSearch => Json.obj("type" -> "finance_search")
    case AgentTool.PeopleSearch  => Json.obj("type" -> "people_search")
    case AgentTool.Sandbox       => Json.obj("type" -> "sandbox")

    case t: AgentTool.Function =>
      withOptional(
        Json.obj("type" -> "function", "name" -> t.name),
        "description" -> t.description.map(JsString),
        "parameters" -> t.parameters.map(Json.toJson(_)(jsonSchemaWrites)),
        "strict" -> t.strict.map(JsBoolean)
      )

    case t: AgentTool.Mcp =>
      withOptional(
        Json.obj(
          "type" -> "mcp",
          "server_label" -> t.serverLabel,
          "server_url" -> t.serverUrl
        ),
        "authorization" -> t.authorization.map(JsString),
        "headers" -> (if (t.headers.nonEmpty) Some(Json.toJson(t.headers)) else None),
        "allowed_tools" -> nonEmpty(t.allowedTools),
        "defer_loading" -> t.deferLoading.map(JsBoolean)
      )

    case t: AgentTool.Connector =>
      withOptional(
        Json.obj("type" -> "connector", "id" -> t.id, "server_label" -> t.serverLabel),
        "server_description" -> t.serverDescription.map(JsString),
        "allowed_tools" -> nonEmpty(t.allowedTools)
      )

    case AgentTool.Raw(json) => json
  }

  implicit lazy val agentSkillWrites: Writes[AgentSkill] = {
    case AgentSkill.Builtin(name) => Json.obj("type" -> "builtin", "name" -> name)
    case AgentSkill.Inline(name, description, instructions) =>
      Json.obj(
        "type" -> "inline",
        "name" -> name,
        "description" -> description,
        "instructions" -> instructions
      )
    case AgentSkill.Custom(id, version) =>
      withOptional(
        Json.obj("type" -> "custom", "id" -> id),
        "version" -> version.map(JsString)
      )
  }

  implicit lazy val agentProfileReferenceWrites: Writes[AgentProfileReference] =
    (p: AgentProfileReference) =>
      withOptional(
        Json.obj("type" -> "custom", "id" -> p.id),
        "version" -> p.version.map(JsString)
      )

  // {"type": "json_schema", "json_schema": {"name", "schema", "strict"}}
  private def responseFormatJson(schemaDef: JsonSchemaDef): JsObject =
    Json.obj(
      "type" -> "json_schema",
      "json_schema" -> Json.obj(
        "name" -> schemaDef.name,
        "schema" -> schemaDef.structure.fold(
          schema => Json.toJson(schema)(jsonSchemaWrites),
          map => Json.toJson(map)(JsonUtil.StringAnyMapFormat)
        ),
        "strict" -> schemaDef.strict
      )
    )

  /** The body of `POST /v1/agent`. */
  def createAgentRequestBody(
    input: AgentInput,
    settings: CreateAgentResponseSettings,
    stream: Boolean
  ): JsObject = {
    val core = withOptional(
      Json.obj("input" -> input),
      "preset" -> settings.preset.map(JsString),
      "model" -> settings.model.map(JsString),
      "models" -> nonEmpty(settings.models),
      "profile" -> settings.profile.map(Json.toJson(_)),
      "instructions" -> settings.instructions.map(JsString),
      "tools" -> (if (settings.tools.nonEmpty) Some(Json.toJson(settings.tools)) else None),
      "skills" -> (if (settings.skills.nonEmpty) Some(Json.toJson(settings.skills)) else None),
      "max_output_tokens" -> settings.maxOutputTokens.map(JsNumber(_)),
      "max_steps" -> settings.maxSteps.map(JsNumber(_)),
      "previous_response_id" -> settings.previousResponseId.map(JsString),
      "reasoning" -> settings.reasoningEffort.map(effort => Json.obj("effort" -> effort)),
      "response_format" -> settings.responseFormat.map(responseFormatJson),
      "language_preference" -> settings.languagePreference.map(JsString),
      "temperature" -> settings.temperature.map(JsNumber(_)),
      "top_p" -> settings.topP.map(JsNumber(_)),
      "prompt_cache_key" -> settings.promptCacheKey.map(JsString),
      "service_tier" -> settings.serviceTier.map(JsString),
      "store" -> settings.store.map(JsBoolean),
      "background" -> settings.background.map(JsBoolean),
      "stream" -> Some(JsBoolean(stream))
    )

    settings.extraParams.foldLeft(core) { case (acc, (key, value)) =>
      acc + (key -> JsonUtil.toJson(value))
    }
  }

  private def withOptional(
    base: JsObject,
    fields: (String, Option[JsValue])*
  ): JsObject =
    fields.foldLeft(base) {
      case (acc, (key, Some(value))) => acc + (key -> value)
      case (acc, _)                  => acc
    }

  private def nonEmpty(values: Seq[String]): Option[JsValue] =
    if (values.nonEmpty) Some(Json.toJson(values)) else None

  // ---- response ----

  private def seqOrEmpty[T: Reads](path: JsPath): Reads[Seq[T]] =
    path.readNullable[Seq[T]].map(_.getOrElse(Nil))

  // `error: null` on success (MCP calls)
  private def nullableString(path: JsPath): Reads[Option[String]] =
    path.readNullable[JsValue].map(_.flatMap(_.asOpt[String]))

  implicit lazy val agentAnnotationReads: Reads[AgentAnnotation] = (
    (__ \ "type").readNullable[String] and
      (__ \ "url").readNullable[String] and
      (__ \ "title").readNullable[String] and
      (__ \ "start_index").readNullable[Int] and
      (__ \ "end_index").readNullable[Int]
  )(AgentAnnotation.apply _)

  implicit lazy val agentOutputTextReads: Reads[AgentOutputText] = (
    (__ \ "text").read[String] and
      seqOrEmpty[AgentAnnotation](__ \ "annotations") and
      (__ \ "type").readNullable[String].map(_.getOrElse("output_text"))
  )(AgentOutputText.apply _)

  implicit lazy val agentSearchResultReads: Reads[AgentSearchResult] = (
    (__ \ "id").read[Int] and
      (__ \ "url").read[String] and
      (__ \ "title").read[String] and
      (__ \ "snippet").read[String] and
      (__ \ "date").readNullable[String] and
      (__ \ "last_updated").readNullable[String] and
      (__ \ "source").readNullable[String]
  )(AgentSearchResult.apply _)

  implicit lazy val agentUrlContentReads: Reads[AgentUrlContent] = (
    (__ \ "url").read[String] and
      (__ \ "title").read[String] and
      (__ \ "snippet").read[String]
  )(AgentUrlContent.apply _)

  implicit lazy val agentFinanceResultReads: Reads[AgentFinanceResult] = (
    (__ \ "category").read[String] and
      (__ \ "content").read[String] and
      seqOrEmpty[String](__ \ "sources") and
      seqOrEmpty[String](__ \ "tickers")
  )(AgentFinanceResult.apply _)

  implicit lazy val agentMcpToolDefReads: Reads[AgentMcpToolDef] = (
    (__ \ "name").read[String] and
      (__ \ "input_schema").read[JsValue] and
      (__ \ "description").readNullable[String]
  )(AgentMcpToolDef.apply _)

  implicit lazy val agentOutputItemReads: Reads[AgentOutputItem] = (json: JsValue) =>
    (json \ "type").validate[String].flatMap {
      case "message" =>
        (
          (__ \ "id").read[String] and
            (__ \ "status").read[String] and
            (__ \ "role").read[String] and
            seqOrEmpty[AgentOutputText](__ \ "content")
        )(AgentOutputItem.Message.apply _).reads(json)

      case "search_results" =>
        (
          seqOrEmpty[AgentSearchResult](__ \ "results") and
            seqOrEmpty[String](__ \ "queries")
        )(AgentOutputItem.SearchResults.apply _).reads(json)

      case "fetch_url_results" =>
        seqOrEmpty[AgentUrlContent](__ \ "contents")
          .map(AgentOutputItem.FetchUrlResults.apply)
          .reads(json)

      case "finance_results" =>
        (
          seqOrEmpty[AgentFinanceResult](__ \ "results") and
            seqOrEmpty[String](__ \ "categories") and
            seqOrEmpty[String](__ \ "tickers")
        )(AgentOutputItem.FinanceResults.apply _).reads(json)

      case "people_search_results" =>
        (
          seqOrEmpty[AgentSearchResult](__ \ "results") and
            seqOrEmpty[String](__ \ "queries")
        )(AgentOutputItem.PeopleSearchResults.apply _).reads(json)

      case "function_call" =>
        (
          (__ \ "id").read[String] and
            (__ \ "status").read[String] and
            (__ \ "name").read[String] and
            (__ \ "call_id").read[String] and
            (__ \ "arguments").read[String] and
            (__ \ "thought_signature").readNullable[String]
        )(AgentOutputItem.FunctionCall.apply _).reads(json)

      case "sandbox_results" =>
        (
          (__ \ "status").read[String] and
            (__ \ "code").readNullable[String] and
            (__ \ "stdout").readNullable[String] and
            (__ \ "stderr").readNullable[String] and
            (__ \ "exit_code").readNullable[Int] and
            (__ \ "duration_ms").readNullable[Long]
        )(AgentOutputItem.SandboxResults.apply _).reads(json)

      case "mcp_list_tools" =>
        (
          (__ \ "id").read[String] and
            (__ \ "server_label").read[String] and
            seqOrEmpty[AgentMcpToolDef](__ \ "tools") and
            (__ \ "connector_id").readNullable[String] and
            nullableString(__ \ "error")
        )(AgentOutputItem.McpListTools.apply _).reads(json)

      case "mcp_call" =>
        (
          (__ \ "id").read[String] and
            (__ \ "server_label").read[String] and
            (__ \ "name").read[String] and
            (__ \ "arguments").read[String] and
            nullableString(__ \ "output") and
            nullableString(__ \ "error") and
            (__ \ "connector_id").readNullable[String]
        )(AgentOutputItem.McpCall.apply _).reads(json)

      case other =>
        json.validate[JsObject].map(AgentOutputItem.Unknown(other, _))
    }

  implicit lazy val agentCostReads: Reads[AgentCost] = (
    (__ \ "currency").read[String] and
      (__ \ "input_cost").read[Double] and
      (__ \ "output_cost").read[Double] and
      (__ \ "total_cost").read[Double] and
      (__ \ "cache_creation_cost").readNullable[Double] and
      (__ \ "cache_read_cost").readNullable[Double] and
      (__ \ "tool_calls_cost").readNullable[Double]
  )(AgentCost.apply _)

  implicit lazy val agentUsageReads: Reads[AgentUsage] = (
    (__ \ "input_tokens").read[Int] and
      (__ \ "output_tokens").read[Int] and
      (__ \ "total_tokens").read[Int] and
      (__ \ "cost").readNullable[AgentCost] and
      (__ \ "input_tokens_details").readNullable[JsObject] and
      (__ \ "tool_calls_details").readNullable[JsObject]
  )(AgentUsage.apply _)

  implicit lazy val agentErrorReads: Reads[AgentError] = (
    (__ \ "message").read[String] and
      (__ \ "code")
        .readNullable[JsValue]
        .map(_.map {
          case JsString(s) => s
          case other       => other.toString
        }) and
      (__ \ "type").readNullable[String]
  )(AgentError.apply _)

  implicit lazy val agentResponseReads: Reads[AgentResponse] = (
    (__ \ "id").read[String] and
      (__ \ "created_at").read[Long] and
      (__ \ "model").readNullable[String].map(_.getOrElse("")) and
      (__ \ "status").read[String] and
      seqOrEmpty[AgentOutputItem](__ \ "output") and
      (__ \ "usage").readNullable[AgentUsage] and
      (__ \ "error").readNullable[JsValue].map(_.flatMap(_.asOpt[AgentError])) and
      (__ \ "background").readNullable[Boolean] and
      nullableString(__ \ "previous_response_id") and
      (__ \ "store").readNullable[Boolean]
  )(AgentResponse.apply _)

  implicit lazy val agentStreamEventReads: Reads[AgentStreamEvent] = (json: JsValue) => {
    import AgentStreamEvent._

    val seq = (json \ "sequence_number").validate[Int]
    def thought = (json \ "thought").asOpt[String]
    def response = (json \ "response").validateOpt[AgentResponse]

    (json \ "type").validate[String].flatMap {
      case "response.created"     => seq.flatMap(s => response.map(ResponseCreated(s, _)))
      case "response.in_progress" => seq.flatMap(s => response.map(ResponseInProgress(s, _)))
      case "response.completed"   => seq.flatMap(s => response.map(ResponseCompleted(s, _)))
      case "response.failed" =>
        seq.flatMap(s => (json \ "error").validate[AgentError].map(ResponseFailed(s, _)))

      case "response.output_item.added" =>
        for {
          s <- seq
          index <- (json \ "output_index").validate[Int]
          item <- (json \ "item").validate[AgentOutputItem]
        } yield OutputItemAdded(s, index, item)

      case "response.output_item.done" =>
        for {
          s <- seq
          index <- (json \ "output_index").validate[Int]
          item <- (json \ "item").validate[AgentOutputItem]
        } yield OutputItemDone(s, index, item)

      case "response.output_text.delta" =>
        for {
          s <- seq
          itemId <- (json \ "item_id").validate[String]
          outputIndex <- (json \ "output_index").validate[Int]
          contentIndex <- (json \ "content_index").validate[Int]
          delta <- (json \ "delta").validate[String]
        } yield OutputTextDelta(s, itemId, outputIndex, contentIndex, delta)

      case "response.output_text.done" =>
        for {
          s <- seq
          itemId <- (json \ "item_id").validate[String]
          outputIndex <- (json \ "output_index").validate[Int]
          contentIndex <- (json \ "content_index").validate[Int]
          text <- (json \ "text").validate[String]
        } yield OutputTextDone(s, itemId, outputIndex, contentIndex, text)

      case "response.reasoning.started" => seq.map(ReasoningStarted(_, thought))
      case "response.reasoning.stopped" => seq.map(ReasoningStopped(_, thought))

      case "response.reasoning.search_queries" =>
        seq
          .map(SearchQueries(_, (json \ "queries").asOpt[Seq[String]].getOrElse(Nil), thought))

      case "response.reasoning.search_results" =>
        for {
          s <- seq
          results <- seqOrEmpty[AgentSearchResult](__ \ "results").reads(json)
          usage <- (json \ "usage").validateOpt[AgentUsage]
        } yield SearchResults(s, results, thought, usage)

      case "response.reasoning.fetch_url_queries" =>
        seq.map(FetchUrlQueries(_, (json \ "urls").asOpt[Seq[String]].getOrElse(Nil), thought))

      case "response.reasoning.fetch_url_results" =>
        for {
          s <- seq
          contents <- seqOrEmpty[AgentUrlContent](__ \ "contents").reads(json)
        } yield FetchUrlResults(s, contents, thought)

      case other =>
        json.validate[JsObject].map { obj =>
          Unknown(other, (json \ "sequence_number").asOpt[Int].getOrElse(-1), obj)
        }
    }
  }

  implicit lazy val agentCancelResponseReads: Reads[AgentCancelResponse] = (
    (__ \ "response_id").read[String] and
      (__ \ "status").read[String]
  )(AgentCancelResponse.apply _)

  implicit lazy val agentResponseFileReads: Reads[AgentResponseFile] = (
    (__ \ "id").read[String] and
      (__ \ "filename").read[String] and
      (__ \ "bytes").read[Long] and
      (__ \ "created_at").read[Long]
  )(AgentResponseFile.apply _)

  implicit lazy val agentModelReads: Reads[AgentModel] = (
    (__ \ "id").read[String] and
      (__ \ "created").readNullable[Long].map(_.getOrElse(0L)) and
      (__ \ "owned_by").readNullable[String].map(_.getOrElse(""))
  )(AgentModel.apply _)
}
