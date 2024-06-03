package io.cequence.openaiscala.domain.response

import io.cequence.openaiscala.domain.FileId

sealed trait AssistantToolResourceResponse

object AssistantToolResourceResponse {

  /**
   * @param fileIds
   *   A list of file IDs made available to the code_interpreter tool. There can be a maximum
   *   of 20 files associated with the tool.
   */
  final case class CodeInterpreterResourcesResponse(file_ids: Seq[FileId])
      extends AssistantToolResourceResponse

  /**
   * @param vectorStoreIds
   *   The vector store attached to this assistant. There can be a maximum of 1 vector store
   *   attached to the assistant.
   */
  final case class FileSearchResourcesResponse(vector_store_ids: Seq[String])
      extends AssistantToolResourceResponse
}
