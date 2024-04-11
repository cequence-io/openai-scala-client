package io.cequence.openaiscala.service

import akka.testkit.TestKit
import io.cequence.openaiscala.domain.{AssistantMessage, BaseMessage, ChatRole, FunctionSpec, MessageSpec, SystemMessage, UserMessage}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Ignore}

import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class OpenAICountTokensServiceSpec
  extends TestKit(ActorSystem("OpenAICountTokensServiceSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with MockitoSugar
    with ScalaFutures {

  protected implicit val patience: PatienceConfig = PatienceConfig(timeout = 10.seconds)

  trait TestCase extends OpenAICountTokensHelper {
    protected lazy val config: Config = ConfigFactory.load()
    private val verbose = false
    private lazy val validateWithChatGPT = config.getBoolean("validateWithChatGPT")
    protected lazy val openAIServiceExternal: OpenAIService = OpenAIServiceFactory(config)

    private def print(
                       expectedTokens: Int,
                       countedTokens: Int,
                       tokensFromOpenAI: Option[Int]
                     ): Unit = {
      if (verbose) {
        if (tokensFromOpenAI.isDefined) {
          println(
            "Test tokens counted: " + countedTokens + " tokens from OpenAI: " + tokensFromOpenAI + " expected: " + expectedTokens
          )
        } else {
          println(
            "Test tokens counted: " + countedTokens + " expected: " + expectedTokens
          )
        }
      } else ()
    }

    protected def checkTokensForMessageCall(messages: Seq[BaseMessage], expectedModelTokenCounts: (String, Int)*): Unit = {
        expectedModelTokenCounts.foreach {
          case (model, expectedTokens) =>
            checkTokensForMessageCall(messages, expectedTokens, model)
        }
    }

    protected def checkTokensForMessageCall(
                                             messages: Seq[BaseMessage],
                                             expectedTokens: Int,
                                             model: String
                                           ): Unit = {
      val countedTokens = countMessageTokens(model, messages)

      if (validateWithChatGPT) {
        val tokensFromOpenAI = openAIServiceExternal
          .createChatCompletion(
            messages,
            CreateChatCompletionSettings(
              model = model,
              temperature = Some(0.0),
              max_tokens = Some(1)
            )
          )
          .futureValue
          .usage
          .get
          .prompt_tokens

        print(expectedTokens, countedTokens, Some(tokensFromOpenAI))
        tokensFromOpenAI shouldEqual expectedTokens
      } else {
        print(expectedTokens, countedTokens, None)
        countedTokens shouldEqual expectedTokens
      }
      ()
    }

    protected def checkTokensForFunctionCall(
      functions: Seq[FunctionSpec],
      messages: Seq[BaseMessage],
      expectedTokens: Int,
      responseFunctionName: Option[String] = None,
      model: String = "gpt-3.5-turbo"
    ): Unit = {
      val countedTokens = countFunMessageTokens(
        model,
        messages = messages,
        functions = functions,
        responseFunctionName = responseFunctionName
      )

      if (validateWithChatGPT) {
        val tokensFromOpenAI = openAIServiceExternal
          .createChatFunCompletion(
            messages = messages,
            functions = functions,
            settings = CreateChatCompletionSettings(
              model = model,
              temperature = Some(0.0),
              max_tokens = Some(1)
            ),
            responseFunctionName = responseFunctionName
          )
          .futureValue
          .usage
          .get
          .prompt_tokens
        print(expectedTokens, countedTokens, Some(tokensFromOpenAI))
        tokensFromOpenAI shouldEqual expectedTokens
      } else {
        print(expectedTokens, countedTokens, None)
      }
      countedTokens shouldEqual expectedTokens
      ()
    }
  }

  "message call token count calculation" should {
    val systemMessage = SystemMessage("You are a helpful consultant assisting with the translation of corporate jargon into plain English.")

    "count tokens for a system message" in new TestCase {
      checkTokensForMessageCall(chat(systemMessage),
        "gpt-4-0613" -> 24, "gpt-4" -> 24, "gpt-3.5-turbo" -> 24, "gpt-3.5-turbo-0301" -> 25, "gpt-3.5-turbo-0613" -> 24)
    }

    "count tokens for a named system message" in new TestCase {
      checkTokensForMessageCall(chat(systemMessage.withName("James")),
        "gpt-4-0613" -> 26, "gpt-4" -> 26, "gpt-3.5-turbo" -> 26, "gpt-3.5-turbo-0301" -> 25, "gpt-3.5-turbo-0613" -> 26)
    }

    val userMessage = UserMessage("Let's circle back and synergize our core competencies to leverage our bandwidth for a paradigm shift in our market-driven deliverables.")

    "count tokens for a user message" in new TestCase {
      checkTokensForMessageCall(chat(userMessage),
        "gpt-4-0613" -> 33, "gpt-4" -> 33, "gpt-3.5-turbo" -> 33, "gpt-3.5-turbo-0301" -> 34, "gpt-3.5-turbo-0613" -> 33)
    }

    "count tokens for a user message with name" in new TestCase {
      checkTokensForMessageCall(chat(userMessage.withName("Alice")),
        "gpt-4-0613" -> 35, "gpt-4" -> 35, "gpt-3.5-turbo" -> 35, "gpt-3.5-turbo-0301" -> 34, "gpt-3.5-turbo-0613" -> 35)
    }

    val assistantMessage = AssistantMessage("Let's go back and use what we're good at to make the most of what we have. This way, we can really change how we make things that people want to buy.")

    "count tokens for an assistant message" in new TestCase {
      checkTokensForMessageCall(chat(assistantMessage),
        "gpt-4-0613" -> 44, "gpt-4" -> 44, "gpt-3.5-turbo" -> 44, "gpt-3.5-turbo-0301" -> 45, "gpt-3.5-turbo-0613" -> 44)
    }

    "count tokens for an assistant message with name" in new TestCase {
      checkTokensForMessageCall(chat(assistantMessage.withName("Bob")),
        "gpt-4-0613" -> 46, "gpt-4" -> 46, "gpt-3.5-turbo" -> 46, "gpt-3.5-turbo-0301" -> 45, "gpt-3.5-turbo-0613" -> 46)
    }

    "count tokens of a chat with two messages" in new TestCase {
      checkTokensForMessageCall(chat(systemMessage, userMessage),
          "gpt-4-0613" -> 54, "gpt-4" -> 54, "gpt-3.5-turbo" -> 54, "gpt-3.5-turbo-0301" -> 56, "gpt-3.5-turbo-0613" -> 54)
    }

    "count tokens of a chat with two messages with names" in new TestCase {
      checkTokensForMessageCall(chat(systemMessage.withName("James"), userMessage.withName("Alice")),
          "gpt-4-0613" -> 58, "gpt-4" -> 58, "gpt-3.5-turbo" -> 58, "gpt-3.5-turbo-0301" -> 56, "gpt-3.5-turbo-0613" -> 58)
    }

    // test case taken from: https://github.com/openai/openai-cookbook/blob/main/examples/How_to_count_tokens_with_tiktoken.ipynb
    val openAICookbookTestCaseMessages: Seq[BaseMessage] = chat(
      SystemMessage("You are a helpful, pattern-following assistant that translates corporate jargon into plain English.", None),
      SystemMessage("New synergies will help drive top-line growth.").withName("example_user"),
      SystemMessage("Things working well together will increase revenue.").withName("example_assistant"),
      SystemMessage("Let's circle back when we have more bandwidth to touch base on opportunities for increased leverage.").withName("example_user"),
      SystemMessage("Let's talk later when we're less busy about how to do better.").withName("example_assistant"),
      UserMessage("This late pivot means we don't have time to boil the ocean for the client deliverable."),
    )

    "count tokens of a chat with multiple messages" in new TestCase {
      checkTokensForMessageCall(openAICookbookTestCaseMessages,
        "gpt-4-0613" -> 129, "gpt-4" -> 129, "gpt-3.5-turbo" -> 129, "gpt-3.5-turbo-0301" -> 127, "gpt-3.5-turbo-0613" -> 129)
    }

  }

  "countFunMessageTokens" should {
    "counting tokens with function - nested description" in new TestCase {
      private val function1 = FunctionSpec(
        name = "function",
        description = Some("description"),
        parameters = Map(
          "type" -> "object",
          "properties" -> ListMap(
            "quality" -> ListMap(
              "type" -> "object",
              "properties" -> ListMap(
                "pros" -> ListMap(
                  "type" -> "array",
                  "description" -> "Write 3 points why this text is well written",
                  "items" -> ListMap("type" -> "string")
                )
              )
            )
          )
        )
      )

      val messages: Seq[BaseMessage] = Seq(UserMessage("hello"))

      checkTokensForFunctionCall(Seq(function1), messages, expectedTokens = 46)
    }

    "counting tokens with function - required field" in new TestCase {
      private val function1 = FunctionSpec(
        name = "function",
        description = Some("description"),
        parameters = ListMap(
          "type" -> "object",
          "properties" -> ListMap(
            "title" -> ListMap("type" -> "string", "description" -> "Write something")
          ),
          "required" -> List("title")
        )
      )
      val messages: Seq[BaseMessage] = Seq(UserMessage("text1\ntext2\ntext3\n"))

      checkTokensForFunctionCall(Seq(function1), messages, expectedTokens = 53)
    }

    "counting tokens with function - nested description, enums" in new TestCase {
      private val function1 = FunctionSpec(
        name = "function",
        description = Some("description1"),
        parameters = ListMap(
          "type" -> "object",
          "description" -> "description2",
          "properties" -> ListMap(
            "mainField" -> ListMap("type" -> "string", "description" -> "description3"),
            "field number one" -> ListMap(
              "type" -> "object",
              "description" -> "description4",
              "properties" -> ListMap(
                "yesNoField" -> ListMap(
                  "type" -> "string",
                  "description" -> "description5",
                  "enum" -> List("Yes", "No")
                ),
                "howIsInteresting" -> ListMap(
                  "type" -> "string",
                  "description" -> "description6"
                ),
                "scoreInteresting" -> ListMap(
                  "type" -> "number",
                  "description" -> "description7"
                ),
                "isInteresting" -> ListMap(
                  "type" -> "string",
                  "description" -> "description8",
                  "enum" -> List("Yes", "No")
                )
              )
            )
          )
        )
      )
      val messages: Seq[BaseMessage] = Seq(UserMessage("hello"))

      checkTokensForFunctionCall(Seq(function1), messages, expectedTokens = 93)
    }

    "counting tokens with function - two fields in object" in new TestCase {
      private val function1 = FunctionSpec(
        name = "get_recipe",
        parameters = ListMap(
          "type" -> "object",
          "required" -> List("ingredients", "instructions", "time_to_cook"),
          "properties" -> ListMap(
            "ingredients" -> ListMap(
              "type" -> "array",
              "items" -> ListMap(
                "type" -> "object",
                "required" -> List("name", "unit", "amount"),
                "properties" -> ListMap(
                  "name" -> ListMap("type" -> "string"),
                  "unit" -> ListMap(
                    "enum" -> List("grams", "ml", "cups", "pieces", "teaspoons"),
                    "type" -> "string"
                  ),
                  "amount" -> ListMap("type" -> "number")
                )
              )
            ),
            "instructions" -> ListMap(
              "type" -> "array",
              "items" -> ListMap("type" -> "string"),
              "description" -> "Steps to prepare the recipe (no numbering)"
            ),
            "time_to_cook" -> ListMap(
              "type" -> "number",
              "description" -> "Total time to prepare the recipe in minutes"
            )
          )
        )
      )
      val messages: Seq[BaseMessage] = Seq(UserMessage("hello"))

      checkTokensForFunctionCall(Seq(function1), messages, expectedTokens = 106)
    }

    "counting tokens with function - many messages" in new TestCase {
      private val function1 = FunctionSpec(
        name = "do_stuff",
        parameters = ListMap("type" -> "object", "properties" -> ListMap())
      )
      val messages: Seq[BaseMessage] = Seq(
        SystemMessage("Hello:"),
        SystemMessage("Hello"),
        UserMessage("Hi there")
      )

      checkTokensForFunctionCall(Seq(function1), messages, expectedTokens = 40)
    }

    "counting tokens with function - empty properties in object" in new TestCase {
      private val function1 = FunctionSpec(
        name = "do_stuff",
        parameters = ListMap("type" -> "object", "properties" -> ListMap())
      )
      val messages: Seq[BaseMessage] =
        Seq(
          SystemMessage("Hello:"),
          UserMessage("Hi there")
        )
      checkTokensForFunctionCall(Seq(function1), messages, expectedTokens = 35)
    }

    "counting tokens with function - gpt4 model" in new TestCase {
      private val function1 = FunctionSpec(
        name = "function",
        description = Some("description"),
        parameters = Map(
          "type" -> "object",
          "properties" -> ListMap(
            "quality" -> ListMap(
              "type" -> "object",
              "properties" -> ListMap(
                "pros" -> ListMap(
                  "type" -> "array",
                  "description" -> "Write 3 points why this text is well written",
                  "items" -> ListMap("type" -> "string")
                )
              )
            )
          )
        )
      )
      val messages: Seq[BaseMessage] = Seq(UserMessage("hello"))

      checkTokensForFunctionCall(Seq(function1), messages, expectedTokens = 46, model = "gpt-4")
    }

    "counting tokens with function - responseFunctionName is set to Some" in new TestCase {
      private val function1 = FunctionSpec(
        name = "function",
        description = Some("description"),
        parameters = Map(
          "type" -> "object",
          "properties" -> ListMap(
            "quality" -> ListMap(
              "type" -> "object",
              "properties" -> ListMap(
                "pros" -> ListMap(
                  "type" -> "array",
                  "description" -> "Write 3 points why this text is well written",
                  "items" -> ListMap("type" -> "string")
                )
              )
            )
          )
        )
      )

      val messages: Seq[BaseMessage] = Seq(UserMessage("hello"))

      private val model = "gpt-3.5-turbo"
      private val responseFunctionName = Some("function")
      checkTokensForFunctionCall(
        Seq(function1),
        messages,
        expectedTokens = 51,
        responseFunctionName = responseFunctionName,
        model = model
      )
    }

    "counting tokens with function - array with enum" in new TestCase {
      private val function1 = FunctionSpec(
        name = "function",
        description = Some("description"),
        parameters = Map(
          "type" -> "object",
          "properties" -> ListMap(
            "quality" -> ListMap(
              "type" -> "object",
              "properties" -> ListMap(
                "pros" -> ListMap(
                  "type" -> "array",
                  "description" -> "Select few connected categories",
                  "items" -> ListMap(
                    "type" -> "string",
                    "enum" -> List(
                      "Basketball",
                      "Football",
                      "Golf",
                      "F1",
                      "Baseball",
                      "Soccer"
                    )
                  )
                )
              )
            )
          )
        )
      )

      val messages: Seq[BaseMessage] = Seq(UserMessage("hello"))

      private val model = "gpt-3.5-turbo"
      private val responseFunctionName = Some("function")
      checkTokensForFunctionCall(
        Seq(function1),
        messages,
        expectedTokens = 78,
        responseFunctionName = responseFunctionName,
        model = model
      )
    }
  }

  private def chat(messages: BaseMessage*): Seq[BaseMessage] = Seq(messages:_*)
}
