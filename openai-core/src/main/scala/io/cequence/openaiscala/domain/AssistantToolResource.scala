package io.cequence.openaiscala.domain

final case class AssistantToolResource(
  codeInterpreter: Option[AssistantToolResource.CodeInterpreterResources],
  fileSearch: Option[AssistantToolResource.FileSearchResources]
)

object AssistantToolResource {

  def apply(): AssistantToolResource = AssistantToolResource(None, None)
  def apply(codeInterpreterResources: CodeInterpreterResources): AssistantToolResource =
    AssistantToolResource(Some(codeInterpreterResources), None)
  def apply(fileSearchResources: FileSearchResources): AssistantToolResource =
    AssistantToolResource(None, Some(fileSearchResources))

  /**
   * @param fileIds
   *   A list of file IDs made available to the code_interpreter tool. There can be a maximum
   *   of 20 files associated with the tool.
   */
  final case class CodeInterpreterResources(fileIds: Seq[FileId])

  /**
   * @param vectorStoreIds
   *   The vector store attached to this assistant. There can be a maximum of 1 vector store
   *   attached to the assistant.
   * @param vectorStores
   *   A helper to create a vector store with file_ids and attach it to this assistant. There
   *   can be a maximum of 1 vector store attached to the assistant.
   */
  final case class FileSearchResources(
    vectorStoreIds: Seq[String],
    vectorStores: Seq[VectorStore] = Nil
  )

  /**
   * @param fileIds
   *   A list of file IDs to add to the vector store. There can be a maximum of 10000 files in
   *   a vector store.
   * @param chunkingStrategy
   *   The chunking strategy used to chunk the file(s). If not set, will use the auto strategy.
   * @param metadata
   *   Set of 16 key-value pairs that can be attached to a vector store. This can be useful for
   *   storing additional information about the vector store in a structured format. Keys can
   *   be a maximum of 64 characters long and values can be a maximum of 512 characters long.
   */
  final case class VectorStore(
    fileIds: Seq[FileId],
    metadata: Map[String, String],
    chunkingStrategy: Option[ChunkingStrategy] = None
  )

  object VectorStore {
    def unapply(vectorStore: VectorStore)
      : Option[(Seq[FileId], Map[String, String], Option[ChunkingStrategy])] =
      Some((vectorStore.fileIds, vectorStore.metadata, vectorStore.chunkingStrategy))
  }
}
