package io.cequence.openaiscala.domain.settings

case class CreateEditSettings(
  // ID of the model to use
  model: String,

  // How many edits to generate for the input and instruction. Defaults to 1.
  n: Option[Int] = None,

  // What sampling temperature to use. Higher values means the model will take more risks.
  // Try 0.9 for more creative applications, and 0 (argmax sampling) for ones with a well-defined answer.
  // We generally recommend altering this or top_p but not both. Defaults to 1.
  temperature: Option[Double] = None,

  // An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens with top_p probability mass.
  // So 0.1 means only the tokens comprising the top 10% probability mass are considered.
  // We generally recommend altering this or temperature but not both. Defaults to 1.
  top_p: Option[Double] = None
)
