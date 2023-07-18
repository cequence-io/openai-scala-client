# OpenAI Scala Client 🤖
[![version](https://img.shields.io/badge/version-0.4.1-green.svg)](https://cequence.io) [![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](https://opensource.org/licenses/MIT) ![GitHub Stars](https://img.shields.io/github/stars/cequence-io/openai-scala-client?style=social) [![Twitter Follow](https://img.shields.io/twitter/follow/0xbnd?style=social)](https://twitter.com/0xbnd) ![GitHub CI](https://github.com/cequence-io/openai-scala-client/actions/workflows/continuous-integration.yml/badge.svg)

This is a no-nonsense async Scala client for OpenAI API supporting all the available endpoints and params **including streaming**, the newest **ChatGPT completion**, and **voice routines** (as defined [here](https://beta.openai.com/docs/api-reference)), provided in a single, convenient service called [OpenAIService](./openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIService.scala). The supported calls are: 

* **Models**: [listModels](https://platform.openai.com/docs/api-reference/models/list), and [retrieveModel](https://platform.openai.com/docs/api-reference/models/retrieve)
* **Completions**: [createCompletion](https://platform.openai.com/docs/api-reference/completions/create)
* **Chat Completions**: [createChatCompletion](https://platform.openai.com/docs/api-reference/chat/create), and [createChatFunCompletion](https://platform.openai.com/docs/api-reference/chat/create) **(🔥 new)**
* **Edits**: [createEdit](https://platform.openai.com/docs/api-reference/edits/create)
* **Images**: [createImage](https://platform.openai.com/docs/api-reference/images/create), [createImageEdit](https://platform.openai.com/docs/api-reference/images/create-edit), and [createImageVariation](https://platform.openai.com/docs/api-reference/images/create-variation)
* **Embeddings**: [createEmbeddings](https://platform.openai.com/docs/api-reference/embeddings/create)
* **Audio**: [createAudioTranscription](https://platform.openai.com/docs/api-reference/audio/create), [createAudioTranslation](https://platform.openai.com/docs/api-reference/audio/create) 
* **Files**: [listFiles](https://platform.openai.com/docs/api-reference/files/list), [uploadFile](https://platform.openai.com/docs/api-reference/files/upload), [deleteFile](https://platform.openai.com/docs/api-reference/files/delete), [retrieveFile](https://platform.openai.com/docs/api-reference/files/retrieve), and [retrieveFileContent](https://platform.openai.com/docs/api-reference/files/retrieve-content)
* **Fine-tunes**: [createFineTune](https://platform.openai.com/docs/api-reference/fine-tunes/create), [listFineTunes](https://platform.openai.com/docs/api-reference/fine-tunes/list), [retrieveFineTune](https://platform.openai.com/docs/api-reference/fine-tunes/retrieve), [cancelFineTune](https://platform.openai.com/docs/api-reference/fine-tunes/cancel), [listFineTuneEvents](https://platform.openai.com/docs/api-reference/fine-tunes/events), and [deleteFineTuneModel](https://platform.openai.com/docs/api-reference/fine-tunes/delete-model)
* **Moderations**: [createModeration](https://platform.openai.com/docs/api-reference/moderations/create)
 
Note that in order to be consistent with the OpenAI API naming, the service function names match exactly the API endpoint titles/descriptions with camelcase.
Also, we aimed the lib to be self-contained with the fewest dependencies possible therefore we ended up using only two libs `play-ahc-ws-standalone` and `play-ws-standalone-json` (at the top level). Additionally, if dependency injection is required we use `scala-guice` lib as well.  

🔥 **New**: This lib supports also "OpenAI-API-compatible" providers such as [FastChat](https://github.com/lm-sys/FastChat) (umbrella for open-source LLMs - Vicuna, Alpaca, LLaMA, fastchat-t5-3b-v1.0, mpt-7b-chat, etc.), [Azure](https://learn.microsoft.com/en-us/azure/cognitive-services/openai/reference), or any other similar service with a custom URL. Check the examples below for more details. 

👉 Check out an article about the lib/client on [Medium](https://medium.com/@0xbnd/openai-scala-client-is-out-d7577de934ad).

Try out also our [Scala client for Pinecone vector database](https://github.com/cequence-io/pinecone-scala), or use both clients together! [This demo project](https://github.com/cequence-io/pinecone-openai-scala-demo) shows how to generate and store OpenAI embeddings (with `text-embedding-ada-002` model) into Pinecone and query them afterward. The OpenAI + Pinecone combo is commonly used for autonomous AI agents, such as [babyAGI](https://github.com/yoheinakajima/babyagi) and [AutoGPT](https://github.com/Significant-Gravitas/Auto-GPT).

**✔️ Important**: this is a "community-maintained" library and, as such, has no relation to OpenAI company.

## Installation 🚀

The currently supported Scala versions are **2.12, 2.13**, and **3**.  

To pull the library you have to add the following dependency to your *build.sbt*

```
"io.cequence" %% "openai-scala-client" % "0.4.1"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>io.cequence</groupId>
    <artifactId>openai-scala-client_2.12</artifactId>
    <version>0.4.1</version>
</dependency>
```

If you want a streaming support use `"io.cequence" %% "openai-scala-client-stream" % "0.4.1"` instead.

## Config ⚙️

- Env. variables: `OPENAI_SCALA_CLIENT_API_KEY` and optionally also `OPENAI_SCALA_CLIENT_ORG_ID` (if you have one)
- File config (default):  [openai-scala-client.conf](./openai-client/src/main/resources/openai-scala-client.conf)

## Usage 👨‍🎓

**I. Obtaining OpenAIService**

First you need to provide an implicit execution context as well as akka materializer, e.g., as

```scala
  implicit val ec = ExecutionContext.global
  implicit val materializer = Materializer(ActorSystem())
```

Then you can obtain a service in one of the following ways.

- Default config (expects env. variable(s) to be set as defined in `Config` section)
```scala
  val service = OpenAIServiceFactory()
```

- Custom config
```scala
  val config = ConfigFactory.load("path_to_my_custom_config")
  val service = OpenAIServiceFactory(config)
```

- Without config

```scala
  val service = OpenAIServiceFactory(
     apiKey = "your_api_key",
     orgId = Some("your_org_id") // if you have one
  )
```

- Minimal `OpenAICoreService` supporting `listModels`, `createCompletion`, `createChatCompletion`, and `createEmbeddings` calls - e.g. [FastChat](https://github.com/lm-sys/FastChat) service running on the port 8000 (🔥 new)

```scala
  val service = OpenAICoreServiceFactory("http://localhost:8000/v1/")
```

- For Azure with API Key (🔥 new)

```scala
  val service = OpenAIServiceFactory.forAzureWithApiKey(
    resourceName = "your-resource-name",
    deploymentId = "your-deployment-id",
    apiVersion = "2023-05-15", // example
    apiKey = "your_api_key"
  )
```

- For Azure with Access Token (🔥 new)

```scala
  val service = OpenAIServiceFactory.forAzureWithAccessToken(
    resourceName = "your-resource-name",
    deploymentId = "your-deployment-id",
    apiVersion = "2023-05-15", // example
    accessToken = "your_access_token"
  )
```

**✔️ Important**: If you want streaming support use `OpenAIServiceStreamedFactory` from `openai-scala-client-stream` lib instead of `OpenAIServiceFactory` (in the three examples above). Three additional functions - `createCompletionStreamed`, `createChatCompletionStreamed`, and `listFineTuneEventsStreamed` provided by [OpenAIServiceStreamedExtra](./openai-client-stream/src/main/scala/io/cequence/openaiscala/service/OpenAIServiceStreamedExtra.scala) will be then available.

- Via dependency injection (requires `openai-scala-guice` lib)

```scala
  class MyClass @Inject() (openAIService: OpenAIService) {...}
```

**II. Calling functions**

Full documentation of each call with its respective inputs and settings is provided in [OpenAIService](./openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIService.scala). Since all the calls are async they return responses wrapped in `Future`.

Examples:

- List models

```scala
  service.listModels.map(models =>
    models.foreach(println)
  )
```

- Retrieve model
```scala
  service.retrieveModel(ModelId.text_davinci_003).map(model =>
    println(model.getOrElse("N/A"))
  )
```

- Create completion
```scala
  val text = """Extract the name and mailing address from this email:
               |Dear Kelly,
               |It was great to talk to you at the seminar. I thought Jane's talk was quite good.
               |Thank you for the book. Here's my address 2111 Ash Lane, Crestview CA 92002
               |Best,
               |Maya
             """.stripMargin

  service.createCompletion(text).map(completion =>
    println(completion.choices.head.text)
  )
```

- Create completion with a custom setting

```scala
  val text = """Extract the name and mailing address from this email:
               |Dear Kelly,
               |It was great to talk to you at the seminar. I thought Jane's talk was quite good.
               |Thank you for the book. Here's my address 2111 Ash Lane, Crestview CA 92002
               |Best,
               |Maya
             """.stripMargin

  service.createCompletion(
    text,
    settings = CreateCompletionSettings(
      model = ModelId.text_davinci_001,
      max_tokens = Some(1500),
      temperature = Some(0.9),
      presence_penalty = Some(0.2),
      frequency_penalty = Some(0.2)
    )
  ).map(completion =>
    println(completion.choices.head.text)
  )
```

- Create completion with streaming and a custom setting

```scala
  val source = service.createCompletionStreamed(
    prompt = "Write me a Shakespeare poem about two cats playing baseball in Russia using at least 2 pages",
    settings = CreateCompletionSettings(
      model = ModelId.text_davinci_003,
      max_tokens = Some(1500),
      temperature = Some(0.9),
      presence_penalty = Some(0.2),
      frequency_penalty = Some(0.2)
    )
  )

  source.map(completion => 
    println(completion.choices.head.text)
  ).runWith(Sink.ignore)
```
For this to work you need to use `OpenAIServiceStreamedFactory` from `openai-scala-client-stream` lib.

- Create chat completion 

```scala
  val createChatCompletionSettings = CreateChatCompletionSettings(
    model = ModelId.gpt_3_5_turbo
  )

  val messages: Seq[MessageSpec] = Seq(
    MessageSpec(role = ChatRole.System, content = "You are a helpful assistant."),
    MessageSpec(role = ChatRole.User, content = "Who won the world series in 2020?"),
    MessageSpec(role = ChatRole.Assistant, content = "The Los Angeles Dodgers won the World Series in 2020."),
    MessageSpec(role = ChatRole.User, content = "Where was it played"),
  )

  service.createChatCompletion(
    messages = messages,
    settings = createChatCompletionSettings
  ).map { chatCompletion =>
    println(chatCompletion.choices.head.message.content)
  }
```

- Create chat completion for functions 

```scala
  val messages = Seq(
    FunMessageSpec(role = ChatRole.User, content = Some("What's the weather like in Boston?")),
  )

  // as a param type we can use "number", "string", "boolean", "object", "array", and "null"
  val functions = Seq(
    FunctionSpec(
      name = "get_current_weather",
      description = Some("Get the current weather in a given location"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "location" -> Map(
            "type" -> "string",
            "description" -> "The city and state, e.g. San Francisco, CA",
          ),
          "unit" -> Map(
            "type" -> "string",
            "enum" -> Seq("celsius", "fahrenheit")
          )
        ),
        "required" -> Seq("location"),
      )
    )
  )

  // if we want to force the model to use the above function as a response
  // we can do so by passing: responseFunctionName = Some("get_current_weather")`
  service.createChatFunCompletion(
    messages = messages,
    functions = functions,
    responseFunctionName = None
  ).map { response =>
    val chatFunCompletionMessage = response.choices.head.message
    val functionCall = chatFunCompletionMessage.function_call

    println("function call name      : " + functionCall.map(_.name).getOrElse("N/A"))
    println("function call arguments : " + functionCall.map(_.arguments).getOrElse("N/A"))
  }
```
Note that instead of `MessageSpec`, the `function_call` version of the chat completion uses the `FunMessageSpec` class to define messages - both as part of the request and the response.
This extension of the standard chat completion is currently supported by the following `0613` models, all conveniently available in `ModelId` object:
- `gpt-3.5-turbo-0613` (default), `gpt-3.5-turbo-16k-0613`, `gpt-4-0613`, and `gpt-4-32k-0613`.


**✔️ Important Note**: After you are done using the service, you should close it by calling `service.close`. Otherwise, the underlying resources/threads won't be released.

**III. Using multiple services**

- Load distribution with `OpenAIMultiServiceAdapter` - _round robin_ (_rotation_) type

```scala
  val service1 = OpenAIServiceFactory("your-api-key1")
  val service2 = OpenAIServiceFactory("your-api-key2")
  val service3 = OpenAIServiceFactory("your-api-key3")

  val service = OpenAIMultiServiceAdapter.ofRoundRobinType(service1, service2, service3)

  service.listModels.map { models =>
    models.foreach(println)
    service.close()
  }
```

- Load distribution with `OpenAIMultiServiceAdapter` - _random order_ type

```scala
  val service1 = OpenAIServiceFactory("your-api-key1")
  val service2 = OpenAIServiceFactory("your-api-key2")
  val service3 = OpenAIServiceFactory("your-api-key3")

  val service = OpenAIMultiServiceAdapter.ofRandomOrderType(service1, service2, service3)

  service.listModels.map { models =>
    models.foreach(println)
    service.close()
  }
```

- Create completion and retry on transient errors (e.g. rate limit error)
```scala
import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.RetryHelpers
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.domain.{ChatRole, MessageSpec}
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import javax.inject.Inject
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class MyCompletionService @Inject() (
  val actorSystem: ActorSystem,
  implicit val ec: ExecutionContext,
  implicit val scheduler: Scheduler
)(val apiKey: String)
  extends RetryHelpers {
  val service: OpenAIService = OpenAIServiceFactory(apiKey)
  implicit val retrySettings: RetrySettings =
    RetrySettings(interval = 10.seconds)

  def ask(prompt: String): Future[String] =
    for {
      completion <- service
        .createChatCompletion(
          List(MessageSpec(ChatRole.User, prompt))
        )
        .retryOnFailure
    } yield completion.choices.head.message.content
}
```

- Retries with `OpenAIRetryServiceAdapter`

```scala
  val serviceAux = ... // your service

  implicit val retrySettings: RetrySettings = 
    RetrySettings(maxRetries = 10).constantInterval(10.seconds)
  // wrap it with the retry adapter
  val service = OpenAIRetryServiceAdapter(serviceAux)

  service.listModels.map { models =>
    models.foreach(println)
    service.close
  }
```

## FAQ 🤔

1. _Wen Scala 3?_ 

   ~~Feb 2023. You are right; we chose the shortest month to do so :)~~
 **Done!**


2. _I got a timeout exception. How can I change the timeout setting?_

   You can do it either by passing the `timeouts` param to `OpenAIServiceFactory` or, if you use your own configuration file, then you can simply add it there as: 

```
openai-scala-client {
    timeouts {
        requestTimeoutSec = 200
        readTimeoutSec = 200
        connectTimeoutSec = 5
        pooledConnectionIdleTimeoutSec = 60
    }
}
```

3. _I got an exception like `com.typesafe.config.ConfigException$UnresolvedSubstitution: openai-scala-client.conf @ jar:file:.../io/cequence/openai-scala-client_2.13/0.0.1/openai-scala-client_2.13-0.0.1.jar!/openai-scala-client.conf: 4: Could not resolve substitution to a value: ${OPENAI_SCALA_CLIENT_API_KEY}`. What should I do?_

   Set the env. variable `OPENAI_SCALA_CLIENT_API_KEY`. If you don't have one register [here](https://beta.openai.com/signup).


4. _It all looks cool. I want to chat with you about your research and development?_

   Just shoot us an email at [openai-scala-client@cequence.io](mailto:openai-scala-client@cequence.io?subject=Research%20andDevelopment).

## License ⚖️

This library is available and published as open source under the terms of the [MIT License](https://opensource.org/licenses/MIT).

## Contributors 🙏

This project is open-source and welcomes any contribution or feedback ([here](https://github.com/cequence-io/openai-scala-client/issues)).

Development of this library has been supported by  [<img src="https://cequence.io/favicon-16x16.png"> - Cequence.io](https://cequence.io) - `The future of contracting` 

Created and maintained by [Peter Banda](https://peterbanda.net).