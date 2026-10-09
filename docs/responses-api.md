# Responses API 🧰

[← back to the README](../README.md)

OpenAI's Responses API through the client: textual inputs and messages, image input, the hosted tools (file search,
web search, function calls, MCP) and server-hosted multi-agent execution.

- **Responses API** - basic usage with textual inputs / messages

```scala
  import io.cequence.openaiscala.domain.responsesapi.Inputs

  service
    .createModelResponse(
      Inputs.Text("What is the capital of France?")
    )
    .map { response =>
      println(response.outputText.getOrElse("N/A"))
    }
```

```scala
  import io.cequence.openaiscala.domain.responsesapi.Input

  service
    .createModelResponse(
      Inputs.Items(
        Input.ofInputSystemTextMessage(
          "You are a helpful assistant. Be verbose and detailed and don't be afraid to use emojis."
        ),
        Input.ofInputUserTextMessage("What is the capital of France?")
      )
    )
    .map { response =>
      println(response.outputText.getOrElse("N/A"))
    }
```

- **Responses API** - image input

```scala

  import io.cequence.openaiscala.domain.responsesapi.{Inputs, Input}
  import io.cequence.openaiscala.domain.responsesapi.InputMessageContent
  import io.cequence.openaiscala.domain.ChatRole

  service
    .createModelResponse(
      Inputs.Items(
        Input.ofInputMessage(
          Seq(
            InputMessageContent.Text("what is in this image?"),
            InputMessageContent.Image(
              imageUrl = Some(
                "https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Gfp-wisconsin-madison-the-nature-boardwalk.jpg/2560px-Gfp-wisconsin-madison-the-nature-boardwalk.jpg"
              )
            )
          ),
          role = ChatRole.User
        )
      )
    )
    .map { response =>
      println(response.outputText.getOrElse("N/A"))
    }
```

- **Responses API** - tool use (file search)

```scala

  service
    .createModelResponse(
      Inputs.Text("What are the attributes of an ancient brown dragon?"),
      settings = CreateModelResponseSettings(
        model = ModelId.gpt_5_4_mini,
        tools = Seq(
          FileSearchTool(
            vectorStoreIds = Seq("vs_1234567890"),
            maxNumResults = Some(20),
            filters = None,
            rankingOptions = None
          )
        )
      )
    )
    .map { response =>
      println(response.outputText.getOrElse("N/A"))

      // citations
      val citations: Seq[Annotation.FileCitation] = response.outputMessageContents.collect {
        case e: OutputText =>
          e.annotations.collect { case citation: Annotation.FileCitation => citation }
      }.flatten

      println("Citations:")
      citations.foreach { citation =>
        println(s"${citation.fileId} - ${citation.filename}")
      }
    }
```

- **Responses API** - tool use (web search)

```scala
  service
    .createModelResponse(
      Inputs.Text("What was a positive news story from today?"),
      settings = CreateModelResponseSettings(
        model = ModelId.gpt_5_4_mini,
        tools = Seq(WebSearchTool())
      )
    )
    .map { response =>
      println(response.outputText.getOrElse("N/A"))

      // citations
      val citations: Seq[Annotation.UrlCitation] = response.outputMessageContents.collect {
        case e: OutputText =>
          e.annotations.collect { case citation: Annotation.UrlCitation => citation }
      }.flatten

      println("Citations:")
      citations.foreach { citation =>
        println(s"${citation.title} - ${citation.url}")
      }
    }
```

- **Responses API** - tool use (function call)

```scala
  service
    .createModelResponse(
      Inputs.Text("What is the weather like in Boston today?"),
      settings = CreateModelResponseSettings(
        model = ModelId.gpt_5_4_mini,
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
            strict = true
          )
        ),
        toolChoice = Some(ToolChoice.Mode.Auto)
      )
    )
    .map { response =>
      val functionCall = response.outputFunctionCalls.headOption
        .getOrElse(throw new RuntimeException("No function call output found"))

      println(
        s"""Function Call Details:
           |Name: ${functionCall.name}
           |Arguments: ${functionCall.arguments}
           |Call ID: ${functionCall.callId}
           |ID: ${functionCall.id}
           |Status: ${functionCall.status}""".stripMargin
      )

      val toolsUsed = response.tools.map(_.typeString)

      println(s"${toolsUsed.size} tools used: ${toolsUsed.mkString(", ")}")
    }
```


- **Responses API** - tool use (MCP)

```scala
  import io.cequence.openaiscala.domain.responsesapi.tools.Tool
  import io.cequence.openaiscala.domain.responsesapi.tools.mcp.MCPRequireApproval

  service
    .createModelResponse(
      Inputs.Text("Search for information about Scala programming language."),
      settings = CreateModelResponseSettings(
        model = ModelId.gpt_5_4_mini,
        tools = Seq(
          Tool.mcp(
            serverLabel = "deepwiki",
            serverUrl = Some("https://mcp.deepwiki.com/sse"),
            requireApproval = Some(MCPRequireApproval.Setting.Never)
          )
        )
      )
    )
    .map { response =>
      println(response.outputText.getOrElse("N/A"))
    }
```

- **Responses API** - server-hosted multi-agent execution (🔥 New, beta, GPT-6.1 Sol): the model spawns, messages and
  waits for subagents on the server. The client sends the required `OpenAI-Beta: responses_multi_agent=v1` header;
  `outputText` is the root agent's answer, the delegation is in `MultiAgentCall` / `MultiAgentCallOutput` / `AgentMessage`
  items and the subagents' own messages in `subagentMessages`. From the chat interface use
  `settings.setResponsesMultiAgent()` (the typed stream shows the delegation as server-side `multi_agent.*` tool calls).
  Reasoning summaries cannot be combined with it.

```scala
  service
    .createModelResponse(
      Inputs.Text("Use two subagents: one lists three fruits, the other three vegetables. Then combine."),
      settings = CreateModelResponseSettings(
        model = ModelId.gpt_6_1_sol,
        multiAgent = Some(MultiAgentConfig(maxConcurrentSubagents = Some(2)))
      )
    )
    .map { response =>
      response.output.collect { case call: MultiAgentCall => println(call.action) } // spawn_agent, wait_agent, ...
      println(response.outputText.getOrElse("N/A"))
    }
```
