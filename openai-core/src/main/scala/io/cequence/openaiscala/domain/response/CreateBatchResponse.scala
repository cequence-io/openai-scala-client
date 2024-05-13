package io.cequence.openaiscala.domain.response

/**
 * Represents the response of a batch request.
 *
 * @param status_code
 *   The HTTP status code of the response.
 * @param request_id
 *   An unique identifier for the OpenAI API request. Please include this request ID when
 *   contacting support.
 * @param body
 *   The JSON body of the response.
 */
trait BatchResponse

case class ChatCompletionBatchResponse(
  status_code: Int,
  request_id: String,
  body: ChatCompletionResponse // completion response or embeddings response
) extends BatchResponse

case class EmbeddingBatchResponse(
  status_code: Int,
  request_id: String,
  body: EmbeddingResponse // completion response or embeddings response
) extends BatchResponse

/**
 * Represents an error in a batch request.
 *
 * @param code
 *   A machine-readable error code.
 * @param message
 *   A human-readable error message.
 */
case class BatchError(
  code: String,
  message: String
)

/**
 * Represents the output of a batch request.
 *
 * @param id
 *   The unique identifier for the batch request.
 * @param custom_id
 *   A developer-provided per-request id that will be used to match outputs to inputs.
 * @param response
 *   The response object, which can be null. It contains the HTTP status code, request id, and
 *   the JSON body of the response.
 * @param error
 *   The error object, which can be null. For requests that failed with a non-HTTP error, this
 *   will contain more information on the cause of the failure. It contains a machine-readable
 *   error code and a human-readable error message.
 *
 * <a href="https://platform.openai.com/docs/api-reference/batch/requestOutput">OpenAI API
 * Reference</a>
 */
case class CreateBatchResponse(
  id: String,
  custom_id: String,
  response: BatchResponse,
  error: Option[BatchError]
)

case class CreateBatchResponses(
  responses: Seq[CreateBatchResponse]
)
