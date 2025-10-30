package io.cequence.openaiscala.domain.graders

/**
 * A grader that runs a python script on the input.
 *
 * @param imageTag
 *   The image tag to use for the python script.
 * @param name
 *   The name of the grader.
 * @param source
 *   The source code of the python script.
 */
case class PythonGrader(
  imageTag: String,
  name: String,
  source: String
) extends Grader {
  override val `type`: String = "python"
}
