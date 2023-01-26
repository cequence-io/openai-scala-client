# OpenAI Scala Client 🤖 [![version](https://img.shields.io/badge/version-0.0.1-green.svg)](https://cequence.io) [![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](https://opensource.org/licenses/MIT)

This is a no-nonsense async Scala client for OpenAI API supporting all the available endpoints and params (as defined [here](https://beta.openai.com/docs/api-reference)), provided in a single, convenient service called [OpenAIService](./openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIService.scala). The supported calls are: 

* **Models**: [listModels](https://beta.openai.com/docs/api-reference/models/list), and [retrieveModel](https://beta.openai.com/docs/api-reference/models/retrieve)
* **Completions**: [createCompletion](https://beta.openai.com/docs/api-reference/completions/create)
* **Edits**: [createEdit](https://beta.openai.com/docs/api-reference/edits/create)
* **Images**: [createImage](https://beta.openai.com/docs/api-reference/images/create), [createImageEdit](https://beta.openai.com/docs/api-reference/images/create-edit), and [createImageVariation](https://beta.openai.com/docs/api-reference/images/create-variation)
* **Embeddings**: [createEmbeddings](https://beta.openai.com/docs/api-reference/embeddings/create)
* **Files**: [listFiles](https://beta.openai.com/docs/api-reference/files/list), [uploadFile](https://beta.openai.com/docs/api-reference/files/upload), [deleteFile](https://beta.openai.com/docs/api-reference/files/delete), [retrieveFile](https://beta.openai.com/docs/api-reference/files/retrieve), and [retrieveFileContent](https://beta.openai.com/docs/api-reference/files/retrieve-content)
* **Fine-tunes**: [createFineTune](https://beta.openai.com/docs/api-reference/fine-tunes/create), [listFineTunes](https://beta.openai.com/docs/api-reference/fine-tunes/list), [retrieveFineTune](https://beta.openai.com/docs/api-reference/fine-tunes/retrieve), [cancelFineTune](https://beta.openai.com/docs/api-reference/fine-tunes/cancel), [listFineTuneEvents](https://beta.openai.com/docs/api-reference/fine-tunes/events), and [deleteFineTuneModel](https://beta.openai.com/docs/api-reference/fine-tunes/delete-model)
* **Moderations**: [createModeration](https://beta.openai.com/docs/api-reference/moderations/create)
 
Note that in order to be consistent with the OpenAI API naming, the service function names match exactly the API endpoint titles/descriptions with camelcase.
Also, we aimed the lib to be self-contained with the fewest dependencies possible therefore we ended up using only two libs `play-ahc-ws-standalone` and `play-ahc-ws-standalone`. Additionally, if dependency injection is required we use `scala-guice` lib as well.  

**✔️ Important**: this is a "community-maintained" library and, as such, has no relation to OpenAI company.

## Installation 🚀

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

## Config ⚙️

- Env. variables: `OPENAI_SCALA_CLIENT_API_KEY` and optionally also `OPENAI_SCALA_CLIENT_ORG_ID` (if you have one)
- File config: (default) [openai-scala-client.conf](./openai-core/src/main/resources/openai-scala-client.conf)

## Usage 👨‍🎓

**I. Obtaining OpenAIService**

First you need to provide an implicit execution context as well as akka materializer, e.g., as

```scala
  implicit val ec = ExecutionContext.global
  implicit val materializer = Materializer(ActorSystem())
```

Then you can obtain a service in one of the following ways:

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

- Via dependency injection (requires `openai-scala-guice` lib)

```scala
  class MyClass @Inject() (openAIService: OpenAIService) {...}
```

**II. Calling functions**

Full documentation of each call with their respective inputs and settings is provided in [OpenAIService](./openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIService.scala). Since all the calls are async they return responses wrapped in `Future`.

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


## FAQ 🤔

1. _Wen Scala 3?_ 

   Feb 2023


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