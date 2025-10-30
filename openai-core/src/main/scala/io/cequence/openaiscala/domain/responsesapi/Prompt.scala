package io.cequence.openaiscala.domain.responsesapi

/**
 * Reusable prompts are intended to be used with Response API.
 *
 * Here's how it works:
 *   - Create a reusable prompt in the dashboard with placeholders like {{customer_name}}.
 *   - Use the prompt in your API request with the prompt parameter. The prompt parameter
 *     object has three properties you can configure: id — Unique identifier of your prompt,
 *     found in the dashboard version — A specific version of your prompt (defaults to the
 *     "current" version as specified in the dashboard) variables — A map of values to
 *     substitute in for variables in your prompt. The substitution values can either be
 *     strings, or other Response input message types like input_image or input_file. See the
 *     full API reference.
 *
 * @param id
 *   The unique identifier of the prompt template to use.
 * @param variables
 *   Optional map of values to substitute in for variables in your prompt. The substitution
 *   values can either be strings, or other Response input types like images or files.
 * @param version
 *   Optional version of the prompt template.
 */
case class Prompt(
  id: String,
  variables: Map[String, String] = Map.empty,
  version: Option[String] = None
)
