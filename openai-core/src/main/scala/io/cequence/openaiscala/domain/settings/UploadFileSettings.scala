package io.cequence.openaiscala.domain.settings

import io.cequence.wsclient.domain.EnumValue

case class UploadFileSettings(
  // The intended purpose of the uploaded documents. Use "fine-tune" for Fine-tuning.
  // This allows us to validate the format of the uploaded file.
  // Note: currently only 'fine-tune' is supported (as of 2023-01-20)
  purpose: FileUploadPurpose
)

sealed trait FileUploadPurpose extends EnumValue

object FileUploadPurpose {
  case object `fine-tune` extends FileUploadPurpose
  case object assistants extends FileUploadPurpose
  case object batch extends FileUploadPurpose
}
