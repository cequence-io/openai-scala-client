package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.JsonSchema
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * The function-definition text the token counter measures, pinned to the upstream
 * openai-chat-tokens rendering: enums render as their values (strings quoted, numbers bare),
 * minimum / maximum are not rendered.
 */
class FunctionCallOpenAISerializerSpec extends AnyWordSpec with Matchers {

  "formatFunctionDefinitions" should {

    "render numeric enums like string enums, and ignore bounds" in {
      val text = FunctionCallOpenAISerializer.formatFunctionDefinitions(
        Seq(
          FunctionTool(
            name = "rate",
            parameters = JsonSchema.Object(
              properties = Seq(
                "stars" -> JsonSchema.Integer(Some("Stars"), `enum` = Seq(1, 2, 3)),
                "weight" -> JsonSchema.Number(`enum` = Seq(0.5, 1, 2.25)),
                "bounded" -> JsonSchema.Integer(minimum = Some(1), maximum = Some(5)),
                "unit" -> JsonSchema.String(`enum` = Seq("g", "kg"))
              )
            )
          )
        )
      )

      text should include regex "stars\\??: 1 \\| 2 \\| 3"
      text should include regex "weight\\??: 0\\.5 \\| 1 \\| 2\\.25"
      text should include regex "bounded\\??: number"
      text should include regex "unit\\??: \"g\" \\| \"kg\""
      text should not include "minimum"
    }
  }
}
