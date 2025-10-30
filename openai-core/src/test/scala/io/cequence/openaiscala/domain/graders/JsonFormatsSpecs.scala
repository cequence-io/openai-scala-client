package io.cequence.openaiscala.domain.graders

import io.cequence.openaiscala.domain.ChatRole
import io.cequence.openaiscala.domain.graders.JsonFormats._
import io.cequence.openaiscala.domain.settings.ReasoningEffort
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json._

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

  private lazy val isScala3 = true

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

    if (!justSemantics && !isScala3) {
      serialized shouldBe json
    } else if (!justSemantics) {
      val parsed = Json.parse(serialized).as[A]
      parsed shouldBe value
    }

    val deserialized = Json.parse(json).as[A]
    deserialized shouldBe value
  }

  "JSON Formats for graders package" should {

    "serialize and deserialize StringGrader" in {
      testCodec[Grader](
        StringGrader(
          input = "{{sample.output_text}}",
          name = "Example string check grader",
          operation = StringCheckOperation.eq,
          reference = "{{item.label}}"
        ),
        """{
          |  "type": "string_check",
          |  "name": "Example string check grader",
          |  "input": "{{sample.output_text}}",
          |  "reference": "{{item.label}}",
          |  "operation": "eq"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize TextSimilarityGrader" in {
      testCodec[Grader](
        TextSimilarityGrader(
          input = "{{sample.output_text}}",
          name = "Example text similarity grader",
          reference = "{{item.label}}",
          evaluationMetric = TextSimilarityEvaluationMetric.fuzzy_match
        ),
        """{
          |  "type": "text_similarity",
          |  "name": "Example text similarity grader",
          |  "input": "{{sample.output_text}}",
          |  "reference": "{{item.label}}",
          |  "evaluation_metric": "fuzzy_match"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize ScoreModelGrader" in {
      testCodec[Grader](
        ScoreModelGrader(
          input = Seq(
            GraderModelInput(
              content = GraderInputContent.TextString(
                "Score how close the reference answer is to the model answer. Score 1.0 if they are the same and 0.0 if they are different. Return just a floating point score\n\n Reference answer: {{item.label}}\n\n Model answer: {{sample.output_text}}"
              ),
              role = ChatRole.User
            )
          ),
          model = "o4-mini-2025-04-16",
          name = "Example score model grader",
          range = Nil,
          samplingParams = Some(
            SamplingParams(
              maxCompletionsTokens = Some(32768),
              reasoningEffort = Some(ReasoningEffort.medium),
              seed = Some(42),
              temperature = Some(1.0),
              topP = Some(1.0)
            )
          )
        ),
        """{
          |    "type": "score_model",
          |    "name": "Example score model grader",
          |    "input": [
          |        {
          |            "role": "user",
          |            "content": "Score how close the reference answer is to the model answer. Score 1.0 if they are the same and 0.0 if they are different. Return just a floating point score\n\n Reference answer: {{item.label}}\n\n Model answer: {{sample.output_text}}",
          |            "type": "message"
          |        }
          |    ],
          |    "model": "o4-mini-2025-04-16",
          |    "sampling_params": {
          |        "temperature": 1,
          |        "top_p": 1,
          |        "seed": 42,
          |        "max_completions_tokens": 32768,
          |        "reasoning_effort": "medium"
          |    }
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize LabelModelGrader" in {
      testCodec[Grader](
        LabelModelGrader(
          input = Seq(
            GraderModelInput(
              content = GraderInputContent.InputText(
                text =
                  "Classify the sentiment of the following statement as one of positive, neutral, or negative"
              ),
              role = ChatRole.System
            ),
            GraderModelInput(
              content = GraderInputContent.InputText(
                text = "Statement: {{item.response}}"
              ),
              role = ChatRole.User
            )
          ),
          labels = Seq("positive", "neutral", "negative"),
          model = "gpt-4o-2024-08-06",
          name = "First label grader",
          passingLabels = Seq("positive")
        ),
        """{
          |  "name": "First label grader",
          |  "type": "label_model",
          |  "model": "gpt-4o-2024-08-06",
          |  "input": [
          |    {
          |      "type": "message",
          |      "role": "system",
          |      "content": {
          |        "type": "input_text",
          |        "text": "Classify the sentiment of the following statement as one of positive, neutral, or negative"
          |      }
          |    },
          |    {
          |      "type": "message",
          |      "role": "user",
          |      "content": {
          |        "type": "input_text",
          |        "text": "Statement: {{item.response}}"
          |      }
          |    }
          |  ],
          |  "passing_labels": [
          |    "positive"
          |  ],
          |  "labels": [
          |    "positive",
          |    "neutral",
          |    "negative"
          |  ]
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize PythonGrader" in {
      val pythonSource =
        "\ndef grade(sample: dict, item: dict) -> float:\n    \"\"\"\n    Returns 1.0 if `output_text` equals `label`, otherwise 0.0.\n    \"\"\"\n    output = sample.get(\"output_text\")\n    label = item.get(\"label\")\n    return 1.0 if output == label else 0.0\n"

      testCodec[Grader](
        PythonGrader(
          imageTag = "2025-05-08",
          name = "Example python grader",
          source = pythonSource
        ),
        """{
          |  "type": "python",
          |  "name": "Example python grader",
          |  "image_tag": "2025-05-08",
          |  "source": "\ndef grade(sample: dict, item: dict) -> float:\n    \"\"\"\n    Returns 1.0 if `output_text` equals `label`, otherwise 0.0.\n    \"\"\"\n    output = sample.get(\"output_text\")\n    label = item.get(\"label\")\n    return 1.0 if output == label else 0.0\n"
          |}""".stripMargin,
        Pretty
      )
    }

    "serialize and deserialize MultiGrader" in {
      testCodec[Grader](
        MultiGrader(
          calculateOutput = "0.5 * text_similarity_score +  0.5 * string_check_score)",
          graders = Seq(
            TextSimilarityGrader(
              input = "The graded text",
              name = "example text similarity grader",
              reference = "The reference text",
              evaluationMetric = TextSimilarityEvaluationMetric.fuzzy_match
            ),
            StringGrader(
              input = "{{sample.output_text}}",
              name = "Example string check grader",
              operation = StringCheckOperation.eq,
              reference = "{{item.label}}"
            )
          ),
          name = "example multi grader"
        ),
        """{
          |  "type": "multi",
          |  "name": "example multi grader",
          |  "graders": [
          |    {
          |      "type": "text_similarity",
          |      "name": "example text similarity grader",
          |      "input": "The graded text",
          |      "reference": "The reference text",
          |      "evaluation_metric": "fuzzy_match"
          |    },
          |    {
          |      "type": "string_check",
          |      "name": "Example string check grader",
          |      "input": "{{sample.output_text}}",
          |      "reference": "{{item.label}}",
          |      "operation": "eq"
          |    }
          |  ],
          |  "calculate_output": "0.5 * text_similarity_score +  0.5 * string_check_score)"
          |}""".stripMargin,
        Pretty
      )
    }
  }
}
