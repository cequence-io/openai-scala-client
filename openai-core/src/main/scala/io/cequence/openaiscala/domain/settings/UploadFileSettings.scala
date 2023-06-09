package io.cequence.openaiscala.domain.settings

case class UploadFileSettings(
    // The intended purpose of the uploaded documents. Use "fine-tune" for Fine-tuning.
    // This allows us to validate the format of the uploaded file.
    // Note: currently only 'fine-tune' is supported (as of 2023-01-20)
    purpose: String
)
