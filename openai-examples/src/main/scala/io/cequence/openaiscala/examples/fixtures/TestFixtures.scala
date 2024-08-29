package io.cequence.openaiscala.examples.fixtures

import io.cequence.openaiscala.domain.settings.JsonSchema

trait TestFixtures {

  val capitalsPrompt = "Give me the most populous capital cities in JSON format."

  val capitalsSchema = JsonSchema(
    name = "capitals_response",
    strict = true,
    structure = capitalsSchemaStructure
  )

  lazy private val capitalsSchemaStructure = Map(
    "type" -> "object",
    "properties" -> Map(
      "countries" -> Map(
        "type" -> "array",
        "items" -> Map(
          "type" -> "object",
          "properties" -> Map(
            "country" -> Map(
              "type" -> "string",
              "description" -> "The name of the country"
            ),
            "capital" -> Map(
              "type" -> "string",
              "description" -> "The capital city of the country"
            )
          ),
          "required" -> Seq("country", "capital")
        )
      )
    ),
    "required" -> Seq("countries")
  )

}
