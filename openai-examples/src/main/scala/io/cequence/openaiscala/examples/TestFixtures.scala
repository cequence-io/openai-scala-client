package io.cequence.openaiscala.examples

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
    properties = Seq(
      "countries" -> JsonSchema.Array(
        items = JsonSchema.Object(
          properties = Seq(
            "country" -> JsonSchema.String(
              description = Some("The name of the country")
            ),
            "capital" -> JsonSchema.String(
              description = Some("The capital city of the country")
            )
          ),
          required = Seq("country", "capital"),
          // Behavioral probe: if this Object description is honored, capital is ALL CAPS.
          description =
            Some("A single country record. The 'capital' value MUST be in ALL UPPERCASE.")
        ),
        // Behavioral probe: if this Array description is honored, we get exactly 2 items.
        description =
          Some("The list MUST contain EXACTLY 2 country entries, no more and no less.")
      )
    ),
    required = Seq("countries"),
    description = Some("Top-level container for the capitals response")
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
