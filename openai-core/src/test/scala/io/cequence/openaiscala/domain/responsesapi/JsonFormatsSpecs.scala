package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.domain.responsesapi.tools.JsonFormats._
import io.cequence.openaiscala.domain.responsesapi.JsonFormats._
import io.cequence.openaiscala.domain.settings.ReasoningEffort
import io.cequence.openaiscala.JsonFormats.reasoningEffortFormat
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import io.cequence.openaiscala.domain.responsesapi._
import io.cequence.openaiscala.domain.responsesapi.tools._
import io.cequence.openaiscala.domain.responsesapi.tools.mcp._
import io.cequence.openaiscala.domain.ChatRole
import play.api.libs.json._
import java.{util => ju}
import scala.util.Properties

object JsonFormatsSpecs {
  sealed trait JsonPrintMode
  object JsonPrintMode {
    case object Compact extends JsonPrintMode
    case object Pretty extends JsonPrintMode
  }
}

class JsonFormatsSpecs extends AnyWordSpecLike with Matchers {
  import JsonFormatsSpecs.JsonPrintMode
  import JsonFormatsSpecs.JsonPrintMode._

  // Scala 3 doesn't preserve the order of fields in json but it's hard to detect at compile time
  private lazy val isScala3 = true

  "JSON Formats for tools package" should {

    "serialize and deserialize ToolChoice.Mode values" in {
      testCodec[ToolChoice.Mode](
        ToolChoice.Mode.None,
        "\"none\"",
        Pretty
      )

      testCodec[ToolChoice.Mode](
        ToolChoice.Mode.Auto,
        "\"auto\"",
        Pretty
      )

      testCodec[ToolChoice.Mode](
        ToolChoice.Mode.Required,
        "\"required\"",
        Pretty
      )
    }

    "serialize and deserialize ToolChoice.AllowedTools with auto mode" in {
      val schema = JsonSchema.Object(
        properties = Seq(
          "location" -> JsonSchema.String(Some("The city and state"))
        ),
        required = Seq("location")
      )

      testCodec[ToolChoice](
        ToolChoice.AllowedTools.auto(
          Seq(
            FunctionTool(
              name = "get_weather",
              parameters = schema,
              strict = true,
              description = Some("Get weather")
            )
          )
        ),
        """{
          |  "mode" : "auto",
          |  "tools" : [ {
          |    "name" : "get_weather",
          |    "parameters" : {
          |      "properties" : {
          |        "location" : {
          |          "description" : "The city and state",
          |          "type" : "string"
          |        }
          |      },
          |      "required" : [ "location" ],
          |      "type" : "object",
          |      "additionalProperties" : false
          |    },
          |    "strict" : true,
          |    "description" : "Get weather",
          |    "type" : "function"
          |  } ],
          |  "type" : "allowed_tools"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ToolChoice.AllowedTools with required mode" in {
      testCodec[ToolChoice](
        ToolChoice.AllowedTools.required(
          Seq(
            FileSearchTool(),
            WebSearchTool()
          )
        ),
        """{
          |  "mode" : "required",
          |  "tools" : [ {
          |    "type" : "file_search"
          |  }, {
          |    "type" : "web_search"
          |  } ],
          |  "type" : "allowed_tools"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize FunctionTool" in {
      val schema = JsonSchema.Object(
        properties = Seq(
          "location" -> JsonSchema.String(Some("The city and state, e.g. San Francisco, CA"))
        ),
        required = Seq("location")
      )

      testCodec[Tool](
        FunctionTool(
          name = "get_weather",
          parameters = schema,
          strict = true,
          description = Some("Get the current weather for a location")
        ),
        """{
          |  "name" : "get_weather",
          |  "parameters" : {
          |    "properties" : {
          |      "location" : {
          |        "description" : "The city and state, e.g. San Francisco, CA",
          |        "type" : "string"
          |      }
          |    },
          |    "required" : [ "location" ],
          |    "type" : "object",
          |    "additionalProperties" : false
          |  },
          |  "strict" : true,
          |  "description" : "Get the current weather for a location",
          |  "type" : "function"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize FunctionTool with strict=false" in {
      val schema = JsonSchema.Object(
        properties = Seq(
          "query" -> JsonSchema.String(Some("The search query"))
        ),
        required = Seq("query")
      )

      testCodec[Tool](
        FunctionTool(
          name = "search",
          parameters = schema,
          strict = false,
          description = Some("Search for information")
        ),
        """{
          |  "name" : "search",
          |  "parameters" : {
          |    "properties" : {
          |      "query" : {
          |        "description" : "The search query",
          |        "type" : "string"
          |      }
          |    },
          |    "required" : [ "query" ],
          |    "type" : "object"
          |  },
          |  "strict" : false,
          |  "description" : "Search for information",
          |  "type" : "function"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize FunctionTool with nested objects and strict=true" in {
      val nestedSchema = JsonSchema.Object(
        properties = Seq(
          "city" -> JsonSchema.String(Some("City name")),
          "address" -> JsonSchema.Object(
            properties = Seq(
              "street" -> JsonSchema.String(Some("Street name")),
              "number" -> JsonSchema.Integer(Some("Street number"))
            ),
            required = Seq("street")
          )
        ),
        required = Seq("city", "address")
      )

      val tool: Tool = FunctionTool(
        name = "get_location",
        parameters = nestedSchema,
        strict = true,
        description = Some("Get location details")
      )

      val json = Json.toJson(tool).as[JsObject]
      val params = (json \ "parameters").as[JsObject]

      // Verify root object has additionalProperties: false
      assert((params \ "additionalProperties").as[Boolean] == false)

      // Verify nested object has additionalProperties: false
      val address = (params \ "properties" \ "address").as[JsObject]
      assert((address \ "additionalProperties").as[Boolean] == false)
    }

    "serialize and deserialize FileSearchTool" in {
      testCodec[Tool](
        FileSearchTool(),
        """{
          |  "vector_store_ids" : [ ],
          |  "type" : "file_search"
          |}""".stripMargin,
        Pretty
      )

      testCodec[Tool](
        FileSearchTool(
          vectorStoreIds = Seq("store1", "store2"),
          filters = Some(
            FileFilter.ComparisonFilter(
              key = "category",
              `type` = FileFilter.ComparisonOperator.Eq,
              value = "document"
            )
          ),
          maxNumResults = Some(10),
          rankingOptions = Some(
            FileSearchRankingOptions(
              ranker = Some("cohere-v3"),
              scoreThreshold = Some(0.2)
            )
          )
        ),
        """{
          |  "vector_store_ids" : [ "store1", "store2" ],
          |  "filters" : {
          |    "key" : "category",
          |    "type" : "eq",
          |    "value" : "document"
          |  },
          |  "max_num_results" : 10,
          |  "ranking_options" : {
          |    "ranker" : "cohere-v3",
          |    "score_threshold" : 0.2
          |  },
          |  "type" : "file_search"
          |}""".stripMargin,
        Pretty
      )

      testDeserialization[Tool](
        FileSearchTool(),
        """{
          |  "type" : "file_search"
          |}""".stripMargin
      )
    }

    "serialize and deserialize WebSearchTool" in {
      testCodec[Tool](
        WebSearchTool(),
        """{
          |  "type" : "web_search"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize WebSearchTool with all fields" in {
      testCodec[Tool](
        WebSearchTool(
          filters = Some(WebSearchFilters(allowedDomains = Seq("pubmed.ncbi.nlm.nih.gov", "arxiv.org"))),
          searchContextSize = Some("high"),
          userLocation = Some(
            WebSearchUserLocation(
              city = Some("San Francisco"),
              country = Some("US"),
              region = Some("California"),
              timezone = Some("America/Los_Angeles")
            )
          ),
          `type` = WebSearchType.WebSearch20250826
        ),
        """{
          |  "filters" : {
          |    "allowed_domains" : [ "pubmed.ncbi.nlm.nih.gov", "arxiv.org" ]
          |  },
          |  "search_context_size" : "high",
          |  "user_location" : {
          |    "city" : "San Francisco",
          |    "country" : "US",
          |    "region" : "California",
          |    "timezone" : "America/Los_Angeles",
          |    "type" : "approximate"
          |  },
          |  "type" : "web_search_2025_08_26"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ComputerUseTool" in {
      testCodec[Tool](
        ComputerUseTool(
          displayHeight = 1024,
          displayWidth = 768,
          environment = "linux"
        ),
        """{
          |  "display_height" : 1024,
          |  "display_width" : 768,
          |  "environment" : "linux",
          |  "type" : "computer_use_preview"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPTool minimal" in {
      testCodec[Tool](
        MCPTool(
          serverLabel = "my_mcp_server"
        ),
        """{
          |  "server_label" : "my_mcp_server",
          |  "type" : "mcp"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPTool with serverUrl" in {
      testCodec[Tool](
        MCPTool(
          serverLabel = "custom_server",
          serverUrl = Some("https://api.example.com/mcp"),
          serverDescription = Some("Custom MCP server for data analysis")
        ),
        """{
          |  "server_label" : "custom_server",
          |  "server_url" : "https://api.example.com/mcp",
          |  "server_description" : "Custom MCP server for data analysis",
          |  "type" : "mcp"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPTool with connectorId" in {
      testCodec[Tool](
        MCPTool(
          serverLabel = "dropbox_server",
          connectorId = Some(ConnectorId.Dropbox),
          authorization = Some("oauth_token_abc123"),
          requireApproval = Some(MCPRequireApproval.Setting.Always)
        ),
        """{
          |  "server_label" : "dropbox_server",
          |  "connector_id" : "connector_dropbox",
          |  "authorization" : "oauth_token_abc123",
          |  "require_approval" : "always",
          |  "type" : "mcp"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPTool with allowedTools array" in {
      testCodec[Tool](
        MCPTool(
          serverLabel = "my_server",
          allowedTools = Some(MCPAllowedTools.ToolNames(Seq("search", "get_data", "analyze")))
        ),
        """{
          |  "server_label" : "my_server",
          |  "allowed_tools" : [ "search", "get_data", "analyze" ],
          |  "type" : "mcp"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPTool with allowedTools filter" in {
      testCodec[Tool](
        MCPTool(
          serverLabel = "filtered_server",
          allowedTools = Some(
            MCPAllowedTools.Filter(
              readOnly = Some(true),
              toolNames = Some(Seq("read_file", "list_directory"))
            )
          )
        ),
        """{
          |  "server_label" : "filtered_server",
          |  "allowed_tools" : {
          |    "read_only" : true,
          |    "tool_names" : [ "read_file", "list_directory" ]
          |  },
          |  "type" : "mcp"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPTool with requireApproval filter" in {
      testCodec[Tool](
        MCPTool(
          serverLabel = "approval_server",
          requireApproval = Some(
            MCPRequireApproval.Filter(
              always = Some(
                MCPToolFilter(
                  readOnly = Some(false),
                  toolNames = Some(Seq("delete_file", "modify_data"))
                )
              ),
              never = Some(
                MCPToolFilter(
                  readOnly = Some(true),
                  toolNames = Some(Seq("read_file", "list_files"))
                )
              )
            )
          )
        ),
        """{
          |  "server_label" : "approval_server",
          |  "require_approval" : {
          |    "always" : {
          |      "read_only" : false,
          |      "tool_names" : [ "delete_file", "modify_data" ]
          |    },
          |    "never" : {
          |      "read_only" : true,
          |      "tool_names" : [ "read_file", "list_files" ]
          |    }
          |  },
          |  "type" : "mcp"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPTool with all fields" in {
      testCodec[Tool](
        MCPTool(
          serverLabel = "full_server",
          serverUrl = Some("https://api.example.com/mcp"),
          serverDescription = Some("Full featured server"),
          allowedTools = Some(MCPAllowedTools.ToolNames(Seq("tool1", "tool2"))),
          authorization = Some("bearer_token_xyz"),
          headers = Some(Map("X-API-Key" -> "key123", "X-Custom" -> "value")),
          requireApproval = Some(MCPRequireApproval.Setting.Never)
        ),
        """{
          |  "server_label" : "full_server",
          |  "allowed_tools" : [ "tool1", "tool2" ],
          |  "authorization" : "bearer_token_xyz",
          |  "headers" : {
          |    "X-API-Key" : "key123",
          |    "X-Custom" : "value"
          |  },
          |  "require_approval" : "never",
          |  "server_description" : "Full featured server",
          |  "server_url" : "https://api.example.com/mcp",
          |  "type" : "mcp"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize CodeInterpreterTool with container ID" in {
      testCodec[Tool](
        CodeInterpreterTool(
          container = CodeInterpreterContainer.ContainerId("container_abc123")
        ),
        """{
          |  "container" : "container_abc123",
          |  "type" : "code_interpreter"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize CodeInterpreterTool with auto container" in {
      testCodec[Tool](
        CodeInterpreterTool(
          container = CodeInterpreterContainer.Auto()
        ),
        """{
          |  "container" : {
          |    "type" : "auto",
          |    "file_ids" : [ ]
          |  },
          |  "type" : "code_interpreter"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize CodeInterpreterTool with auto container and files" in {
      testCodec[Tool](
        CodeInterpreterTool(
          container = CodeInterpreterContainer.Auto(
            fileIds = Seq("file_abc123", "file_xyz789")
          )
        ),
        """{
          |  "container" : {
          |    "type" : "auto",
          |    "file_ids" : [ "file_abc123", "file_xyz789" ]
          |  },
          |  "type" : "code_interpreter"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ImageGenerationTool with defaults" in {
      testCodec[Tool](
        ImageGenerationTool(),
        """{
          |  "background" : null,
          |  "model" : null,
          |  "moderation" : null,
          |  "output_compression" : null,
          |  "output_format" : null,
          |  "partial_images" : null,
          |  "quality" : null,
          |  "size" : null,
          |  "type" : "image_generation"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ImageGenerationTool with optional fields" in {
      testCodec[Tool](
        ImageGenerationTool(
          background = Some(ImageGenerationBackground.transparent),
          inputFidelity = Some("high"),
          quality = Some("high"),
          size = Some("1024x1024")
        ),
        """{
          |  "background" : "transparent",
          |  "input_fidelity" : "high",
          |  "model" : null,
          |  "moderation" : null,
          |  "output_compression" : null,
          |  "output_format" : null,
          |  "partial_images" : null,
          |  "quality" : "high",
          |  "size" : "1024x1024",
          |  "type" : "image_generation"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ImageGenerationTool with input image mask" in {
      testCodec[Tool](
        ImageGenerationTool(
          background = Some(ImageGenerationBackground.auto),
          inputImageMask = Some(InputImageMask(
            fileId = Some("file_abc123"),
            imageUrl = Some("https://example.com/mask.png")
          )),
          outputFormat = Some("jpeg"),
          outputCompression = Some(85)
        ),
        """{
          |  "background" : "auto",
          |  "input_image_mask" : {
          |    "file_id" : "file_abc123",
          |    "image_url" : "https://example.com/mask.png"
          |  },
          |  "model" : null,
          |  "moderation" : null,
          |  "output_compression" : 85,
          |  "output_format" : "jpeg",
          |  "partial_images" : null,
          |  "quality" : null,
          |  "size" : null,
          |  "type" : "image_generation"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ImageGenerationTool with all custom fields" in {
      testCodec[Tool](
        ImageGenerationTool(
          background = Some(ImageGenerationBackground.auto),
          inputFidelity = Some("medium"),
          inputImageMask = Some(InputImageMask(
            fileId = Some("file_mask789")
          )),
          model = Some("gpt-image-2"),
          moderation = Some("none"),
          outputCompression = Some(75),
          outputFormat = Some("webp"),
          partialImages = Some(3),
          quality = Some("high"),
          size = Some("1920x1080")
        ),
        """{
          |  "background" : "auto",
          |  "input_fidelity" : "medium",
          |  "input_image_mask" : {
          |    "file_id" : "file_mask789"
          |  },
          |  "model" : "gpt-image-2",
          |  "moderation" : "none",
          |  "output_compression" : 75,
          |  "output_format" : "webp",
          |  "partial_images" : 3,
          |  "quality" : "high",
          |  "size" : "1920x1080",
          |  "type" : "image_generation"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize FunctionToolCall" in {
      testCodec[ToolCall](
        FunctionToolCall(
          arguments = """{"location":"San Francisco, CA"}""",
          callId = "call_abc123",
          name = "get_weather",
          status = Some(ModelStatus.Completed)
        ),
        """{
          |  "arguments" : "{\"location\":\"San Francisco, CA\"}",
          |  "call_id" : "call_abc123",
          |  "name" : "get_weather",
          |  "status" : "completed",
          |  "type" : "function_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize FunctionToolCall with all optional fields" in {
      testCodec[ToolCall](
        FunctionToolCall(
          arguments = """{"city":"New York","units":"metric"}""",
          callId = "call_xyz789",
          name = "get_forecast",
          id = Some("func_call_456"),
          status = Some(ModelStatus.InProgress)
        ),
        """{
          |  "arguments" : "{\"city\":\"New York\",\"units\":\"metric\"}",
          |  "call_id" : "call_xyz789",
          |  "name" : "get_forecast",
          |  "id" : "func_call_456",
          |  "status" : "in_progress",
          |  "type" : "function_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize FileSearchToolCall" in {
      testCodec[ToolCall](
        FileSearchToolCall(
          id = "call_abc123",
          queries = Seq("find all PDFs about neural networks"),
          status = ModelStatus.Searching,
          results = Nil
        ),
        """{
          |  "id" : "call_abc123",
          |  "queries" : [ "find all PDFs about neural networks" ],
          |  "status" : "searching",
          |  "results" : [ ],
          |  "type" : "file_search_call"
          |}""".stripMargin,
        Pretty
      )

      testCodec[ToolCall](
        FileSearchToolCall(
          id = "call_abc123",
          queries = Seq("find all the clues!"),
          status = ModelStatus.InProgress,
          results = Seq(
            FileSearchResult(
              attributes = Map("key1" -> "perfect", "key2" -> "2"),
              fileId = Some("file_abc123"),
              filename = Some("file_abc123.pdf"),
              score = Some(0.5),
              text = Some("This is a test file")
            )
          )
        ),
        """{
          |  "id" : "call_abc123",
          |  "queries" : [ "find all the clues!" ],
          |  "status" : "in_progress",
          |  "results" : [ {
          |    "attributes" : {
          |      "key1" : "perfect",
          |      "key2" : "2"
          |    },
          |    "file_id" : "file_abc123",
          |    "filename" : "file_abc123.pdf",
          |    "score" : 0.5,
          |    "text" : "This is a test file"
          |  } ],
          |  "type" : "file_search_call"
          |}""".stripMargin,
        Pretty
      )

      testDeserialization[ToolCall](
        FileSearchToolCall(
          id = "call_abc123",
          queries = Seq("find all PDFs about neural networks"),
          status = ModelStatus.Searching
        ),
        """{
          |  "id" : "call_abc123",
          |  "queries" : [ "find all PDFs about neural networks" ],
          |  "status" : "searching",
          |  "type" : "file_search_call"
          |}""".stripMargin
      )

      testDeserialization[ToolCall](
        FileSearchToolCall(
          id = "call_abc123",
          status = ModelStatus.Searching
        ),
        """{
          |  "id" : "call_abc123",
          |  "status" : "searching",
          |  "type" : "file_search_call"
          |}""".stripMargin
      )

      // Test with explicit empty queries array
      testCodec[ToolCall](
        FileSearchToolCall(
          id = "call_def456",
          queries = Seq.empty,
          status = ModelStatus.InProgress
        ),
        """{
          |  "id" : "call_def456",
          |  "queries" : [ ],
          |  "status" : "in_progress",
          |  "results" : [ ],
          |  "type" : "file_search_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize FileSearchResult with all fields" in {
      // Test with all fields populated
      testCodec[FileSearchResult](
        FileSearchResult(
          attributes = Map(
            "stringKey" -> "stringValue",
            "boolKey" -> true,
            "numKey" -> 42
          ),
          fileId = Some("file_xyz789"),
          filename = Some("research.pdf"),
          score = Some(0.87),
          text = Some("This document contains relevant information about the topic.")
        ),
        """{
          |  "attributes" : {
          |    "stringKey" : "stringValue",
          |    "boolKey" : true,
          |    "numKey" : 42
          |  },
          |  "file_id" : "file_xyz789",
          |  "filename" : "research.pdf",
          |  "score" : 0.87,
          |  "text" : "This document contains relevant information about the topic."
          |}""".stripMargin,
        Pretty
      )

      // Test with minimal fields (all optional fields omitted)
      testCodec[FileSearchResult](
        FileSearchResult(),
        """{
          |  "attributes" : { }
          |}""".stripMargin,
        Pretty
      )

      // Test deserialization with completely empty object (all fields have defaults)
      testDeserialization[FileSearchResult](
        FileSearchResult(),
        "{ }"
      )

      // Test with attributes containing various data types
      testCodec[FileSearchResult](
        FileSearchResult(
          attributes = Map(
            "category" -> "machine-learning",
            "verified" -> false,
            "relevanceScore" -> 95.5,
            "pageCount" -> 128
          ),
          fileId = Some("file_ml_paper"),
          score = Some(0.95)
        ),
        """{
          |  "attributes" : {
          |    "category" : "machine-learning",
          |    "verified" : false,
          |    "relevanceScore" : 95.5,
          |    "pageCount" : 128
          |  },
          |  "file_id" : "file_ml_paper",
          |  "score" : 0.95
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize WebSearchToolCall with Search action" in {
      testCodec[ToolCall](
        WebSearchToolCall(
          action = WebSearchAction.Search(
            query = "best pizza restaurants in New York",
            sources = Seq(
              WebSearchSource("https://example.com"),
              WebSearchSource("https://example.org")
            )
          ),
          id = "call_abc123",
          status = ModelStatus.Completed
        ),
        """{
          |  "action" : {
          |    "query" : "best pizza restaurants in New York",
          |    "sources" : [ {
          |      "url" : "https://example.com",
          |      "type" : "url"
          |    }, {
          |      "url" : "https://example.org",
          |      "type" : "url"
          |    } ],
          |    "type" : "search"
          |  },
          |  "id" : "call_abc123",
          |  "status" : "completed",
          |  "type" : "web_search_call"
          |}""".stripMargin,
        Pretty
      )

      // Test with no sources (empty array)
      testCodec[ToolCall](
        WebSearchToolCall(
          action = WebSearchAction.Search(
            query = "machine learning tutorials"
          ),
          id = "call_def456",
          status = ModelStatus.InProgress
        ),
        """{
          |  "action" : {
          |    "query" : "machine learning tutorials",
          |    "sources" : [ ],
          |    "type" : "search"
          |  },
          |  "id" : "call_def456",
          |  "status" : "in_progress",
          |  "type" : "web_search_call"
          |}""".stripMargin,
        Pretty
      )

      // Test deserialization with missing sources field
      testDeserialization[ToolCall](
        WebSearchToolCall(
          action = WebSearchAction.Search(
            query = "climate change"
          ),
          id = "call_ghi789",
          status = ModelStatus.Completed
        ),
        """{
          |  "action" : {
          |    "query" : "climate change",
          |    "type" : "search"
          |  },
          |  "id" : "call_ghi789",
          |  "status" : "completed",
          |  "type" : "web_search_call"
          |}""".stripMargin
      )
    }

    "serialize and deserialize WebSearchToolCall with OpenPage action" in {
      testCodec[ToolCall](
        WebSearchToolCall(
          action = WebSearchAction.OpenPage(
            url = "https://www.example.com/article"
          ),
          id = "call_open123",
          status = ModelStatus.Completed
        ),
        """{
          |  "action" : {
          |    "url" : "https://www.example.com/article",
          |    "type" : "open_page"
          |  },
          |  "id" : "call_open123",
          |  "status" : "completed",
          |  "type" : "web_search_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize WebSearchToolCall with Find action" in {
      testCodec[ToolCall](
        WebSearchToolCall(
          action = WebSearchAction.Find(
            pattern = "contact information",
            url = "https://www.example.com/about"
          ),
          id = "call_find789",
          status = ModelStatus.Searching
        ),
        """{
          |  "action" : {
          |    "pattern" : "contact information",
          |    "url" : "https://www.example.com/about",
          |    "type" : "find"
          |  },
          |  "id" : "call_find789",
          |  "status" : "searching",
          |  "type" : "web_search_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ComputerToolCall" in {
      testCodec[ToolCall](
        ComputerToolCall(
          action = ComputerToolAction.Click(
            button = ComputerToolAction.ButtonClick.Left,
            x = 100,
            y = 200
          ),
          callId = "call_abc123",
          id = "call_abc123",
          status = ModelStatus.InProgress
        ),
        """{
          |  "action" : {
          |    "button" : "left",
          |    "x" : 100,
          |    "y" : 200,
          |    "type" : "click"
          |  },
          |  "call_id" : "call_abc123",
          |  "id" : "call_abc123",
          |  "pending_safety_checks" : [ ],
          |  "status" : "in_progress",
          |  "type" : "computer_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ComputerToolCall with DoubleClick action" in {
      testCodec[ToolCall](
        ComputerToolCall(
          action = ComputerToolAction.DoubleClick(
            x = 150,
            y = 250
          ),
          callId = "call_dblclick123",
          id = "call_dblclick123",
          status = ModelStatus.Completed
        ),
        """{
          |  "action" : {
          |    "x" : 150,
          |    "y" : 250,
          |    "type" : "double_click"
          |  },
          |  "call_id" : "call_dblclick123",
          |  "id" : "call_dblclick123",
          |  "pending_safety_checks" : [ ],
          |  "status" : "completed",
          |  "type" : "computer_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ComputerToolCall with Drag action" in {
      testCodec[ToolCall](
        ComputerToolCall(
          action = ComputerToolAction.Drag(
            path = Seq(
              ComputerToolAction.Coordinate(100, 200),
              ComputerToolAction.Coordinate(200, 300),
              ComputerToolAction.Coordinate(300, 400)
            )
          ),
          callId = "call_drag123",
          id = "call_drag123",
          status = ModelStatus.InProgress
        ),
        """{
          |  "action" : {
          |    "path" : [ {
          |      "x" : 100,
          |      "y" : 200
          |    }, {
          |      "x" : 200,
          |      "y" : 300
          |    }, {
          |      "x" : 300,
          |      "y" : 400
          |    } ],
          |    "type" : "drag"
          |  },
          |  "call_id" : "call_drag123",
          |  "id" : "call_drag123",
          |  "pending_safety_checks" : [ ],
          |  "status" : "in_progress",
          |  "type" : "computer_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ComputerToolCall with KeyPress action" in {
      testCodec[ToolCall](
        ComputerToolCall(
          action = ComputerToolAction.KeyPress(
            keys = Seq("Control", "C")
          ),
          callId = "call_keypress123",
          id = "call_keypress123",
          status = ModelStatus.Completed
        ),
        """{
          |  "action" : {
          |    "keys" : [ "Control", "C" ],
          |    "type" : "keypress"
          |  },
          |  "call_id" : "call_keypress123",
          |  "id" : "call_keypress123",
          |  "pending_safety_checks" : [ ],
          |  "status" : "completed",
          |  "type" : "computer_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ComputerToolCall with Move action" in {
      testCodec[ToolCall](
        ComputerToolCall(
          action = ComputerToolAction.Move(
            x = 500,
            y = 600
          ),
          callId = "call_move123",
          id = "call_move123",
          status = ModelStatus.InProgress
        ),
        """{
          |  "action" : {
          |    "x" : 500,
          |    "y" : 600,
          |    "type" : "move"
          |  },
          |  "call_id" : "call_move123",
          |  "id" : "call_move123",
          |  "pending_safety_checks" : [ ],
          |  "status" : "in_progress",
          |  "type" : "computer_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ComputerToolCall with Screenshot action" in {
      testCodec[ToolCall](
        ComputerToolCall(
          action = ComputerToolAction.Screenshot,
          callId = "call_screenshot123",
          id = "call_screenshot123",
          status = ModelStatus.Completed
        ),
        """{
          |  "action" : {
          |    "type" : "screenshot"
          |  },
          |  "call_id" : "call_screenshot123",
          |  "id" : "call_screenshot123",
          |  "pending_safety_checks" : [ ],
          |  "status" : "completed",
          |  "type" : "computer_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ComputerToolCall with Scroll action" in {
      testCodec[ToolCall](
        ComputerToolCall(
          action = ComputerToolAction.Scroll(
            scrollX = 10,
            scrollY = -50,
            x = 400,
            y = 300
          ),
          callId = "call_scroll123",
          id = "call_scroll123",
          status = ModelStatus.InProgress
        ),
        """{
          |  "action" : {
          |    "scroll_x" : 10,
          |    "scroll_y" : -50,
          |    "x" : 400,
          |    "y" : 300,
          |    "type" : "scroll"
          |  },
          |  "call_id" : "call_scroll123",
          |  "id" : "call_scroll123",
          |  "pending_safety_checks" : [ ],
          |  "status" : "in_progress",
          |  "type" : "computer_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ComputerToolCall with Type action" in {
      testCodec[ToolCall](
        ComputerToolCall(
          action = ComputerToolAction.Type(
            text = "Hello, World!"
          ),
          callId = "call_type123",
          id = "call_type123",
          status = ModelStatus.Completed
        ),
        """{
          |  "action" : {
          |    "text" : "Hello, World!",
          |    "type" : "type"
          |  },
          |  "call_id" : "call_type123",
          |  "id" : "call_type123",
          |  "pending_safety_checks" : [ ],
          |  "status" : "completed",
          |  "type" : "computer_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ComputerToolCall with Wait action" in {
      testCodec[ToolCall](
        ComputerToolCall(
          action = ComputerToolAction.Wait,
          callId = "call_wait123",
          id = "call_wait123",
          status = ModelStatus.InProgress
        ),
        """{
          |  "action" : {
          |    "type" : "wait"
          |  },
          |  "call_id" : "call_wait123",
          |  "id" : "call_wait123",
          |  "pending_safety_checks" : [ ],
          |  "status" : "in_progress",
          |  "type" : "computer_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize FunctionToolCallOutput with string output" in {
      testCodec[FunctionToolCallOutput](
        FunctionToolCallOutput(
          callId = "call_abc123",
          output = FunctionToolOutput.StringOutput("""{"temperature":72,"unit":"F"}"""),
          id = None,
          status = None
        ),
        """{
          |  "call_id" : "call_abc123",
          |  "output" : "{\"temperature\":72,\"unit\":\"F\"}"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize FunctionToolCallOutput with content output" in {
      testCodec[FunctionToolCallOutput](
        FunctionToolCallOutput(
          callId = "call_func789",
          output = FunctionToolOutput.ContentOutput(
            Seq(
              InputMessageContent.Text("Result text"),
              InputMessageContent.Image(
                fileId = Some("file_image123"),
                imageUrl = None,
                detail = Some("high")
              )
            )
          ),
          id = Some("output_456"),
          status = Some(ModelStatus.Completed)
        ),
        """{
          |  "call_id" : "call_func789",
          |  "output" : [ {
          |    "text" : "Result text",
          |    "type" : "input_text"
          |  }, {
          |    "file_id" : "file_image123",
          |    "detail" : "high",
          |    "type" : "input_image"
          |  } ],
          |  "id" : "output_456",
          |  "status" : "completed"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ImageGenerationToolCall" in {
      testCodec[ImageGenerationToolCall](
        ImageGenerationToolCall(
          id = "img_gen_abc123",
          result = "base64_encoded_image_data_here",
          status = "completed"
        ),
        """{
          |  "id" : "img_gen_abc123",
          |  "result" : "base64_encoded_image_data_here",
          |  "status" : "completed",
          |  "type" : "image_generation_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize CodeInterpreterOutputLogs" in {
      testCodec[CodeInterpreterOutput](
        CodeInterpreterOutputLogs(
          logs = "Installing package...\nSuccess!"
        ),
        """{
          |  "logs" : "Installing package...\nSuccess!",
          |  "type" : "logs"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize CodeInterpreterOutputImage" in {
      testCodec[CodeInterpreterOutput](
        CodeInterpreterOutputImage(
          url = "https://example.com/output.png"
        ),
        """{
          |  "url" : "https://example.com/output.png",
          |  "type" : "image"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize CodeInterpreterToolCall" in {
      testCodec[CodeInterpreterToolCall](
        CodeInterpreterToolCall(
          id = "code_interp_abc123",
          code = Some("import matplotlib.pyplot as plt\nplt.plot([1, 2, 3])"),
          containerId = "container_xyz789",
          outputs = Seq(
            CodeInterpreterOutputLogs("Executing code..."),
            CodeInterpreterOutputImage("https://example.com/plot.png")
          ),
          status = "completed"
        ),
        """{
          |  "id" : "code_interp_abc123",
          |  "code" : "import matplotlib.pyplot as plt\nplt.plot([1, 2, 3])",
          |  "container_id" : "container_xyz789",
          |  "outputs" : [ {
          |    "logs" : "Executing code...",
          |    "type" : "logs"
          |  }, {
          |    "url" : "https://example.com/plot.png",
          |    "type" : "image"
          |  } ],
          |  "status" : "completed",
          |  "type" : "code_interpreter_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize CodeInterpreterToolCall with no code" in {
      testCodec[CodeInterpreterToolCall](
        CodeInterpreterToolCall(
          id = "code_interp_def456",
          code = None,
          containerId = "container_abc123",
          outputs = Seq.empty,
          status = "in_progress"
        ),
        """{
          |  "id" : "code_interp_def456",
          |  "container_id" : "container_abc123",
          |  "outputs" : [ ],
          |  "status" : "in_progress",
          |  "type" : "code_interpreter_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize LocalShellAction" in {
      testCodec[LocalShellAction](
        LocalShellAction(
          command = Seq("ls", "-la"),
          env = Map("PATH" -> "/usr/bin", "HOME" -> "/home/user"),
          timeoutMs = Some(5000),
          user = Some("ubuntu"),
          workingDirectory = Some("/home/ubuntu")
        ),
        """{
          |  "command" : [ "ls", "-la" ],
          |  "env" : {
          |    "PATH" : "/usr/bin",
          |    "HOME" : "/home/user"
          |  },
          |  "type" : "exec",
          |  "timeout_ms" : 5000,
          |  "user" : "ubuntu",
          |  "working_directory" : "/home/ubuntu"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize LocalShellToolCall" in {
      testCodec[LocalShellToolCall](
        LocalShellToolCall(
          action = LocalShellAction(
            command = Seq("python", "script.py"),
            env = Map("PYTHONPATH" -> "/usr/lib/python3"),
            timeoutMs = Some(10000),
            user = None,
            workingDirectory = Some("/app")
          ),
          callId = "call_shell_abc123",
          id = "shell_xyz789",
          status = "completed"
        ),
        """{
          |  "action" : {
          |    "command" : [ "python", "script.py" ],
          |    "env" : {
          |      "PYTHONPATH" : "/usr/lib/python3"
          |    },
          |    "type" : "exec",
          |    "timeout_ms" : 10000,
          |    "working_directory" : "/app"
          |  },
          |  "call_id" : "call_shell_abc123",
          |  "id" : "shell_xyz789",
          |  "status" : "completed",
          |  "type" : "local_shell_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize LocalShellToolCall with minimal action" in {
      testCodec[LocalShellToolCall](
        LocalShellToolCall(
          action = LocalShellAction(
            command = Seq("echo", "hello"),
            env = Map.empty
          ),
          callId = "call_shell_def456",
          id = "shell_abc123",
          status = "in_progress"
        ),
        """{
          |  "action" : {
          |    "command" : [ "echo", "hello" ],
          |    "env" : { },
          |    "type" : "exec"
          |  },
          |  "call_id" : "call_shell_def456",
          |  "id" : "shell_abc123",
          |  "status" : "in_progress",
          |  "type" : "local_shell_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize LocalShellToolCallOutput" in {
      testCodec[LocalShellToolCallOutput](
        LocalShellToolCallOutput(
          id = "shell_output_abc123",
          output = """{"exit_code": 0, "stdout": "Hello World\n", "stderr": ""}""",
          status = Some("completed")
        ),
        """{
          |  "id" : "shell_output_abc123",
          |  "output" : "{\"exit_code\": 0, \"stdout\": \"Hello World\\n\", \"stderr\": \"\"}",
          |  "status" : "completed",
          |  "type" : "local_shell_call_output"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize LocalShellToolCallOutput without status" in {
      testCodec[LocalShellToolCallOutput](
        LocalShellToolCallOutput(
          id = "shell_output_def456",
          output = """{"result": "success"}"""
        ),
        """{
          |  "id" : "shell_output_def456",
          |  "output" : "{\"result\": \"success\"}",
          |  "type" : "local_shell_call_output"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPTool" in {
      testCodec[MCPToolRef](
        MCPToolRef(
          inputSchema = Map("type" -> "object", "properties" -> Map("query" -> Map("type" -> "string"))),
          name = "search_tool",
          annotations = Map("category" -> "search"),
          description = Some("A tool to search for information")
        ),
        """{
          |  "input_schema" : {
          |    "type" : "object",
          |    "properties" : {
          |      "query" : {
          |        "type" : "string"
          |      }
          |    }
          |  },
          |  "name" : "search_tool",
          |  "annotations" : {
          |    "category" : "search"
          |  },
          |  "type" : "mcp_tool",
          |  "description" : "A tool to search for information"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPListTools" in {
      testCodec[MCPListTools](
        MCPListTools(
          id = "mcp_list_abc123",
          serverLabel = "my_mcp_server",
          tools = Seq(
            MCPToolRef(
              inputSchema = Map("type" -> "object"),
              name = "tool1"
            ),
            MCPToolRef(
              inputSchema = Map("type" -> "object"),
              name = "tool2",
              description = Some("Tool 2 description")
            )
          ),
          error = None
        ),
        """{
          |  "id" : "mcp_list_abc123",
          |  "server_label" : "my_mcp_server",
          |  "tools" : [ {
          |    "input_schema" : {
          |      "type" : "object"
          |    },
          |    "name" : "tool1",
          |    "annotations" : { },
          |    "type" : "mcp_tool"
          |  }, {
          |    "input_schema" : {
          |      "type" : "object"
          |    },
          |    "name" : "tool2",
          |    "annotations" : { },
          |    "type" : "mcp_tool",
          |    "description" : "Tool 2 description"
          |  } ],
          |  "type" : "mcp_list_tools"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPListTools with error" in {
      testCodec[MCPListTools](
        MCPListTools(
          id = "mcp_list_def456",
          serverLabel = "failing_server",
          tools = Seq.empty,
          error = Some("Connection to server failed")
        ),
        """{
          |  "id" : "mcp_list_def456",
          |  "server_label" : "failing_server",
          |  "tools" : [ ],
          |  "error" : "Connection to server failed",
          |  "type" : "mcp_list_tools"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPApprovalRequest" in {
      testCodec[MCPApprovalRequest](
        MCPApprovalRequest(
          arguments = """{"param1": "value1", "param2": 42}""",
          id = "approval_req_abc123",
          name = "risky_operation",
          serverLabel = "production_server"
        ),
        """{
          |  "arguments" : "{\"param1\": \"value1\", \"param2\": 42}",
          |  "id" : "approval_req_abc123",
          |  "name" : "risky_operation",
          |  "server_label" : "production_server",
          |  "type" : "mcp_approval_request"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPApprovalResponse with approval" in {
      testCodec[MCPApprovalResponse](
        MCPApprovalResponse(
          approvalRequestId = "approval_req_abc123",
          approve = true,
          id = Some("approval_resp_xyz789"),
          reason = Some("Verified with supervisor")
        ),
        """{
          |  "approval_request_id" : "approval_req_abc123",
          |  "approve" : true,
          |  "id" : "approval_resp_xyz789",
          |  "reason" : "Verified with supervisor",
          |  "type" : "mcp_approval_response"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPApprovalResponse without optional fields" in {
      testCodec[MCPApprovalResponse](
        MCPApprovalResponse(
          approvalRequestId = "approval_req_def456",
          approve = false
        ),
        """{
          |  "approval_request_id" : "approval_req_def456",
          |  "approve" : false,
          |  "type" : "mcp_approval_response"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPToolCall with all optional fields" in {
      testCodec[MCPToolCall](
        MCPToolCall(
          arguments = """{"query": "search term", "limit": 10}""",
          id = "mcp_call_abc123",
          name = "search_tool",
          serverLabel = "my_server",
          approvalRequestId = Some("approval_xyz789"),
          error = None,
          output = Some("""{"results": [{"title": "Result 1"}]}"""),
          status = Some(ModelStatus.Completed)
        ),
        """{
          |  "arguments" : "{\"query\": \"search term\", \"limit\": 10}",
          |  "id" : "mcp_call_abc123",
          |  "name" : "search_tool",
          |  "server_label" : "my_server",
          |  "approval_request_id" : "approval_xyz789",
          |  "output" : "{\"results\": [{\"title\": \"Result 1\"}]}",
          |  "status" : "completed",
          |  "type" : "mcp_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPToolCall with error" in {
      testCodec[MCPToolCall](
        MCPToolCall(
          arguments = """{"action": "delete"}""",
          id = "mcp_call_def456",
          name = "risky_tool",
          serverLabel = "prod_server",
          error = Some("Permission denied"),
          status = Some(ModelStatus.Failed)
        ),
        """{
          |  "arguments" : "{\"action\": \"delete\"}",
          |  "id" : "mcp_call_def456",
          |  "name" : "risky_tool",
          |  "server_label" : "prod_server",
          |  "error" : "Permission denied",
          |  "status" : "failed",
          |  "type" : "mcp_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MCPToolCall minimal" in {
      testCodec[MCPToolCall](
        MCPToolCall(
          arguments = """{"param": "value"}""",
          id = "mcp_call_ghi789",
          name = "simple_tool",
          serverLabel = "dev_server"
        ),
        """{
          |  "arguments" : "{\"param\": \"value\"}",
          |  "id" : "mcp_call_ghi789",
          |  "name" : "simple_tool",
          |  "server_label" : "dev_server",
          |  "type" : "mcp_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize CustomToolCall with all fields" in {
      testCodec[CustomToolCall](
        CustomToolCall(
          callId = "call_custom_123",
          input = """{"query": "search term", "limit": 10}""",
          name = "custom_search_tool",
          id = Some("custom_call_abc123")
        ),
        """{
          |  "call_id" : "call_custom_123",
          |  "input" : "{\"query\": \"search term\", \"limit\": 10}",
          |  "name" : "custom_search_tool",
          |  "id" : "custom_call_abc123",
          |  "type" : "custom_tool_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize CustomToolCall minimal" in {
      testCodec[CustomToolCall](
        CustomToolCall(
          callId = "call_custom_456",
          input = """{"action": "process"}""",
          name = "process_tool"
        ),
        """{
          |  "call_id" : "call_custom_456",
          |  "input" : "{\"action\": \"process\"}",
          |  "name" : "process_tool",
          |  "type" : "custom_tool_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize CustomToolCallOutput with string output" in {
      testCodec[CustomToolCallOutput](
        CustomToolCallOutput(
          callId = "call_custom_123",
          output = FunctionToolOutput.StringOutput("""{"result": "success", "data": 42}"""),
          id = Some("output_abc123")
        ),
        """{
          |  "call_id" : "call_custom_123",
          |  "output" : "{\"result\": \"success\", \"data\": 42}",
          |  "id" : "output_abc123",
          |  "type" : "custom_tool_call_output"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize CustomToolCallOutput with content output" in {
      testCodec[CustomToolCallOutput](
        CustomToolCallOutput(
          callId = "call_custom_456",
          output = FunctionToolOutput.ContentOutput(
            Seq(
              InputMessageContent.Text("Processing completed successfully"),
              InputMessageContent.Image(fileId = Some("file_result_789"))
            )
          ),
          id = Some("output_def456")
        ),
        """{
          |  "call_id" : "call_custom_456",
          |  "output" : [ {
          |    "text" : "Processing completed successfully",
          |    "type" : "input_text"
          |  }, {
          |    "file_id" : "file_result_789",
          |    "type" : "input_image"
          |  } ],
          |  "id" : "output_def456",
          |  "type" : "custom_tool_call_output"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize CustomToolCallOutput minimal" in {
      testCodec[CustomToolCallOutput](
        CustomToolCallOutput(
          callId = "call_custom_789",
          output = FunctionToolOutput.StringOutput("""{"status": "done"}""")
        ),
        """{
          |  "call_id" : "call_custom_789",
          |  "output" : "{\"status\": \"done\"}",
          |  "type" : "custom_tool_call_output"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ComputerToolCallOutput" in {
      testCodec[ComputerToolCallOutput](
        ComputerToolCallOutput(
          callId = "call_abc123",
          output = ComputerScreenshot(
            fileId = Some("file_abc123"),
            imageUrl = None
          )
        ),
        """{
          |  "call_id" : "call_abc123",
          |  "output" : {
          |    "file_id" : "file_abc123",
          |    "image_url" : null,
          |    "type" : "computer_screenshot"
          |  },
          |  "acknowledged_safety_checks" : [ ]
          |}""".stripMargin,
        Pretty
      )

      testDeserialization[ComputerToolCallOutput](
        ComputerToolCallOutput(
          callId = "call_abc123",
          output = ComputerScreenshot(
            fileId = Some("file_abc123"),
            imageUrl = None
          )
        ),
        """{
          |  "call_id" : "call_abc123",
          |  "output" : {
          |    "file_id" : "file_abc123",
          |    "image_url" : null,
          |    "type" : "computer_screenshot"
          |  }
        }""".stripMargin
      )

      testCodec[ComputerToolCallOutput](
        ComputerToolCallOutput(
          callId = "call_abc123",
          output = ComputerScreenshot(
            fileId = Some("file_abc123"),
            imageUrl = None
          ),
          acknowledgedSafetyChecks = Seq(
            AcknowledgedSafetyCheck(
              id = "safety_check_1",
              code = Some("safety_code_1"),
              message = Some("Safety check 1")
            )
          ),
          id = Some("call_abc123"),
          status = Some(ModelStatus.Completed)
        ),
        """{
          |  "call_id" : "call_abc123",
          |  "output" : {
          |    "file_id" : "file_abc123",
          |    "image_url" : null,
          |    "type" : "computer_screenshot"
          |  },
          |  "acknowledged_safety_checks" : [ {
          |    "id" : "safety_check_1",
          |    "code" : "safety_code_1",
          |    "message" : "Safety check 1"
          |  } ],
          |  "id" : "call_abc123",
          |  "status" : "completed"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ComputerToolCallOutput with imageUrl" in {
      testCodec[ComputerToolCallOutput](
        ComputerToolCallOutput(
          callId = "call_screenshot456",
          output = ComputerScreenshot(
            fileId = None,
            imageUrl = Some("https://example.com/screenshot.png")
          ),
          acknowledgedSafetyChecks = Seq(
            AcknowledgedSafetyCheck(
              id = "safety_check_2",
              code = None,
              message = None
            )
          ),
          id = Some("call_screenshot456"),
          status = Some(ModelStatus.InProgress)
        ),
        """{
          |  "call_id" : "call_screenshot456",
          |  "output" : {
          |    "file_id" : null,
          |    "image_url" : "https://example.com/screenshot.png",
          |    "type" : "computer_screenshot"
          |  },
          |  "acknowledged_safety_checks" : [ {
          |    "id" : "safety_check_2"
          |  } ],
          |  "id" : "call_screenshot456",
          |  "status" : "in_progress"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ModelStatus values" in {
      testCodec[ModelStatus](
        ModelStatus.InProgress,
        "\"in_progress\"",
        Pretty
      )

      testCodec[ModelStatus](
        ModelStatus.Completed,
        "\"completed\"",
        Pretty
      )

      testCodec[ModelStatus](
        ModelStatus.Incomplete,
        "\"incomplete\"",
        Pretty
      )

      testCodec[ModelStatus](
        ModelStatus.Searching,
        "\"searching\"",
        Pretty
      )

      testCodec[ModelStatus](
        ModelStatus.Failed,
        "\"failed\"",
        Pretty
      )
    }

    "serialize and deserialize TruncationStrategy values" in {
      testCodec[TruncationStrategy](
        TruncationStrategy.Auto,
        "\"auto\"",
        Pretty
      )

      testCodec[TruncationStrategy](
        TruncationStrategy.Disabled,
        "\"disabled\"",
        Pretty
      )
    }

    "serialize and deserialize ResponseFormat values" in {
      testCodec[ResponseFormat](
        ResponseFormat.Text,
        """{
          |  "type" : "text"
          |}""".stripMargin,
        Pretty
      )

      testCodec[ResponseFormat](
        ResponseFormat.JsonObject,
        """{
          |  "type" : "json_object"
          |}""".stripMargin,
        Pretty
      )

      testCodec[ResponseFormat](
        ResponseFormat.JsonSchemaSpec(
          schema = JsonSchema.Object(
            properties = Seq(
              "name" -> JsonSchema.String(Some("The person's name")),
              "age" -> JsonSchema.Integer(Some("The person's age"))
            ),
            required = Seq("name")
          ),
          description = Some("A schema for person data"),
          name = Some("person_schema"),
          strict = Some(true)
        ),
        """{
          |  "schema" : {
          |    "properties" : {
          |      "name" : {
          |        "description" : "The person's name",
          |        "type" : "string"
          |      },
          |      "age" : {
          |        "description" : "The person's age",
          |        "type" : "integer"
          |      }
          |    },
          |    "required" : [ "name" ],
          |    "type" : "object"
          |  },
          |  "description" : "A schema for person data",
          |  "name" : "person_schema",
          |  "strict" : true,
          |  "type" : "json_schema"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize TextResponseConfig" in {
      testCodec[TextResponseConfig](
        TextResponseConfig(
          format = ResponseFormat.Text
        ),
        """{
          |  "format" : {
          |    "type" : "text"
          |  }
          |}""".stripMargin,
        Pretty
      )

      testCodec[TextResponseConfig](
        TextResponseConfig(
          format = ResponseFormat.JsonObject
        ),
        """{
          |  "format" : {
          |    "type" : "json_object"
          |  }
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ReasoningEffort values" in {
      testCodec[ReasoningEffort](
        ReasoningEffort.low,
        "\"low\"",
        Pretty
      )

      testCodec[ReasoningEffort](
        ReasoningEffort.medium,
        "\"medium\"",
        Pretty
      )

      testCodec[ReasoningEffort](
        ReasoningEffort.high,
        "\"high\"",
        Pretty
      )
    }

    "serialize and deserialize ReasoningConfig" in {
      testCodec[ReasoningConfig](
        ReasoningConfig(
          effort = Some(ReasoningEffort.medium),
          generateSummary = Some("concise")
        ),
        """{
          |  "effort" : "medium",
          |  "generate_summary" : "concise"
          |}""".stripMargin,
        Pretty
      )

      testCodec[ReasoningConfig](
        ReasoningConfig(),
        "{ }",
        Pretty
      )
    }

    "serialize and deserialize SummaryText" in {
      testCodec[SummaryText](
        SummaryText(
          text = "This is a summary text"
        ),
        """{
          |  "text" : "This is a summary text",
          |  "type" : "summary_text"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ReasoningText" in {
      testCodec[ReasoningText](
        ReasoningText(
          text = "This is a reasoning text"
        ),
        """{
          |  "text" : "This is a reasoning text",
          |  "type" : "reasoning_text"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Reasoning" in {
      testCodec[Reasoning](
        Reasoning(
          id = "reasoning_abc123",
          summary = Seq(
            SummaryText("First summary step"),
            SummaryText("Second summary step")
          ),
          status = Some(ModelStatus.Completed)
        ),
        """{
          |  "id" : "reasoning_abc123",
          |  "summary" : [ {
          |    "text" : "First summary step",
          |    "type" : "summary_text"
          |  }, {
          |    "text" : "Second summary step",
          |    "type" : "summary_text"
          |  } ],
          |  "content" : [ ],
          |  "status" : "completed"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Reasoning with content and encrypted_content" in {
      testCodec[Reasoning](
        Reasoning(
          id = "reasoning_xyz789",
          summary = Seq(
            SummaryText("Summary text")
          ),
          content = Seq(
            ReasoningText("Detailed reasoning step 1"),
            ReasoningText("Detailed reasoning step 2")
          ),
          encryptedContent = Some("encrypted_data_here"),
          status = Some(ModelStatus.InProgress)
        ),
        """{
          |  "id" : "reasoning_xyz789",
          |  "summary" : [ {
          |    "text" : "Summary text",
          |    "type" : "summary_text"
          |  } ],
          |  "content" : [ {
          |    "text" : "Detailed reasoning step 1",
          |    "type" : "reasoning_text"
          |  }, {
          |    "text" : "Detailed reasoning step 2",
          |    "type" : "reasoning_text"
          |  } ],
          |  "encrypted_content" : "encrypted_data_here",
          |  "status" : "in_progress"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ItemReference" in {
      testCodec[ItemReference](
        ItemReference(
          id = "item_abc123"
        ),
        """{
          |  "id" : "item_abc123"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize InputTokensDetails" in {
      testCodec[InputTokensDetails](
        InputTokensDetails(
          cachedTokens = Some(100)
        ),
        """{
          |  "cached_tokens" : 100
          |}""".stripMargin,
        Pretty
      )

      testCodec[InputTokensDetails](
        InputTokensDetails(),
        "{ }",
        Pretty
      )
    }

    "serialize and deserialize OutputTokensDetails" in {
      testCodec[OutputTokensDetails](
        OutputTokensDetails(
          reasoningTokens = 150
        ),
        """{
          |  "reasoning_tokens" : 150
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize UsageInfo" in {
      testCodec[UsageInfo](
        UsageInfo(
          inputTokens = 100,
          inputTokensDetails = Some(
            InputTokensDetails(
              cachedTokens = Some(50)
            )
          ),
          outputTokens = 200,
          outputTokensDetails = Some(
            OutputTokensDetails(
              reasoningTokens = 120
            )
          ),
          totalTokens = 300
        ),
        """{
          |  "input_tokens" : 100,
          |  "input_tokens_details" : {
          |    "cached_tokens" : 50
          |  },
          |  "output_tokens" : 200,
          |  "output_tokens_details" : {
          |    "reasoning_tokens" : 120
          |  },
          |  "total_tokens" : 300
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize InputMessageContent.Text" in {
      testCodec[InputMessageContent](
        InputMessageContent.Text(
          text = "Hello, world!"
        ),
        """{
          |  "text" : "Hello, world!",
          |  "type" : "input_text"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize InputMessageContent.Image" in {
      testCodec[InputMessageContent](
        InputMessageContent.Image(
          fileId = Some("file_abc123"),
          imageUrl = Some("https://example.com/image.jpg"),
          detail = Some("high")
        ),
        """{
          |  "file_id" : "file_abc123",
          |  "image_url" : "https://example.com/image.jpg",
          |  "detail" : "high",
          |  "type" : "input_image"
          |}""".stripMargin,
        Pretty
      )

      testCodec[InputMessageContent](
        InputMessageContent.Image(
          detail = Some("auto")
        ),
        """{
          |  "detail" : "auto",
          |  "type" : "input_image"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize InputMessageContent.File" in {
      testCodec[InputMessageContent](
        InputMessageContent.File(
          fileData = Some("file content"),
          fileId = Some("file_abc123"),
          filename = Some("document.pdf")
        ),
        """{
          |  "file_data" : "file content",
          |  "file_id" : "file_abc123",
          |  "filename" : "document.pdf",
          |  "type" : "input_file"
          |}""".stripMargin,
        Pretty
      )

      testCodec[InputMessageContent](
        InputMessageContent.File(
          fileUrl = Some("https://example.com/files/document.pdf"),
          filename = Some("document.pdf")
        ),
        """{
          |  "file_url" : "https://example.com/files/document.pdf",
          |  "filename" : "document.pdf",
          |  "type" : "input_file"
          |}""".stripMargin,
        Pretty
      )

      testCodec[InputMessageContent](
        InputMessageContent.File(),
        """{
          |  "type" : "input_file"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize OutputMessageContent.OutputText" in {
      testCodec[OutputMessageContent](
        OutputMessageContent.OutputText(
          text = "This is a response from the model",
          annotations = Seq(
            Annotation.UrlCitation(
              startIndex = 0,
              endIndex = 10,
              url = "https://example.com/1",
              title = "annotation1"
            ),
            Annotation.UrlCitation(
              startIndex = 11,
              endIndex = 21,
              url = "https://example.com/2",
              title = "annotation2"
            )
          )
        ),
        """{
          |  "annotations" : [ {
          |    "start_index" : 0,
          |    "end_index" : 10,
          |    "url" : "https://example.com/1",
          |    "title" : "annotation1",
          |    "type" : "url_citation"
          |  }, {
          |    "start_index" : 11,
          |    "end_index" : 21,
          |    "url" : "https://example.com/2",
          |    "title" : "annotation2",
          |    "type" : "url_citation"
          |  } ],
          |  "text" : "This is a response from the model",
          |  "type" : "output_text"
          |}""".stripMargin,
        Pretty
      )

      testCodec[OutputMessageContent](
        OutputMessageContent.OutputText(
          text = "This is a response from the model"
        ),
        """{
          |  "annotations" : [ ],
          |  "text" : "This is a response from the model",
          |  "type" : "output_text"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize OutputMessageContent.Refusal" in {
      testCodec[OutputMessageContent](
        OutputMessageContent.Refusal(
          refusal = "I cannot comply with this request as it violates content policy."
        ),
        """{
          |  "refusal" : "I cannot comply with this request as it violates content policy.",
          |  "type" : "refusal"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize InputMessage with different content types" in {
      testCodec[Message.InputContent](
        Message.InputContent(
          content = Seq(
            InputMessageContent.Text("Hello, I have a question."),
            InputMessageContent.Image(
              fileId = Some("file_abc123"),
              detail = Some("high")
            ),
            InputMessageContent.File(
              fileId = Some("file_def456"),
              filename = Some("report.pdf")
            )
          ),
          role = ChatRole.User,
          status = Some(ModelStatus.Completed)
        ),
        """{
          |  "content" : [ {
          |    "text" : "Hello, I have a question.",
          |    "type" : "input_text"
          |  }, {
          |    "file_id" : "file_abc123",
          |    "detail" : "high",
          |    "type" : "input_image"
          |  }, {
          |    "file_id" : "file_def456",
          |    "filename" : "report.pdf",
          |    "type" : "input_file"
          |  } ],
          |  "role" : "user",
          |  "status" : "completed"
          |}""".stripMargin,
        Pretty
      )

      testCodec[Message.InputContent](
        Message.InputContent(
          content = Seq(
            InputMessageContent.Text("Can you analyze this image?"),
            InputMessageContent.Image(
              imageUrl = Some("https://example.com/image.jpg"),
              detail = Some("auto")
            )
          ),
          role = ChatRole.Developer
        ),
        """{
          |  "content" : [ {
          |    "text" : "Can you analyze this image?",
          |    "type" : "input_text"
          |  }, {
          |    "image_url" : "https://example.com/image.jpg",
          |    "detail" : "auto",
          |    "type" : "input_image"
          |  } ],
          |  "role" : "developer"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize OutputMessage with different content types" in {
      testCodec[Message.OutputContent](
        Message.OutputContent(
          content = Seq(
            OutputMessageContent.OutputText(
              text = "I've analyzed the image and found the following:"
            ),
            OutputMessageContent.OutputText(
              annotations = Seq(
                Annotation.UrlCitation(
                  startIndex = 0,
                  endIndex = 10,
                  url = "https://example.com/citation1",
                  title = "Citation 1"
                ),
                Annotation.UrlCitation(
                  startIndex = 12,
                  endIndex = 22,
                  url = "https://example.com/reference2",
                  title = "Reference 2"
                )
              ),
              text = "The image shows a classic example of Renaissance architecture."
            )
          ),
          id = "output_abc123",
          status = ModelStatus.Completed
        ),
        """{
          |  "content" : [ {
          |    "annotations" : [ ],
          |    "text" : "I've analyzed the image and found the following:",
          |    "type" : "output_text"
          |  }, {
          |    "annotations" : [ {
          |      "start_index" : 0,
          |      "end_index" : 10,
          |      "url" : "https://example.com/citation1",
          |      "title" : "Citation 1",
          |      "type" : "url_citation"
          |    }, {
          |      "start_index" : 12,
          |      "end_index" : 22,
          |      "url" : "https://example.com/reference2",
          |      "title" : "Reference 2",
          |      "type" : "url_citation"
          |    } ],
          |    "text" : "The image shows a classic example of Renaissance architecture.",
          |    "type" : "output_text"
          |  } ],
          |  "id" : "output_abc123",
          |  "status" : "completed"
          |}""".stripMargin,
        Pretty
      )

      testCodec[Message.OutputContent](
        Message.OutputContent(
          content = Seq(
            OutputMessageContent.Refusal(
              refusal = "I cannot analyze this image as it contains inappropriate content."
            )
          ),
          id = "output_def456",
          status = ModelStatus.Incomplete
        ),
        """{
          |  "content" : [ {
          |    "refusal" : "I cannot analyze this image as it contains inappropriate content.",
          |    "type" : "refusal"
          |  } ],
          |  "id" : "output_def456",
          |  "status" : "incomplete"
          |}""".stripMargin,
        Pretty
      )

      testCodec[Message.OutputContent](
        Message.OutputContent(
          id = "output_empty789",
          status = ModelStatus.InProgress
        ),
        """{
          |  "content" : [ ],
          |  "id" : "output_empty789",
          |  "status" : "in_progress"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Input.ofInputTextMessage" in {
      val input = Input.ofInputTextMessage(
        content = "Hello, world!",
        role = ChatRole.User
      )

      testCodec[Input](
        input,
        """{
          |  "content" : "Hello, world!",
          |  "role" : "user",
          |  "type" : "message"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Input.ofInputMessage" in {
      testCodec[Input](
        Input.ofInputMessage(
          content = Seq(
            InputMessageContent.Text("Hello, I have a question about my data."),
            InputMessageContent.File(fileId = Some("file_abc123"))
          ),
          role = ChatRole.User
        ),
        """{
          |  "content" : [ {
          |    "text" : "Hello, I have a question about my data.",
          |    "type" : "input_text"
          |  }, {
          |    "file_id" : "file_abc123",
          |    "type" : "input_file"
          |  } ],
          |  "role" : "user",
          |  "type" : "message"
          |}""".stripMargin,
        Pretty
      )

      testCodec[Input](
        Input.ofInputMessage(
          content = Seq(
            InputMessageContent.Text("Hello, I have a question about my data."),
            InputMessageContent.File(fileId = Some("file_abc123"))
          ),
          role = ChatRole.User,
          status = Some(ModelStatus.Completed)
        ),
        """{
          |  "content" : [ {
          |    "text" : "Hello, I have a question about my data.",
          |    "type" : "input_text"
          |  }, {
          |    "file_id" : "file_abc123",
          |    "type" : "input_file"
          |  } ],
          |  "role" : "user",
          |  "status" : "completed",
          |  "type" : "message"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Input.ofOutputMessage" in {
      val input = Input.ofOutputMessage(
        content = Seq(
          OutputMessageContent.OutputText(text = "Here's the analysis of your data.")
        ),
        id = "output_abc123",
        status = ModelStatus.Completed
      )

      testCodec[Input](
        input,
        """{
          |  "content" : [ {
          |    "annotations" : [ ],
          |    "text" : "Here's the analysis of your data.",
          |    "type" : "output_text"
          |  } ],
          |  "id" : "output_abc123",
          |  "status" : "completed",
          |  "type" : "message"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Input.ofFileSearchToolCall" in {
      val input = Input.ofFileSearchToolCall(
        id = "search_abc123",
        queries = Seq("Find documents about machine learning"),
        status = ModelStatus.Searching,
        results = Seq(
          FileSearchResult(
            attributes = Map("topic" -> "machine learning"),
            fileId = Some("file_abc123"),
            filename = Some("ml_paper.pdf"),
            score = Some(0.95),
            text = Some("This paper discusses advanced machine learning techniques.")
          )
        )
      )

      testCodec[Input](
        input,
        """{
          |  "id" : "search_abc123",
          |  "queries" : [ "Find documents about machine learning" ],
          |  "status" : "searching",
          |  "results" : [ {
          |    "attributes" : {
          |      "topic" : "machine learning"
          |    },
          |    "file_id" : "file_abc123",
          |    "filename" : "ml_paper.pdf",
          |    "score" : 0.95,
          |    "text" : "This paper discusses advanced machine learning techniques."
          |  } ],
          |  "type" : "file_search_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize Input.ofComputerToolCall" in {
      val input = Input.ofComputerToolCall(
        action = ComputerToolAction.Click(
          button = ComputerToolAction.ButtonClick.Left,
          x = 100,
          y = 200
        ),
        callId = "computer_call_abc123",
        id = "computer_abc123",
        pendingSafetyChecks = Seq(
          PendingSafetyCheck(
            id = "safety_check_1",
            code = Some("safety_code_1"),
            message = Some("Confirm this action")
          )
        ),
        status = ModelStatus.InProgress
      )

      testCodec[Input](
        input,
        """{
          |  "action" : {
          |    "button" : "left",
          |    "x" : 100,
          |    "y" : 200,
          |    "type" : "click"
          |  },
          |  "call_id" : "computer_call_abc123",
          |  "id" : "computer_abc123",
          |  "pending_safety_checks" : [ {
          |    "id" : "safety_check_1",
          |    "code" : "safety_code_1",
          |    "message" : "Confirm this action"
          |  } ],
          |  "status" : "in_progress",
          |  "type" : "computer_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Input.ofComputerToolCallOutput" in {
      val input = Input.ofComputerToolCallOutput(
        callId = "computer_call_abc123",
        output = ComputerScreenshot(
          fileId = Some("file_abc123"),
          imageUrl = Some("https://example.com/screenshot.png")
        ),
        acknowledgedSafetyChecks = Seq(
          AcknowledgedSafetyCheck(
            id = "safety_check_1",
            code = Some("safety_code_1"),
            message = Some("Action confirmed")
          )
        ),
        id = Some("output_abc123"),
        status = Some(ModelStatus.Completed)
      )

      testCodec[Input](
        input,
        """{
          |  "call_id" : "computer_call_abc123",
          |  "output" : {
          |    "file_id" : "file_abc123",
          |    "image_url" : "https://example.com/screenshot.png",
          |    "type" : "computer_screenshot"
          |  },
          |  "acknowledged_safety_checks" : [ {
          |    "id" : "safety_check_1",
          |    "code" : "safety_code_1",
          |    "message" : "Action confirmed"
          |  } ],
          |  "id" : "output_abc123",
          |  "status" : "completed",
          |  "type" : "computer_call_output"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Input.ofWebSearchToolCall" in {
      val input = Input.ofWebSearchToolCall(
        action = WebSearchAction.Search(query = "test query"),
        id = "web_search_abc123",
        status = ModelStatus.InProgress
      )

      testCodec[Input](
        input,
        """{
          |  "action" : {
          |    "query" : "test query",
          |    "type" : "search"
          |  },
          |  "id" : "web_search_abc123",
          |  "status" : "in_progress",
          |  "type" : "web_search_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Input.ofFunctionToolCall" in {
      val input = Input.ofFunctionToolCall(
        arguments = """{"location":"San Francisco, CA"}""",
        callId = "function_call_abc123",
        name = "get_weather",
        id = Some("call_id_abc123"),
        status = Some(ModelStatus.Completed)
      )

      testCodec[Input](
        input,
        """{
          |  "arguments" : "{\"location\":\"San Francisco, CA\"}",
          |  "call_id" : "function_call_abc123",
          |  "name" : "get_weather",
          |  "id" : "call_id_abc123",
          |  "status" : "completed",
          |  "type" : "function_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Input.ofFunctionToolCallOutput" in {
      val input = Input.ofFunctionToolCallOutput(
        callId = "function_call_abc123",
        output = FunctionToolOutput.StringOutput("""{"temperature":72,"unit":"F"}"""),
        id = Some("output_abc123"),
        status = Some(ModelStatus.Completed)
      )

      testCodec[Input](
        input,
        """{
          |  "call_id" : "function_call_abc123",
          |  "output" : "{\"temperature\":72,\"unit\":\"F\"}",
          |  "id" : "output_abc123",
          |  "status" : "completed",
          |  "type" : "function_call_output"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Input.ofReasoning" in {
      val input = Input.ofReasoning(
        id = "reasoning_abc123",
        summary = Seq(
          SummaryText("First step: Analyze the data"),
          SummaryText("Second step: Draw conclusions")
        ),
        status = Some(ModelStatus.Completed)
      )

      testCodec[Input](
        input,
        """{
          |  "id" : "reasoning_abc123",
          |  "summary" : [ {
          |    "text" : "First step: Analyze the data",
          |    "type" : "summary_text"
          |  }, {
          |    "text" : "Second step: Draw conclusions",
          |    "type" : "summary_text"
          |  } ],
          |  "content" : [ ],
          |  "status" : "completed",
          |  "type" : "reasoning"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Input.ofCustomToolCall" in {
      val input = Input.ofCustomToolCall(
        callId = "call_custom_123",
        input = """{"query": "search term"}""",
        name = "custom_search",
        id = Some("custom_call_abc123")
      )

      testCodec[Input](
        input,
        """{
          |  "call_id" : "call_custom_123",
          |  "input" : "{\"query\": \"search term\"}",
          |  "name" : "custom_search",
          |  "id" : "custom_call_abc123",
          |  "type" : "custom_tool_call"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Input.ofCustomToolCallOutput" in {
      val input = Input.ofCustomToolCallOutput(
        callId = "call_custom_123",
        output = FunctionToolOutput.StringOutput("""{"result": "success"}"""),
        id = Some("output_custom_abc123")
      )

      testCodec[Input](
        input,
        """{
          |  "call_id" : "call_custom_123",
          |  "output" : "{\"result\": \"success\"}",
          |  "id" : "output_custom_abc123",
          |  "type" : "custom_tool_call_output"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Input.ofItemReference" in {
      val input = Input.ofItemReference(
        id = "item_abc123"
      )

      testCodec[Input](
        input,
        """{
          |  "id" : "item_abc123",
          |  "type" : "item_reference"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Output types" in {

      val outputMessage = Message.OutputContent(
        content = Seq(
          OutputMessageContent.OutputText(text = "This is a response")
        ),
        id = "output_abc123",
        status = ModelStatus.Completed
      )

      testCodec[Output](
        outputMessage,
        """{
          |  "content" : [ {
          |    "annotations" : [ ],
          |    "text" : "This is a response",
          |    "type" : "output_text"
          |  } ],
          |  "id" : "output_abc123",
          |  "status" : "completed",
          |  "type" : "message"
          |}""".stripMargin,
        Pretty
      )

      val fileSearchToolCall = FileSearchToolCall(
        id = "search_abc123",
        queries = Seq("Find documents"),
        status = ModelStatus.Completed,
        results = Seq()
      )

      testCodec[Output](
        fileSearchToolCall,
        """{
          |  "id" : "search_abc123",
          |  "queries" : [ "Find documents" ],
          |  "status" : "completed",
          |  "results" : [ ],
          |  "type" : "file_search_call"
          |}""".stripMargin,
        Pretty
      )

      val functionToolCall = FunctionToolCall(
        arguments = """{"query":"weather"}""",
        callId = "call_abc123",
        name = "search",
        status = Some(ModelStatus.Completed)
      )

      testCodec[Output](
        functionToolCall,
        """{
          |  "arguments" : "{\"query\":\"weather\"}",
          |  "call_id" : "call_abc123",
          |  "name" : "search",
          |  "status" : "completed",
          |  "type" : "function_call"
          |}""".stripMargin,
        Pretty
      )

      val webSearchToolCall = WebSearchToolCall(
        action = WebSearchAction.Search(query = "weather forecast"),
        id = "web_search_abc123",
        status = ModelStatus.Completed
      )

      testCodec[Output](
        webSearchToolCall,
        """{
          |  "action" : {
          |    "query" : "weather forecast",
          |    "type" : "search"
          |  },
          |  "id" : "web_search_abc123",
          |  "status" : "completed",
          |  "type" : "web_search_call"
          |}""".stripMargin,
        Pretty
      )

      testCodec[Output](
        ComputerToolCall(
          action = ComputerToolAction.Click(
            button = ComputerToolAction.ButtonClick.Left,
            x = 100,
            y = 200
          ),
          callId = "computer_call_abc123",
          id = "computer_abc123",
          status = ModelStatus.Completed
        ),
        """{
          |  "action" : {
          |    "button" : "left",
          |    "x" : 100,
          |    "y" : 200,
          |    "type" : "click"
          |  },
          |  "call_id" : "computer_call_abc123",
          |  "id" : "computer_abc123",
          |  "pending_safety_checks" : [ ],
          |  "status" : "completed",
          |  "type" : "computer_call"
          |}""".stripMargin,
        Pretty
      )

      testDeserialization[Output](
        ComputerToolCall(
          action = ComputerToolAction.DoubleClick(
            x = 100,
            y = 200
          ),
          callId = "computer_call_abc123",
          id = "computer_abc123",
          status = ModelStatus.Completed
        ),
        """{
          |  "action" : {
          |    "x" : 100,
          |    "y" : 200,
          |    "type" : "double_click"
          |  },
          |  "call_id" : "computer_call_abc123",
          |  "id" : "computer_abc123",
          |  "status" : "completed",
          |  "type" : "computer_call"
          |}""".stripMargin
      )

      // Reasoning as Output
      val reasoning = Reasoning(
        id = "reasoning_abc123",
        summary = Seq(
          SummaryText("Reasoning step")
        ),
        status = Some(ModelStatus.Completed)
      )

      testCodec[Output](
        reasoning,
        """{
          |  "id" : "reasoning_abc123",
          |  "summary" : [ {
          |    "text" : "Reasoning step",
          |    "type" : "summary_text"
          |  } ],
          |  "content" : [ ],
          |  "status" : "completed",
          |  "type" : "reasoning"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Response" in {
      val date = new ju.Date(1620000000000L) // May 3, 2021

      val response = Response(
        createdAt = date,
        error = None,
        id = "resp_abc123",
        incompleteDetails = None,
        instructions = Some(Inputs.Text("Act as a helpful assistant")),
        maxOutputTokens = Some(1000),
        metadata = Some(Map("key1" -> "value1", "key2" -> "value2")),
        model = "gpt-4o",
        output = Seq(
          Message.OutputContent(
            content = Seq(
              OutputMessageContent.OutputText(
                text = "Hello, how can I help you today?"
              )
            ),
            id = "output_def456",
            status = ModelStatus.Completed
          )
        ),
        parallelToolCalls = true,
        previousResponseId = None,
        reasoning = Some(
          ReasoningConfig(
            effort = Some(ReasoningEffort.medium)
          )
        ),
        status = ModelStatus.Completed,
        temperature = Some(0.7),
        text = TextResponseConfig(
          format = ResponseFormat.Text
        ),
        toolChoice = Some(ToolChoice.Mode.Auto),
        tools = Seq(
          FunctionTool(
            name = "get_weather",
            parameters = JsonSchema.Object(
              properties = Seq(
                "location" -> JsonSchema.String(
                  Some("The city and state, e.g. San Francisco, CA")
                )
              ),
              required = Seq("location")
            ),
            strict = false,
            description = Some("Get the current weather for a location")
          )
        ),
        topP = Some(0.9),
        truncation = Some(TruncationStrategy.Auto),
        usage = Some(
          UsageInfo(
            inputTokens = 50,
            inputTokensDetails = Some(
              InputTokensDetails(
                cachedTokens = Some(50)
              )
            ),
            outputTokens = 150,
            outputTokensDetails = Some(
              OutputTokensDetails(
                reasoningTokens = 75
              )
            ),
            totalTokens = 200
          )
        ),
        user = Some("user123")
      )

      testCodec[Response](
        response,
        """{
          |  "created_at" : 1620000000,
          |  "id" : "resp_abc123",
          |  "instructions" : "Act as a helpful assistant",
          |  "max_output_tokens" : 1000,
          |  "metadata" : {
          |    "key1" : "value1",
          |    "key2" : "value2"
          |  },
          |  "model" : "gpt-4o",
          |  "output" : [ {
          |    "content" : [ {
          |      "annotations" : [ ],
          |      "text" : "Hello, how can I help you today?",
          |      "type" : "output_text"
          |    } ],
          |    "id" : "output_def456",
          |    "status" : "completed",
          |    "type" : "message"
          |  } ],
          |  "parallel_tool_calls" : true,
          |  "reasoning" : {
          |    "effort" : "medium"
          |  },
          |  "status" : "completed",
          |  "temperature" : 0.7,
          |  "text" : {
          |    "format" : {
          |      "type" : "text"
          |    }
          |  },
          |  "tool_choice" : "auto",
          |  "tools" : [ {
          |    "name" : "get_weather",
          |    "parameters" : {
          |      "properties" : {
          |        "location" : {
          |          "description" : "The city and state, e.g. San Francisco, CA",
          |          "type" : "string"
          |        }
          |      },
          |      "required" : [ "location" ],
          |      "type" : "object"
          |    },
          |    "strict" : false,
          |    "description" : "Get the current weather for a location",
          |    "type" : "function"
          |  } ],
          |  "top_p" : 0.9,
          |  "truncation" : "auto",
          |  "usage" : {
          |    "input_tokens" : 50,
          |    "input_tokens_details" : {
          |      "cached_tokens" : 50
          |    },
          |    "output_tokens" : 150,
          |    "output_tokens_details" : {
          |      "reasoning_tokens" : 75
          |    },
          |    "total_tokens" : 200
          |  },
          |  "user" : "user123"
          |}""".stripMargin,
        Pretty
      )

      // Test with minimal fields
      val minimalResponse = Response(
        createdAt = date,
        id = "resp_abc123",
        model = "gpt-4o",
        parallelToolCalls = false,
        status = ModelStatus.InProgress,
        text = TextResponseConfig(
          format = ResponseFormat.Text
        ),
        usage = Some(
          UsageInfo(
            inputTokens = 50,
            outputTokens = 0,
            totalTokens = 50
          )
        )
      )

      testCodec[Response](
        minimalResponse,
        """{
          |  "created_at" : 1620000000,
          |  "id" : "resp_abc123",
          |  "model" : "gpt-4o",
          |  "output" : [ ],
          |  "parallel_tool_calls" : false,
          |  "status" : "in_progress",
          |  "text" : {
          |    "format" : {
          |      "type" : "text"
          |    }
          |  },
          |  "tools" : [ ],
          |  "usage" : {
          |    "input_tokens" : 50,
          |    "output_tokens" : 0,
          |    "total_tokens" : 50
          |  }
          |}""".stripMargin,
        Pretty
      )

      // official example 1
      val officialExample1 = Response(
        id = "resp_67ccd2bed1ec8190b14f964abc0542670bb6a6b452d3795b",
        createdAt = date,
        status = ModelStatus.Completed,
        model = "gpt-4o-2024-08-06",
        output = Seq(
          Message.OutputContent(
            id = "msg_67ccd2bf17f0819081ff3bb2cf6508e60bb6a6b452d3795b",
            status = ModelStatus.Completed,
            content = Seq(
              OutputMessageContent.OutputText(
                text =
                  "In a peaceful grove beneath a silver moon, a unicorn named Lumina discovered a hidden pool that reflected the stars. As she dipped her horn into the water, the pool began to shimmer, revealing a pathway to a magical realm of endless night skies. Filled with wonder, Lumina whispered a wish for all who dream to find their own hidden magic, and as she glanced back, her hoofprints sparkled like stardust."
              )
            )
          )
        ),
        parallelToolCalls = true,
        previousResponseId = None,
        reasoning = Some(
          ReasoningConfig(
            effort = None,
            generateSummary = None
          )
        ),
        temperature = Some(1.0),
        text = TextResponseConfig(
          format = ResponseFormat.Text
        ),
        toolChoice = Some(ToolChoice.Mode.Auto),
        topP = Some(1.0),
        truncation = Some(TruncationStrategy.Disabled),
        usage = Some(
          UsageInfo(
            inputTokens = 36,
            inputTokensDetails = Some(InputTokensDetails(cachedTokens = Some(0))),
            outputTokens = 87,
            outputTokensDetails = Some(OutputTokensDetails(reasoningTokens = 0)),
            totalTokens = 123
          )
        ),
        user = None,
        metadata = Some(Map())
      )

      testCodec[Response](
        officialExample1,
        """{
          |  "id": "resp_67ccd2bed1ec8190b14f964abc0542670bb6a6b452d3795b",
          |  "object": "response",
          |  "created_at": 1620000000,
          |  "status": "completed",
          |  "error": null,
          |  "incomplete_details": null,
          |  "instructions": null,
          |  "max_output_tokens": null,
          |  "model": "gpt-4o-2024-08-06",
          |  "output": [
          |    {
          |      "type": "message",
          |      "id": "msg_67ccd2bf17f0819081ff3bb2cf6508e60bb6a6b452d3795b",
          |      "status": "completed",
          |      "role": "assistant",
          |      "content": [
          |        {
          |          "type": "output_text",
          |          "text": "In a peaceful grove beneath a silver moon, a unicorn named Lumina discovered a hidden pool that reflected the stars. As she dipped her horn into the water, the pool began to shimmer, revealing a pathway to a magical realm of endless night skies. Filled with wonder, Lumina whispered a wish for all who dream to find their own hidden magic, and as she glanced back, her hoofprints sparkled like stardust.",
          |          "annotations": []
          |        }
          |      ]
          |    }
          |  ],
          |  "parallel_tool_calls": true,
          |  "previous_response_id": null,
          |  "reasoning": {
          |    "effort": null,
          |    "summary": null
          |  },
          |  "temperature": 1.0,
          |  "text": {
          |    "format": {
          |      "type": "text"
          |    }
          |  },
          |  "tool_choice": "auto",
          |  "tools": [],
          |  "top_p": 1.0,
          |  "truncation": "disabled",
          |  "usage": {
          |    "input_tokens": 36,
          |    "input_tokens_details": {
          |      "cached_tokens": 0
          |    },
          |    "output_tokens": 87,
          |    "output_tokens_details": {
          |      "reasoning_tokens": 0
          |    },
          |    "total_tokens": 123
          |  },
          |  "user": null,
          |  "metadata": {}
          |}""".stripMargin,
        Pretty,
        justSemantics = true
      )
    }

    "serialize and deserialize CreateModelResponse" in {
      val createModelResponse = CreateModelResponseSettings(
        model = "gpt-4o",
        include = Seq(
          "file_search_call.results",
          "message.input_image.image_url"
        ),
        instructions = Some("You are a helpful assistant"),
        maxOutputTokens = Some(2000),
        metadata = Some(Map("purpose" -> "testing", "environment" -> "dev")),
        parallelToolCalls = Some(true),
        previousResponseId = Some("resp_previous123"),
        reasoning = Some(
          ReasoningConfig(
            effort = Some(ReasoningEffort.high),
            generateSummary = Some("detailed")
          )
        ),
        store = Some(true),
        stream = Some(false),
        temperature = Some(0.8),
        text = Some(
          TextResponseConfig(
            format = ResponseFormat.JsonObject
          )
        ),
        toolChoice = Some(ToolChoice.Mode.Required),
        tools = Seq(
          FunctionTool(
            name = "search_database",
            parameters = JsonSchema.Object(
              properties = Seq(
                "query" -> JsonSchema.String(Some("The search query")),
                "limit" -> JsonSchema.Integer(Some("Max results to return"))
              ),
              required = Seq("query")
            ),
            strict = false,
            description = Some("Search the database for information")
          ),
          FileSearchTool(
            vectorStoreIds = Seq("store1", "store2")
          )
        ),
        topP = Some(0.95),
        truncation = Some(TruncationStrategy.Auto),
        user = Some("user456")
      )

      testCodec[CreateModelResponseSettings](
        createModelResponse,
        """{
          |  "model" : "gpt-4o",
          |  "include" : [ "file_search_call.results", "message.input_image.image_url" ],
          |  "instructions" : "You are a helpful assistant",
          |  "max_output_tokens" : 2000,
          |  "metadata" : {
          |    "purpose" : "testing",
          |    "environment" : "dev"
          |  },
          |  "parallel_tool_calls" : true,
          |  "previous_response_id" : "resp_previous123",
          |  "reasoning" : {
          |    "effort" : "high",
          |    "generate_summary" : "detailed"
          |  },
          |  "store" : true,
          |  "stream" : false,
          |  "temperature" : 0.8,
          |  "text" : {
          |    "format" : {
          |      "type" : "json_object"
          |    }
          |  },
          |  "tool_choice" : "required",
          |  "tools" : [ {
          |    "name" : "search_database",
          |    "parameters" : {
          |      "properties" : {
          |        "query" : {
          |          "description" : "The search query",
          |          "type" : "string"
          |        },
          |        "limit" : {
          |          "description" : "Max results to return",
          |          "type" : "integer"
          |        }
          |      },
          |      "required" : [ "query" ],
          |      "type" : "object"
          |    },
          |    "strict" : false,
          |    "description" : "Search the database for information",
          |    "type" : "function"
          |  }, {
          |    "vector_store_ids" : [ "store1", "store2" ],
          |    "type" : "file_search"
          |  } ],
          |  "top_p" : 0.95,
          |  "truncation" : "auto",
          |  "user" : "user456"
          |}""".stripMargin,
        Pretty
      )

      // Test with minimal fields
      val minimalCreateModelResponse = CreateModelResponseSettings(
        model = "gpt-4o"
      )

      testCodec[CreateModelResponseSettings](
        minimalCreateModelResponse,
        """{
          |  "model" : "gpt-4o"
          |}""".stripMargin,
        Pretty
      )

      // official openai example1
      testCodec[CreateModelResponseSettings](
        CreateModelResponseSettings(
          model = "gpt-4o",
          tools = Seq(
            FileSearchTool(
              vectorStoreIds = Seq("vs_1234567890"),
              maxNumResults = Some(20)
            )
          )
        ),
        """{
          |  "model" : "gpt-4o",
          |  "tools" : [ {
          |    "vector_store_ids" : [ "vs_1234567890" ],
          |    "max_num_results" : 20,
          |    "type" : "file_search"
          |  } ]
          |}""".stripMargin,
        Pretty
      )

      // official openai example2
      testCodec[CreateModelResponseSettings](
        CreateModelResponseSettings(
          model = "gpt-4o",
          tools = Seq(
            FunctionTool(
              name = "get_current_weather",
              parameters = JsonSchema.Object(
                properties = Map(
                  "location" -> JsonSchema.String(
                    description = Some("The city and state, e.g. San Francisco, CA")
                  ),
                  "unit" -> JsonSchema.String(
                    `enum` = Seq("celsius", "fahrenheit")
                  )
                ),
                required = Seq("location", "unit")
              ),
              description = Some("Get the current weather in a given location"),
              strict = false
            )
          ),
          toolChoice = Some(ToolChoice.Mode.Auto)
        ),
        """{
          |  "model" : "gpt-4o",
          |  "tool_choice" : "auto",
          |  "tools" : [ {
          |    "name" : "get_current_weather",
          |    "parameters" : {
          |      "properties" : {
          |        "location" : {
          |          "description" : "The city and state, e.g. San Francisco, CA",
          |          "type" : "string"
          |        },
          |        "unit" : {
          |          "enum" : [ "celsius", "fahrenheit" ],
          |          "type" : "string"
          |        }
          |      },
          |      "required" : [ "location", "unit" ],
          |      "type" : "object"
          |    },
          |    "strict" : false,
          |    "description" : "Get the current weather in a given location",
          |    "type" : "function"
          |  } ]
          |}""".stripMargin,
        Pretty
      )

      // official openai example 3
      testCodec(
        CreateModelResponseSettings(
          model = "o3-mini",
          reasoning = Some(ReasoningConfig(effort = Some(ReasoningEffort.high)))
        ),
        """{
          |  "model" : "o3-mini",
          |  "reasoning" : {
          |    "effort" : "high"
          |  }
          |}""".stripMargin,
        Pretty
      )

      // one more test for new fields
      testCodec[CreateModelResponseSettings](
        CreateModelResponseSettings(
          model = "gpt-4o",
          prompt = Some(
            Prompt(
              id = "prompt_123",
              variables = Map("customer_name" -> "John Doe"),
              version = Some("v1.0")
            )
          ),
          promptCacheKey = Some("cache_key_123"),
          background = Some(true),
          maxToolCalls = Some(5),
          safetyIdentifier = Some("safety_level_high"),
          serviceTier = Some("premium"),
          streamOptions = Some(StreamOptions(includeObfuscation = Some(true))),
          topLogprobs = Some(10),
          tools = Seq(
            FunctionTool(
              name = "execute_code",
              parameters = JsonSchema.Object(
                properties = Seq(
                  "code" -> JsonSchema.String(Some("The code to execute")),
                  "language" -> JsonSchema.String(Some("Programming language"))
                ),
                required = Seq("code")
              ),
              strict = true,
              description = Some("Execute code in the specified language")
            )
          ),
          temperature = Some(0.3),
          topP = Some(0.9)
        ),
        """{
          |  "model" : "gpt-4o",
          |  "prompt" : {
          |    "id" : "prompt_123",
          |    "variables" : {
          |      "customer_name" : "John Doe"
          |    },
          |    "version" : "v1.0"
          |  },
          |  "prompt_cache_key" : "cache_key_123",
          |  "background" : true,
          |  "max_tool_calls" : 5,
          |  "safety_identifier" : "safety_level_high",
          |  "service_tier" : "premium",
          |  "stream_options" : {
          |    "include_obfuscation" : true
          |  },
          |  "top_logprobs" : 10,
          |  "tools" : [ {
          |    "name" : "execute_code",
          |    "parameters" : {
          |      "properties" : {
          |        "code" : {
          |          "description" : "The code to execute",
          |          "type" : "string"
          |        },
          |        "language" : {
          |          "description" : "Programming language",
          |          "type" : "string"
          |        }
          |      },
          |      "required" : [ "code" ],
          |      "type" : "object"
          |    },
          |    "strict" : true,
          |    "description" : "Execute code in the specified language",
          |    "type" : "function"
          |  } ],
          |  "temperature" : 0.3,
          |  "top_p" : 0.9
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Prompt with all fields" in {
      testCodec[Prompt](
        Prompt(
          id = "prompt_abc123",
          variables = Map("customer_name" -> "Alice", "order_id" -> "12345"),
          version = Some("v2.0")
        ),
        """{
          |  "id" : "prompt_abc123",
          |  "variables" : {
          |    "customer_name" : "Alice",
          |    "order_id" : "12345"
          |  },
          |  "version" : "v2.0"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Prompt with minimal fields" in {
      testCodec[Prompt](
        Prompt(id = "prompt_xyz789"),
        """{
          |  "id" : "prompt_xyz789",
          |  "variables" : {
          |  }
          |}""".stripMargin,
        Pretty
      )
    }

    "deserialize Prompt without variables field (uses default)" in {
      val json = """{
        |  "id" : "prompt_def456",
        |  "version" : "v1.0"
        |}""".stripMargin

      val prompt = Json.parse(json).as[Prompt]
      assert(prompt.id == "prompt_def456")
      assert(prompt.variables == Map.empty)
      assert(prompt.version == Some("v1.0"))
    }

    "deserialize Prompt with only id (uses default for variables)" in {
      val json = """{"id":"prompt_minimal"}"""

      val prompt = Json.parse(json).as[Prompt]
      assert(prompt.id == "prompt_minimal")
      assert(prompt.variables == Map.empty)
      assert(prompt.version == None)
    }
  }

  private def testCodec[A](
    value: A,
    json: String,
    printMode: JsonPrintMode = Compact,
    justSemantics: Boolean = false
  )(
    implicit format: Format[A]
  ): Unit = {
    val jsValue = Json.toJson(value)
    val serialized = printMode match {
      case Compact => jsValue.toString()
      case Pretty  => Json.prettyPrint(jsValue)
    }

    // special handling for Scala 3, which doesn't preserve the order of fields in json
    if (!justSemantics && !isScala3) {
      serialized shouldBe json
    } else if (!justSemantics) {
      val parsed = Json.parse(serialized).as[A]
      parsed shouldBe value
    }

    val json2 = Json.parse(json).as[A]
    json2 shouldBe value
  }

  private def prettyTestCodec[A](
    value: A,
    json: String,
    justSemantics: Boolean = false
  )(
    implicit format: Format[A]
  ): Unit =
    testCodec(value, json, Pretty, justSemantics)

  private def testSerialization[A](
    value: A,
    jsonString: String,
    printMode: JsonPrintMode = Compact
  )(
    implicit format: Writes[A]
  ): Unit = {
    val jsValue = Json.toJson(value)

    // special handling for Scala 3, which doesn't preserve the order of fields in json
    if (!isScala3) {
      val serialized = printMode match {
        case Compact => jsValue.toString()
        case Pretty  => Json.prettyPrint(jsValue)
      }
      serialized shouldBe jsonString
    } else {
      jsValue shouldBe Json.parse(jsonString)
    }
  }

  private def testDeserialization[A](
    value: A,
    json: String
  )(
    implicit format: Reads[A]
  ): Unit = {
    Json.parse(json).as[A] shouldBe value
  }
}
