# OpenAI Scala Client - Count tokens [![version](https://img.shields.io/badge/version-0.5.0-green.svg)](https://cequence.io) [![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](https://opensource.org/licenses/MIT)

This module provides ability for estimating the number of tokens an OpenAI chat completion request will use. 
Note that the full project documentation can be found [here](../README.md).

This code was written without any knowledge how counting tokens in OpenAI works under the hood, so it may not be 100% accurate.

## Installation 🚀

The currently supported Scala versions are **2.12, 2.13**, and **3**.

To pull the library you have to add the following dependency to your *build.sbt*

```
"io.cequence" %% "openai-scala-count-tokens" % "0.5.0"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>io.cequence</groupId>
    <artifactId>openai-scala-count-tokens_2.12</artifactId>
    <version>0.5.0</version>
</dependency>
```

## Usage

```scala
import io.cequence.openaiscala.service.OpenAICountTokensHelper
import io.cequence.openaiscala.domain.{ChatRole, FunMessageSpec, FunctionSpec}

val messages: Seq[FunMessageSpec] = ??? // messages to be sent to OpenAI
val function: FunctionSpec = ??? // function to be called

val service = new OpenAICountTokensService()

val tokens = service.countFunMessageTokens(messages, List(function), Some(function.name))
```



## Development and testing

Test with sbt

```bash
$ sbt test
```

When adding new test cases or debugging token count mismatches, it can be helpful to validate the estimated tokens in the tests against the live OpenAI API. To do this:

1. Set up the `OPENAI_SCALA_CLIENT_API_KEY` environment variable with a live API key and optionally also `OPENAI_SCALA_CLIENT_ORG_ID` (if you have one).
2. Set `validateWithChatGPT = true` in file `src/test/resources/application.conf`


## References
1. GitHub repository: [hmarr/openai-chat-tokens](https://github.com/hmarr/openai-chat-tokens)
2. "Counting tokens for chat completions API calls" in OpenAI's ["How to count tokens with tiktoken" notebook](https://github.com/openai/openai-cookbook/blob/main/examples/How_to_count_tokens_with_tiktoken.ipynb)
3. A post about [counting function call tokens](https://community.openai.com/t/how-to-calculate-the-tokens-when-using-function-call/266573/23) on the OpenAI forum.
