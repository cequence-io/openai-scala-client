# OpenAI Scala Client [![version](https://img.shields.io/badge/version-0.0.1-green.svg)](https://cequence.io) [![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](https://opensource.org/licenses/MIT)

This is a no-nonsense async Scala client for OpenAI API supporting all the available endpoints and params (as defined [here](https://beta.openai.com/docs/api-reference)), provided in a single, convenient service called [OpenAIService](./openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIService.scala). The supported calls: 

*  **Models**: [listModels](https://beta.openai.com/docs/api-reference/models/list), and [retrieveModel](https://beta.openai.com/docs/api-reference/models/retrieve)
*  **Completions**: [createCompletion](https://beta.openai.com/docs/api-reference/completions/create)
*  **Edits**: [createEdit](https://beta.openai.com/docs/api-reference/edits/create)
*  **Images**: [createImage](https://beta.openai.com/docs/api-reference/images/create), [createImageEdit](https://beta.openai.com/docs/api-reference/images/create-edit), and [createImageVariation](https://beta.openai.com/docs/api-reference/images/create-variation)
*  **Embeddings**: [createEmbeddings](https://beta.openai.com/docs/api-reference/embeddings/create)
*  **Files**: [listFiles](https://beta.openai.com/docs/api-reference/files/list), [uploadFile](https://beta.openai.com/docs/api-reference/files/upload), [deleteFile](https://beta.openai.com/docs/api-reference/files/delete), [retrieveFile](https://beta.openai.com/docs/api-reference/files/retrieve), and [retrieveFileContent](https://beta.openai.com/docs/api-reference/files/retrieve-content)
*  **Fine-tunes**: [createFineTune](https://beta.openai.com/docs/api-reference/fine-tunes/create), [listFineTunes](https://beta.openai.com/docs/api-reference/fine-tunes/list), [retrieveFineTune](https://beta.openai.com/docs/api-reference/fine-tunes/retrieve), [cancelFineTune](https://beta.openai.com/docs/api-reference/fine-tunes/cancel), [listFineTuneEvents](https://beta.openai.com/docs/api-reference/fine-tunes/events), and [deleteFineTuneModel](https://beta.openai.com/docs/api-reference/fine-tunes/delete-model)
*  **Moderations**: [createModeration](https://beta.openai.com/docs/api-reference/moderations/create)

Note that in order to be consistent with the OpenAI API naming, the service's function names match exactly the API endpoint names/descriptions with camelcase.
We also aimed to reduce dependencies as much as possible therefore we aimed the lib to be self-contained and use only two libs `play-ahc-ws-standalone` and `play-ahc-ws-standalone`. Additionally, if dependency injection is required we use `scala-guice` lib.  

## Installation

The currently supported Scala versions are **2.12** and **2.13** but **Scala 3**-version will come out soon.

To pull the library you have to add the following dependency to your *build.sbt*

```
"io.cequence" %% "openai-scala-client" % "0.0.1"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>io.cequence</groupId>
    <artifactId>openai-scala-client_2.12</artifactId>
    <version>0.0.1</version>
</dependency>
```

## Config

- Env. variables: `OPENAI_SCALA_CLIENT_API_KEY` and optionally also `OPENAI_SCALA_CLIENT_ORG_ID` (if you have one)
- File config: [openai-scala-client.conf](./openai-core/src/main/resources/openai-scala-client.conf)

## Usage

**I. Obtaining OpenAIService**

First you need to provide an implicit execution context and akka materializer, e.g., as

```scala
  implicit val ec = ExecutionContext.global
  implicit val materializer = Materializer(ActorSystem())
```

Then you can get a service in one of the following ways:

- Default config (expects env. variables to be set as defined in `Config` section)
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

- Via dependecy injection (requires `openai-scala-guice` lib)

```scala
  class MyClass @Inject() (openAIService: OpenAIService) {...}
```

**II. Calling functions**

Note that all calls are async therefore they return `Future`s. Full documentation of each call, which includes the inputs and the settings, is provided in [OpenAIService](./openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIService.scala) 

Examples:

- List models:

```scala
  service.listModels.map(models =>
    models.foreach(println(_))
  )
```

- Retrieve model:
```scala
  service.retrieveModel(ModelId.text_davinci_003).map(model =>
    println(model.getOrElse("N/A"))
  )
```

- Create completion:
```scala
  val text = """Extract the name and mailing address from this email:
               |Dear Kelly,
               |It was great to talk to you at the seminar. I thought Jane's talk was quite good.
               |Thank you for the book. Here's my address 2111 Ash Lane, Crestview CA 92002
               |Best,
               |Maya
             """.stripMargin

  service.createCompletion(text).map(completition =>
    println(completition.choices.head.text)
  )
```

- Create completion with a custom setting:

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
      max_tokens = Some(2000),
      temperature = Some(0.9),
      presence_penalty = Some(0.2),
      frequency_penalty = Some(0.2)
    )
  ).map(completition =>
    println(completition.choices.head.text)
  )
```


## FAQ

1. *Wen Scala 3?* 

   Feb 2023

2. I got a timeout exception. How can I change the timeout setting?

   You can do it either by passing the `timeouts` param to `OpenAIServiceFactory` or if you use your own configuration file then you can set it there, such as: 

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

## License

This library is available and published as open source under the terms of the [MIT License](https://opensource.org/licenses/MIT).

## Contributors

This project is open-source and welcomes any contribution or feedback ([here]()).

Development of this library has been supported by  [<img src="https://cequence.io/favicon-16x16.png"> - Cequence.io](https://cequence.io) - `The future of contracting` 

Created and maintained by [Peter Banda](https://peterbanda.net).