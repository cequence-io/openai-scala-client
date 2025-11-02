package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.graders.Grader
import scala.concurrent.Future

/**
 * Service interface for OpenAI Graders API endpoints.
 *
 * The Graders API provides a way to evaluate model outputs against specific criteria using
 * graders.
 *
 *   - Available Functions:
 *
 * '''Run Grader'''
 *   - [[runGrader]] - Runs a grader to evaluate a model sample against a dataset item
 *   - [[https://platform.openai.com/docs/api-reference/graders/run API Doc]]
 *
 * '''Validate Grader'''
 *   - [[validateGrader]] - Validates a grader
 *   - [[https://platform.openai.com/docs/api-reference/graders/validate API Doc]]
 *
 * @see
 *   <a href="https://platform.openai.com/docs/api-reference/graders">OpenAI Responses API
 *   Doc</a>
 */
trait OpenAIGraderService extends OpenAIServiceConsts {

  /**
   * Runs a grader to evaluate a model sample against a dataset item.
   *
   * @param grader
   *   The grader configuration used for evaluation
   * @param modelSample
   *   The model sample to be evaluated. This value will be used to populate the sample
   *   namespace. The output_json variable will be populated if the model sample is a valid
   *   JSON string
   * @param item
   *   The dataset item provided to the grader. This will be used to populate the item
   *   namespace
   * @return
   *   The evaluation result as a string
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/graders/run">OpenAI Doc</a>
   */
  def runGrader(
    grader: Grader,
    modelSample: String,
    item: Map[String, Any]
  ): Future[String]

  /**
   * Validates a grader.
   *
   * @param grader
   *   The grader used for the fine-tuning job.
   * @return
   *   The validated grader object.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/graders/validate">OpenAI Doc</a>
   */
  def validateGrader(
    grader: Grader
  ): Future[Grader]
}
