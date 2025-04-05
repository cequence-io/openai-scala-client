package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.domain.responsesapi.tools.JsonFormats._
import io.cequence.openaiscala.domain.responsesapi.JsonFormats._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import io.cequence.openaiscala.domain.responsesapi._
import io.cequence.openaiscala.domain.responsesapi.tools._
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

    "serialize and deserialize ToolChoice.HostedTool" in {
      testCodec[ToolChoice](
        ToolChoice.HostedTool.FileSearch,
        """{
          |  "type" : "file_search"
          |}""".stripMargin,
        Pretty
      )

      testCodec[ToolChoice](
        ToolChoice.HostedTool.WebSearchPreview,
        """{
          |  "type" : "web_search_preview"
          |}""".stripMargin,
        Pretty
      )

      testCodec[ToolChoice](
        ToolChoice.HostedTool.ComputerUsePreview,
        """{
          |  "type" : "computer_use_preview"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ToolChoice.FunctionTool" in {
      testCodec[ToolChoice](
        ToolChoice.FunctionTool("get_weather"),
        """{
          |  "name" : "get_weather",
          |  "type" : "function"
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
          |    "type" : "object"
          |  },
          |  "strict" : true,
          |  "description" : "Get the current weather for a location",
          |  "type" : "function"
          |}""".stripMargin,
        Pretty
      )
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
          |  "type" : "web_search_preview"
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
          status = ModelStatus.Searching,
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
          |  "status" : "searching",
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
    }

    "serialize and deserialize WebSearchToolCall" in {
      testCodec[ToolCall](
        WebSearchToolCall(
          id = "call_abc123",
          status = ModelStatus.Completed
        ),
        """{
          |  "id" : "call_abc123",
          |  "status" : "completed",
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

    "serialize and deserialize FunctionToolCallOutput" in {
      testCodec[FunctionToolCallOutput](
        FunctionToolCallOutput(
          callId = "call_abc123",
          output = """{"temperature":72,"unit":"F"}""",
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
              code = "safety_code_1",
              id = "safety_check_1",
              message = "Safety check 1"
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
          |    "code" : "safety_code_1",
          |    "id" : "safety_check_1",
          |    "message" : "Safety check 1"
          |  } ],
          |  "id" : "call_abc123",
          |  "status" : "completed"
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
        ReasoningEffort.Low,
        "\"low\"",
        Pretty
      )

      testCodec[ReasoningEffort](
        ReasoningEffort.Medium,
        "\"medium\"",
        Pretty
      )

      testCodec[ReasoningEffort](
        ReasoningEffort.High,
        "\"high\"",
        Pretty
      )
    }

    "serialize and deserialize ReasoningConfig" in {
      testCodec[ReasoningConfig](
        ReasoningConfig(
          effort = Some(ReasoningEffort.Medium),
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

    "serialize and deserialize ReasoningText" in {
      testCodec[ReasoningText](
        ReasoningText(
          text = "This is a reasoning text"
        ),
        """{
          |  "text" : "This is a reasoning text"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize Reasoning" in {
      testCodec[Reasoning](
        Reasoning(
          id = "reasoning_abc123",
          summary = Seq(
            ReasoningText("First reasoning step"),
            ReasoningText("Second reasoning step")
          ),
          status = Some(ModelStatus.Completed)
        ),
        """{
          |  "id" : "reasoning_abc123",
          |  "summary" : [ {
          |    "text" : "First reasoning step"
          |  }, {
          |    "text" : "Second reasoning step"
          |  } ],
          |  "status" : "completed"
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
            code = "safety_code_1",
            id = "safety_check_1",
            message = "Confirm this action"
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
          |    "code" : "safety_code_1",
          |    "id" : "safety_check_1",
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
            code = "safety_code_1",
            id = "safety_check_1",
            message = "Action confirmed"
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
          |    "code" : "safety_code_1",
          |    "id" : "safety_check_1",
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
        id = "web_search_abc123",
        status = ModelStatus.InProgress
      )

      testCodec[Input](
        input,
        """{
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
        output = """{"temperature":72,"unit":"F"}""",
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
          ReasoningText("First step: Analyze the data"),
          ReasoningText("Second step: Draw conclusions")
        ),
        status = Some(ModelStatus.Completed)
      )

      testCodec[Input](
        input,
        """{
          |  "id" : "reasoning_abc123",
          |  "summary" : [ {
          |    "text" : "First step: Analyze the data"
          |  }, {
          |    "text" : "Second step: Draw conclusions"
          |  } ],
          |  "status" : "completed",
          |  "type" : "reasoning"
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
        id = "web_search_abc123",
        status = ModelStatus.Completed
      )

      testCodec[Output](
        webSearchToolCall,
        """{
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
          ReasoningText("Reasoning step")
        ),
        status = Some(ModelStatus.Completed)
      )

      testCodec[Output](
        reasoning,
        """{
          |  "id" : "reasoning_abc123",
          |  "summary" : [ {
          |    "text" : "Reasoning step"
          |  } ],
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
        instructions = Some("Act as a helpful assistant"),
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
            effort = Some(ReasoningEffort.Medium)
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
        usage = UsageInfo(
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
        usage = UsageInfo(
          inputTokens = 50,
          outputTokens = 0,
          totalTokens = 50
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
        usage = UsageInfo(
          inputTokens = 36,
          inputTokensDetails = Some(InputTokensDetails(cachedTokens = Some(0))),
          outputTokens = 87,
          outputTokensDetails = Some(OutputTokensDetails(reasoningTokens = 0)),
          totalTokens = 123
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
            effort = Some(ReasoningEffort.High),
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
          reasoning = Some(ReasoningConfig(effort = Some(ReasoningEffort.High)))
        ),
        """{
          |  "model" : "o3-mini",
          |  "reasoning" : {
          |    "effort" : "high"
          |  }
          |}""".stripMargin,
        Pretty
      )
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
