package io.cequence.openaiscala.domain

final case class ThreadAndRunToolResource(
  codeInterpreter: Option[ThreadAndRunToolResource.CodeInterpreterResource],
  fileSearchResources: Option[ThreadAndRunToolResource.FileSearchResource]
)

object ThreadAndRunToolResource {

  /**
   * @param fileIds
   *   A list of file IDs made available to the code_interpreter tool. There can be a maximum
   *   of 20 files associated with the tool.
   */
  final case class CodeInterpreterResource(fileIds: Seq[FileId])

  /**
   * @param vectorStoreIds
   *   The vector store attached to this assistant. There can be a maximum of 1 vector store
   *   attached to the assistant.
   */
  final case class FileSearchResource(vectorStoreIds: Seq[String])

}
