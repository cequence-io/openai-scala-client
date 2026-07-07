package io.cequence.openaiscala.vertexai.service

/**
 * Configuration enabling the provider-agnostic chat-completion batch endpoints on the Vertex
 * AI OpenAI adapter. Vertex AI batch prediction has no inline input/output, so requests are
 * staged as a JSONL file in Cloud Storage and results are read back from there.
 *
 * @param batchService
 *   The batch-prediction service to run the jobs with.
 * @param gcsBucket
 *   Cloud Storage bucket used for staging inputs and outputs. The Application Default
 *   Credentials must be allowed to read/write it.
 * @param gcsPathPrefix
 *   Object-name prefix under which the batch folders are created.
 */
final case class VertexAIBatchSupport(
  batchService: VertexAIBatchPredictionService,
  gcsBucket: String,
  gcsPathPrefix: String = "openai-scala-client-batches"
)
