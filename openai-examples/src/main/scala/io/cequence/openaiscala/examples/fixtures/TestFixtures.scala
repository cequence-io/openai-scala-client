package io.cequence.openaiscala.examples.fixtures

import scala.collection.immutable.ListMap

trait TestFixtures {

  val capitalsPrompt = "Give me the most populous capital cities in JSON format."

  val capitalsSchema = Map(
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
