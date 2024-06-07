package io.cequence.openaiscala.domain.settings

import io.cequence.wsclient.domain.{EnumValue, NamedEnumValue}

case class UploadFileSettings(
  // The intended purpose of the uploaded documents. Use "fine-tune" for Fine-tuning.
  // This allows us to validate the format of the uploaded file.
  // Note: currently only 'fine-tune' is supported (as of 2023-01-20)
  purpose: FileUploadPurpose
)

sealed abstract class FileUploadPurpose(value: String) extends NamedEnumValue(value)

object FileUploadPurpose {
  case object `fine-tune` extends FileUploadPurpose("fine-tune")
  case object assistants extends FileUploadPurpose("assistants")
  case object batch extends FileUploadPurpose("batch")
}
