package io.cequence.openaiscala.perplexity

import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.domain.settings.{JsonSchemaDef, ReasoningEffort}
import io.cequence.openaiscala.perplexity.AgentJsonFormats._
import io.cequence.openaiscala.perplexity.domain.agent._
import io.cequence.openaiscala.perplexity.service.impl.EndPoint
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

import java.io.File
import scala.io.Source

/**
 * Checks the Agent API client against Perplexity's published OpenAPI document
 * (`https://docs.perplexity.ai/openapi.json`, vendored as `perplexity-openapi.json`, fetched
 * 2026-09-25) and the response examples of the Agent API guides (`agent-docs-examples/`):
 *
 *   - what the client writes validates against the request schema - every property known,
 *     every required one present, enums and discriminators right - for every input item,
 *     content part, tool and skill
 *   - a minimal and a maximal instance of EVERY output item and stream event schema, generated
 *     from the spec, decodes into its typed case (never the Unknown fallback)
 *   - every documented response example decodes
 */
class AgentOpenApiConformanceSpec extends AnyWordSpec with Matchers {

  private def resource(name: String): String = {
    val source = Source.fromResource(name)
    try source.mkString
    finally source.close()
  }

  private val spec = Json.parse(resource("perplexity-openapi.json")).as[JsObject]
  private val schemas = (spec \ "components" \ "schemas").as[JsObject]

  private def schema(name: String): JsObject = (schemas \ name).as[JsObject]

  private def refName(s: JsObject): Option[String] =
    (s \ "$ref").asOpt[String].map(_.split('/').last)

  // ---- a minimal JSON-schema validator (the constructs the spec uses) ----

  private def validate(
    value: JsValue,
    s: JsObject,
    path: String
  ): Seq[String] =
    refName(s) match {
      case Some(name) => validate(value, schema(name), path)
      case None =>
        val alternatives =
          (s \ "oneOf").asOpt[Seq[JsObject]].orElse((s \ "anyOf").asOpt[Seq[JsObject]])

        alternatives match {
          case Some(options) =>
            val results = options.map(validate(value, _, path))
            if (results.exists(_.isEmpty)) Nil
            else
              Seq(
                s"$path: matches none of ${options.size} alternatives: " + results
                  .minBy(_.size)
                  .mkString("; ")
              )

          case None if (s \ "allOf").isDefined =>
            validate(value, mergeAllOf(s), path)

          case None =>
            validateTyped(value, s, path)
        }
    }

  private def mergeAllOf(s: JsObject): JsObject = {
    val parts = (s \ "allOf").as[Seq[JsObject]].map(p => refName(p).map(schema).getOrElse(p))
    Json.obj(
      "type" -> "object",
      "properties" -> parts
        .map(p => (p \ "properties").asOpt[JsObject].getOrElse(Json.obj()))
        .reduce(_ ++ _),
      "required" -> parts.flatMap(p => (p \ "required").asOpt[Seq[String]].getOrElse(Nil)),
      "additionalProperties" -> false
    )
  }

  private def types(s: JsObject): Seq[String] =
    (s \ "type").toOption match {
      case Some(JsString(t)) => Seq(t)
      case Some(JsArray(ts)) => ts.map(_.as[String]).toSeq
      case _                 => Nil
    }

  private def validateTyped(
    value: JsValue,
    s: JsObject,
    path: String
  ): Seq[String] = {
    val enumErrors = (s \ "enum").asOpt[Seq[JsValue]] match {
      case Some(values) if !values.contains(value) => Seq(s"$path: $value not in enum $values")
      case _                                       => Nil
    }
    val constErrors = (s \ "const").toOption match {
      case Some(c) if c != value => Seq(s"$path: $value is not the const $c")
      case _                     => Nil
    }

    val typeErrors = (types(s), value) match {
      case (Nil, _)                                     => Nil
      case (ts, JsNull) if ts.contains("null")          => Nil
      case (ts, _: JsString) if ts.contains("string")   => Nil
      case (ts, _: JsBoolean) if ts.contains("boolean") => Nil
      case (ts, n: JsNumber) if ts.contains("integer") =>
        if (n.value.isWhole) Nil else Seq(s"$path: $n is not an integer")
      case (ts, _: JsNumber) if ts.contains("number") => Nil
      case (ts, JsArray(items)) if ts.contains("array") =>
        val itemSchema = (s \ "items").asOpt[JsObject].getOrElse(Json.obj())
        items.zipWithIndex.flatMap { case (item, i) =>
          validate(item, itemSchema, s"$path[$i]")
        }.toSeq
      case (ts, obj: JsObject) if ts.contains("object") => validateObject(obj, s, path)
      case (ts, other) => Seq(s"$path: $other is not of type $ts")
    }

    // an untyped object schema with properties
    val untypedObjectErrors = (types(s), value) match {
      case (Nil, obj: JsObject) if (s \ "properties").isDefined => validateObject(obj, s, path)
      case _                                                    => Nil
    }

    enumErrors ++ constErrors ++ typeErrors ++ untypedObjectErrors
  }

  private def validateObject(
    obj: JsObject,
    s: JsObject,
    path: String
  ): Seq[String] =
    (s \ "properties").asOpt[JsObject] match {
      case None => Nil // a free-form object (parameters, schema, headers)
      case Some(properties) =>
        val required = (s \ "required").asOpt[Seq[String]].getOrElse(Nil)
        val missing =
          required.filterNot(obj.keys.contains).map(k => s"$path: missing required '$k'")
        val unknown =
          if ((s \ "additionalProperties").asOpt[JsValue].exists(_ != JsBoolean(false)))
            Nil
          else
            obj.keys.toSeq
              .filterNot(properties.keys.contains)
              .map(k => s"$path: unknown property '$k'")
        val nested = obj.fields.toSeq.flatMap { case (k, v) =>
          (properties \ k).asOpt[JsObject].toSeq.flatMap(validate(v, _, s"$path.$k"))
        }
        missing ++ unknown ++ nested
    }

  // ---- an instance generator (minimal: required properties only; maximal: all) ----

  private def generate(
    s: JsObject,
    maximal: Boolean,
    depth: Int = 0
  ): JsValue =
    refName(s) match {
      case Some(name)         => generate(schema(name), maximal, depth + 1)
      case None if depth > 12 => JsNull
      case None =>
        (s \ "const").toOption
          .orElse((s \ "enum").asOpt[Seq[JsValue]].flatMap(_.headOption))
          .getOrElse {
            (s \ "oneOf")
              .asOpt[Seq[JsObject]]
              .orElse((s \ "anyOf").asOpt[Seq[JsObject]]) match {
              case Some(options) => generate(options.head, maximal, depth + 1)
              case None if (s \ "allOf").isDefined =>
                generate(mergeAllOf(s), maximal, depth + 1)
              case None =>
                types(s).filterNot(_ == "null").headOption.getOrElse("object") match {
                  case "string"  => JsString("x")
                  case "integer" => JsNumber(1)
                  case "number"  => JsNumber(1.5)
                  case "boolean" => JsBoolean(true)
                  case "array" =>
                    JsArray(
                      Seq(
                        generate(
                          (s \ "items").asOpt[JsObject].getOrElse(Json.obj()),
                          maximal,
                          depth + 1
                        )
                      )
                    )
                  case _ =>
                    val properties = (s \ "properties").asOpt[JsObject].getOrElse(Json.obj())
                    val required = (s \ "required").asOpt[Seq[String]].getOrElse(Nil).toSet
                    JsObject(properties.fields.collect {
                      case (k, v: JsObject) if maximal || required.contains(k) =>
                        k -> generate(v, maximal, depth + 1)
                    })
                }
            }
          }
    }

  private def discriminatorMapping(name: String): Seq[(String, String)] =
    (schema(name) \ "discriminator" \ "mapping").asOpt[Map[String, String]] match {
      case Some(mapping) => mapping.toSeq.map { case (k, v) => k -> v.split('/').last }
      case None => // the mapping is implicit: each alternative's `type` enum / const
        (schema(name) \ "oneOf").as[Seq[JsObject]].flatMap(refName).map { alternative =>
          val typeSchema = (schema(alternative) \ "properties" \ "type").as[JsObject]
          val value = (typeSchema \ "const")
            .asOpt[String]
            .orElse((typeSchema \ "enum").asOpt[Seq[String]].flatMap(_.headOption))
            .getOrElse(fail(s"$alternative has no type const/enum"))
          value -> alternative
        }
    }

  private def requestErrors(json: JsValue): Seq[String] =
    validate(json, schema("ResponsesRequest"), "$")

  "The vendored OpenAPI document" should {

    "declare every endpoint the client calls, with its method" in {
      val paths = (spec \ "paths").as[JsObject]
      val agent = "/" + EndPoint.agent.toString
      val models = "/" + EndPoint.models.toString

      (paths \ agent \ "post").toOption shouldBe defined
      (paths \ s"$agent/{id}" \ "get").toOption shouldBe defined
      (paths \ s"$agent/{id}/cancel" \ "post").toOption shouldBe defined
      (paths \ s"$agent/{id}/files" \ "get").toOption shouldBe defined
      (paths \ s"$agent/{id}/files/{file_id}/content" \ "get").toOption shouldBe defined
      (paths \ models \ "get").toOption shouldBe defined
    }

    "stream text/event-stream from the create endpoint" in {
      val agent = "/" + EndPoint.agent.toString
      (spec \ "paths" \ agent \ "post" \ "responses" \ "200" \ "content" \ "text/event-stream").toOption shouldBe defined
    }
  }

  "The request body" should {

    val weather = AgentTool.Function(
      name = "get_weather",
      description = Some("Weather in a city"),
      parameters = Some(
        JsonSchema
          .Object(properties = Seq("city" -> JsonSchema.String()), required = Seq("city"))
      ),
      strict = Some(true)
    )

    val allTools: Seq[AgentTool] = Seq(
      AgentTool.WebSearch(
        filters = Some(
          WebSearchFilters(
            searchDomainFilter = Seq("nature.com"),
            searchRecencyFilter = Some("week"),
            searchAfterDateFilter = Some("01/01/2026"),
            searchBeforeDateFilter = Some("09/01/2026"),
            lastUpdatedAfterFilter = Some("01/01/2026"),
            lastUpdatedBeforeFilter = Some("09/01/2026")
          )
        ),
        searchType = Some("fast"),
        searchContextSize = Some("high"),
        maxResults = Some(10),
        maxTokens = Some(10000),
        maxTokensPerPage = Some(2000),
        userLocation =
          Some(UserLocation(Some("Oslo"), Some("Oslo"), Some("NO"), Some(59.9), Some(10.7)))
      ),
      AgentTool.FetchUrl(Some(3)),
      AgentTool.FinanceSearch,
      AgentTool.PeopleSearch,
      AgentTool.Sandbox,
      weather,
      AgentTool.Mcp(
        serverLabel = "deepwiki",
        serverUrl = "https://mcp.deepwiki.com/mcp",
        authorization = Some("token"),
        headers = Map("x-api-key" -> "k"),
        allowedTools = Seq("ask_question"),
        deferLoading = Some(true)
      ),
      AgentTool.Connector("conn_1", "drive", Some("My drive"), Seq("search"))
    )

    val maximal = CreateAgentResponseSettings(
      preset = Some(AgentPreset.low),
      model = Some("openai/gpt-5.4-mini"),
      models = Seq("openai/gpt-5.4-mini", "anthropic/claude-sonnet-4-6"),
      profile = Some(AgentProfileReference("prof_1", Some("3"))),
      instructions = Some("Be brief."),
      tools = allTools,
      skills = Seq(
        AgentSkill.Builtin("office/xlsx"),
        AgentSkill.Inline("unit-converter", "Converts units", "Use SI units."),
        AgentSkill.Custom("skill_abc", Some("latest"))
      ),
      maxOutputTokens = Some(2000),
      maxSteps = Some(5),
      previousResponseId = Some("resp_prev"),
      reasoningEffort = Some(ReasoningEffort.high),
      responseFormat = Some(
        JsonSchemaDef(
          "capital",
          strict = true,
          JsonSchema.Object(
            properties = Seq("capital" -> JsonSchema.String()),
            required = Seq("capital")
          )
        )
      ),
      languagePreference = Some("en"),
      temperature = Some(0.2),
      topP = Some(0.9),
      store = Some(true),
      background = Some(true)
    )

    val conversation = AgentInput(
      AgentInputItem.Message.system("You are terse."),
      AgentInputItem.Message(
        AgentRole.user,
        Right(
          Seq(
            AgentContentPart.InputText("What is in this image?"),
            AgentContentPart.InputImage("https://example.com/cat.png")
          )
        )
      ),
      AgentInputItem.Message.assistant("A cat."),
      AgentInputItem.Message.developer("Answer in English."),
      AgentInputItem
        .FunctionCall("call_1", "get_weather", """{"city":"Oslo"}""", Some("c2ln")),
      AgentInputItem.FunctionCallOutput("call_1", """{"temp":12}"""),
      AgentInputItem.FunctionCallOutput(
        "call_2",
        Right(
          Seq(
            AgentContentPart.InputText("chart"),
            AgentContentPart.InputImage("data:image/png;base64,AAAA")
          )
        ),
        name = Some("render_chart"),
        thoughtSignature = Some("c2ln")
      )
    )

    "validate against ResponsesRequest with every tool, skill, input item and content part" in {
      val body = createAgentRequestBody(conversation, maximal, stream = true)

      requestErrors(body) shouldBe empty
    }

    "validate for a plain string input and the defaults" in {
      val body = createAgentRequestBody(
        AgentInput("hi"),
        CreateAgentResponseSettings(preset = Some("fast")),
        stream = false
      )

      requestErrors(body) shouldBe empty
      body shouldBe Json.obj("input" -> "hi", "preset" -> "fast", "stream" -> false)
    }

    "write every tool type the spec's Tool union declares, and nothing else" in {
      val specToolTypes = discriminatorMapping("Tool").map(_._1).toSet
      val writtenTypes = allTools.map(t => (Json.toJson(t) \ "type").as[String]).toSet

      writtenTypes shouldBe specToolTypes
    }

    "write every skill type the spec's Skill union declares" in {
      val body = createAgentRequestBody(AgentInput("x"), maximal, stream = false)
      (body \ "skills").as[Seq[JsObject]].map(s => (s \ "type").as[String]).toSet shouldBe
        discriminatorMapping("Skill").map(_._1).toSet
    }

    "carry the two fields the Agent API guides use beyond the spec (prompt_cache_key, service_tier)" in {
      val body = createAgentRequestBody(
        AgentInput("x"),
        CreateAgentResponseSettings(
          promptCacheKey = Some("fast"),
          serviceTier = Some("priority")
        ),
        stream = false
      )
      val unknown = requestErrors(body).filter(_.contains("unknown property"))

      unknown should contain theSameElementsAs Seq(
        "$: unknown property 'prompt_cache_key'",
        "$: unknown property 'service_tier'"
      )
      (body \ "prompt_cache_key").as[String] shouldBe "fast"
      (body \ "service_tier").as[String] shouldBe "priority"
    }

    "send extra params as given and a map-based response schema unchanged" in {
      val body = createAgentRequestBody(
        AgentInput("x"),
        CreateAgentResponseSettings(
          responseFormat =
            Some(JsonSchemaDef("m", strict = false, Map[String, Any]("type" -> "object"))),
          extraParams = Map("custom_flag" -> true)
        ),
        stream = false
      )

      (body \ "custom_flag").as[Boolean] shouldBe true
      (body \ "response_format" \ "json_schema" \ "schema").as[JsObject] shouldBe Json.obj(
        "type" -> "object"
      )
      (body \ "response_format" \ "json_schema" \ "strict").as[Boolean] shouldBe false
    }
  }

  "The output items" should {

    val mapping = discriminatorMapping("OutputItem")

    "cover every type the spec declares" in {
      mapping.map(_._1).toSet shouldBe Set(
        "message",
        "search_results",
        "fetch_url_results",
        "finance_results",
        "people_search_results",
        "function_call",
        "sandbox_results",
        "mcp_list_tools",
        "mcp_call",
        "tool_search_output"
      )
    }

    // tool_search_output is deliberately kept raw (response-only namespace definitions)
    val typed = mapping.filterNot(_._1 == "tool_search_output")

    for ((itemType, schemaName) <- typed; maximal <- Seq(false, true)) {
      s"decode a ${if (maximal) "maximal" else "minimal"} '$itemType' ($schemaName) into its typed case" in {
        val json = generate(schema(schemaName), maximal)
        val item = json.validate[AgentOutputItem]

        item.isSuccess shouldBe true
        item.get should not be an[AgentOutputItem.Unknown]
      }
    }

    "keep tool_search_output and future item types as Unknown with the raw JSON" in {
      val toolSearch =
        generate(schema(mapping.toMap.apply("tool_search_output")), maximal = true)
      toolSearch.as[AgentOutputItem] shouldBe AgentOutputItem.Unknown(
        "tool_search_output",
        toolSearch.as[JsObject]
      )

      val future = Json.obj("type" -> "brand_new_item", "id" -> "x")
      future.as[AgentOutputItem] shouldBe AgentOutputItem.Unknown("brand_new_item", future)
    }

    "read an MCP call's null error as None" in {
      val json = Json.obj(
        "type" -> "mcp_call",
        "id" -> "mcp_1",
        "server_label" -> "deepwiki",
        "name" -> "ask_question",
        "arguments" -> "{}",
        "output" -> "answer",
        "error" -> JsNull
      )
      json.as[AgentOutputItem] shouldBe AgentOutputItem.McpCall(
        "mcp_1",
        "deepwiki",
        "ask_question",
        "{}",
        Some("answer"),
        None
      )
    }
  }

  "The stream events" should {

    val mapping = discriminatorMapping("ResponseStreamEvent")
    val eventTypes = (schema("EventType") \ "enum").as[Seq[String]]

    "be declared for every value of EventType" in {
      eventTypes should not be empty
    }

    for (eventType <- eventTypes; maximal <- Seq(false, true)) {
      s"decode a ${if (maximal) "maximal" else "minimal"} '$eventType' into its typed case" in {
        // the event schemas share the EventType enum, so pick the schema by its description
        val schemaName = mapping.toMap.getOrElse(
          eventType,
          (schema("ResponseStreamEvent") \ "oneOf")
            .as[Seq[JsObject]]
            .flatMap(refName)
            .find { name =>
              (schema(name) \ "description").asOpt[String].exists(_.contains(eventType))
            }
            .getOrElse(fail(s"no schema for $eventType"))
        )
        val json =
          generate(schema(schemaName), maximal).as[JsObject] + ("type" -> JsString(eventType))
        val event = json.validate[AgentStreamEvent]

        withClue(s"$schemaName: $json") {
          event.isSuccess shouldBe true
          event.get should not be an[AgentStreamEvent.Unknown]
          event.get.sequenceNumber shouldBe 1
        }
      }
    }

    "keep future event types as Unknown with the raw JSON" in {
      val json = Json.obj("type" -> "response.brand_new", "sequence_number" -> 7)
      json
        .as[AgentStreamEvent] shouldBe AgentStreamEvent.Unknown("response.brand_new", 7, json)
    }
  }

  "The other responses" should {

    "decode minimal and maximal ResponsesResponse, cancel, file list and model list instances" in {
      for (maximal <- Seq(false, true)) {
        generate(schema("ResponsesResponse"), maximal)
          .validate[AgentResponse]
          .isSuccess shouldBe true
        generate(schema("ResponseFileList"), maximal)
          .\("data")
          .validate[Seq[AgentResponseFile]]
          .isSuccess shouldBe true
        generate(schema("ListModelsResponse"), maximal)
          .\("data")
          .validate[Seq[AgentModel]]
          .isSuccess shouldBe true
      }
      val cancel =
        (spec \ "paths" \ "/v1/agent/{id}/cancel" \ "post" \ "responses" \ "200" \ "content" \ "application/json" \ "schema")
          .as[JsObject]
      generate(cancel, maximal = true)
        .as[AgentCancelResponse] shouldBe AgentCancelResponse("x", "cancelling")
    }
  }

  "The documented examples" should {

    val dir = new File(getClass.getClassLoader.getResource("agent-docs-examples").toURI)
    val examples = dir.listFiles().toSeq.sortBy(_.getName)

    "be present" in {
      examples.count(_.getName.startsWith("response-")) should be >= 10
    }

    for (file <- examples if file.getName.startsWith("response-")) {
      s"decode ${file.getName}" in {
        val json = Json.parse(resource(s"agent-docs-examples/${file.getName}"))
        val response = json.as[AgentResponse]

        response.id should startWith("resp_")
        response.output.collect { case u: AgentOutputItem.Unknown => u } shouldBe empty
        response.output.size shouldBe (json \ "output").as[Seq[JsValue]].size
        if (response.status == AgentResponseStatus.completed)
          response.outputText should not be empty
      }
    }

    "decode the documented file list" in {
      val file = examples
        .find(_.getName.startsWith("file-list-"))
        .getOrElse(fail("no file list example"))
      val files = (Json.parse(resource(s"agent-docs-examples/${file.getName}")) \ "data")
        .as[Seq[AgentResponseFile]]

      files should not be empty
      files.head.filename should not be empty
    }
  }
}
