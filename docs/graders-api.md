# Graders API 📝

[← back to the README](../README.md)

Evaluating model outputs with OpenAI's graders.

- **Graders API** - evaluate model outputs

```scala
  import io.cequence.openaiscala.domain.graders._

  val grader = ScoreModelGrader(
    input = Seq(
      GraderModelInput(
        content = GraderInputContent.TextString(
          "Rate the helpfulness of the following response on a scale from 0 to 1:"
        ),
        role = ChatRole.System
      ),
      GraderModelInput(
        content = GraderInputContent.InputText("{{item.question}}"),
        role = ChatRole.User
      ),
      GraderModelInput(
        content = GraderInputContent.OutputText("{{sample.output_json}}"),
        role = ChatRole.Assistant
      )
    ),
    model = ModelId.gpt_5_4_mini,
    name = "helpfulness_scorer",
    range = Seq(0.0, 1.0)
  )

  service
    .runGrader(
      grader = grader,
      modelSample = """{"answer": "The capital of France is Paris."}""",
      item = Map("question" -> "What is the capital of France?")
    )
    .map { result =>
      println(s"Grader evaluation result: $result")
    }
```

- Count expected used tokens before calling `createChatCompletions` or `createChatFunCompletions`, this helps you select proper model and reduce costs. This is an experimental feature and it may not work for all models. Requires `openai-scala-count-tokens` lib.

An example how to count message tokens:
```scala
import io.cequence.openaiscala.service.OpenAICountTokensHelper
import io.cequence.openaiscala.domain.{AssistantMessage, BaseMessage, FunctionSpec, ModelId, SystemMessage, UserMessage}

class MyCompletionService extends OpenAICountTokensHelper {
  def exec = {
    val model = ModelId.gpt_5_6_luna

    // messages to be sent to OpenAI
    val messages: Seq[BaseMessage] = Seq(
      SystemMessage("You are a helpful assistant."),
      UserMessage("Who won the world series in 2020?"),
      AssistantMessage("The Los Angeles Dodgers won the World Series in 2020."),
      UserMessage("Where was it played?"),
    )

    val tokenCount = countMessageTokens(model, messages)
  }
}
```

An example how to count message tokens when a function is involved:
```scala
import io.cequence.openaiscala.service.OpenAICountTokensHelper
import io.cequence.openaiscala.domain.{BaseMessage, FunctionSpec, ModelId, SystemMessage, UserMessage}

class MyCompletionService extends OpenAICountTokensHelper {
  def exec = {
    val model = ModelId.gpt_5_6_luna
    
    // messages to be sent to OpenAI
    val messages: Seq[BaseMessage] = 
     Seq(
       SystemMessage("You are a helpful assistant."),
       UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?")
     )
     
    // function to be called
    val function: FunctionSpec = FunctionSpec(
      name = "getWeather",
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "location" -> Map(
            "type" -> "string",
            "description" -> "The city to get the weather for"
          ),
          "unit" -> Map("type" -> "string", "enum" -> List("celsius", "fahrenheit"))
        )
      )
    )

    val tokenCount = countFunMessageTokens(model, messages, Seq(function), Some(function.name))
  }
}
```
