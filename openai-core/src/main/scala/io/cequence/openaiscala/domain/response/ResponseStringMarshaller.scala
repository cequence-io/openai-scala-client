package io.cequence.openaiscala.domain.response

/**
 * Used only for debugging... can be removed later on
 */
trait ResponseStringMarshaller {

  def fileInfoToString(fileInfo: FileInfo) =
    s"""File Info
       |-id: ${fileInfo.id}
       |-filename: ${fileInfo.filename}
       |-bytes: ${fileInfo.bytes}
       |-created at: ${fileInfo.created_at.toString}
       |-status: ${fileInfo.status}
       |-status_details: ${fileInfo.status_details.getOrElse("N/A")}
       |-purpose: ${fileInfo.purpose}
     """.stripMargin

  def imageToString(image: ImageInfo) =
    s"""Image
       |-created: ${image.created.toString}
       |-data: ${image.data.map(_.mkString(", ")).mkString("; ")}
     """.stripMargin

  def embeddingToString(embedding: EmbeddingResponse) =
    s"""Completion
       |-model: ${embedding.model}
       |-data: ${embedding.data.map(embeddingInfoToString).mkString("\n")}
       |-usage: ${usageToString(embedding.usage)},
     """.stripMargin

  def embeddingInfoToString(embeddingInfo: EmbeddingInfo) =
    s"""Embedding Info
       |-index: ${embeddingInfo.index}
       |-embedding: ${embeddingInfo.embedding.mkString(", ")}
     """.stripMargin

  def moderationToString(edit: ModerationResponse) =
    s"""Moderation
       |-id: ${edit.id}
       |-model: ${edit.model}
       |-results: ${edit.results.map(moderationResultToString).mkString("\n")}
     """.stripMargin

  def moderationResultToString(moderationResult: ModerationResult) =
    s"""Moderation Result
       |-categories: ${moderationCategoriesToString(moderationResult.categories)}
       |-category scores: ${moderationCategoryScoresToString(moderationResult.category_scores)}
       |-flagged: ${moderationResult.flagged}
     """.stripMargin

  def moderationCategoriesToString(moderationCategories: ModerationCategories) =
    s"""Moderation Categories
       |-hate: ${moderationCategories.hate}
       |-hate threatening: ${moderationCategories.hate_threatening}
       |-self harm: ${moderationCategories.self_harm}
       |-sexual: ${moderationCategories.sexual}
       |-sexual minors: ${moderationCategories.sexual_minors}
       |-violence: ${moderationCategories.violence}
       |-violence_graphic: ${moderationCategories.violence_graphic}
     """.stripMargin

  def moderationCategoryScoresToString(moderationCategoryScores: ModerationCategoryScores) =
    s"""Moderation Category Scores
       |-hate: ${moderationCategoryScores.hate}
       |-hate threatening: ${moderationCategoryScores.hate_threatening}
       |-self harm: ${moderationCategoryScores.self_harm}
       |-sexual: ${moderationCategoryScores.sexual}
       |-sexual minors: ${moderationCategoryScores.sexual_minors}
       |-violence: ${moderationCategoryScores.violence}
       |-violence_graphic: ${moderationCategoryScores.violence_graphic}
     """.stripMargin

  def editToString(edit: TextEditResponse) =
    s"""Completion
       |-created: ${edit.created.toString}
       |-usage: ${usageToString(edit.usage)}
       |-choices: ${edit.choices.map(editChoiceToString).mkString("\n")}
     """.stripMargin

  def editChoiceToString(choice: TextEditChoiceInfo) =
    s"""Choice
       |-index: ${choice.index}
       |-text: ${choice.text}
       |-logprobs: ${choice.logprobs.map(logprobsToString).getOrElse("N/A")}
     """.stripMargin

  def completionToString(completion: TextCompletionResponse) =
    s"""Completion
       |-id: ${completion.id}
       |-model: ${completion.model}
       |-created" ${completion.created.toString}
       |-usage: ${completion.usage.map(usageToString).getOrElse("N/A")}
       |-choices: ${completion.choices.map(completionChoiceToString).mkString("\n")}
     """.stripMargin

  def completionChoiceToString(choice: TextCompletionChoiceInfo) =
    s"""Choice
       |-index: ${choice.index}
       |-text: ${choice.text}
       |-logprobs: ${choice.logprobs.map(logprobsToString).getOrElse("N/A")}
       |-finish reason: ${choice.finish_reason}
     """.stripMargin

  def logprobsToString(logProb: LogprobsInfo) =
    s"""Logprobs
       |-tokens: ${logProb.tokens.mkString(", ")}
       |-token_logprobs: ${logProb.token_logprobs.mkString(", ")}
       |-top_logprobs: ${logProb.top_logprobs.map(_.mkString(",")).mkString("; ")}
       |-text_offset: ${logProb.text_offset.mkString(", ")}
     """.stripMargin

  def usageToString(usage: UsageInfo) =
    s"""Usage
       |-prompt tokens: ${usage.prompt_tokens}
       |-completion tokens: ${usage.completion_tokens.getOrElse("N/A")}
       |-total tokens: ${usage.total_tokens}
     """.stripMargin

  def usageToString(usage: EmbeddingUsageInfo) =
    s"""Usage
       |-prompt tokens: ${usage.prompt_tokens}
       |-total tokens: ${usage.total_tokens}
     """.stripMargin
}