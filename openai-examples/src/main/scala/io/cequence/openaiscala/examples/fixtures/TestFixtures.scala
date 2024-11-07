package io.cequence.openaiscala.examples.fixtures

import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.domain.settings.JsonSchemaDef
import org.slf4j.LoggerFactory

trait TestFixtures {

  val logger = LoggerFactory.getLogger(getClass)

  val capitalsPrompt = "Give me the most populous capital cities in JSON format."

  val capitalsSchemaDef1 = capitalsSchemaDefAux(Left(capitalsSchema1))

  val capitalsSchemaDef2 = capitalsSchemaDefAux(Right(capitalsSchema2))

  def capitalsSchemaDefAux(schema: Either[JsonSchema, Map[String, Any]]): JsonSchemaDef =
    JsonSchemaDef(
      name = "capitals_response",
      strict = true,
      structure = schema
    )

  lazy protected val capitalsSchema1 = JsonSchema.Object(
    properties = Map(
      "countries" -> JsonSchema.Array(
        items = JsonSchema.Object(
          properties = Map(
            "country" -> JsonSchema.String(
              description = Some("The name of the country")
            ),
            "capital" -> JsonSchema.String(
              description = Some("The capital city of the country")
            )
          ),
          required = Seq("country", "capital")
        )
      )
    ),
    required = Seq("countries")
  )

  lazy protected val capitalsSchema2 = Map(
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
