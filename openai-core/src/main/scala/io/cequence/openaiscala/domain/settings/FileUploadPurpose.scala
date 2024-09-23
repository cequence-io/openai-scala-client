package io.cequence.openaiscala.domain.settings

import io.cequence.wsclient.domain.NamedEnumValue

sealed abstract class FileUploadPurpose(value: String) extends NamedEnumValue(value)

object FileUploadPurpose {
  case object `fine-tune` extends FileUploadPurpose("fine-tune")
  case object assistants extends FileUploadPurpose("assistants")
  case object batch extends FileUploadPurpose("batch")
}
