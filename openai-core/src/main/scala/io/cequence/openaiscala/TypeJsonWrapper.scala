package io.cequence.openaiscala

import io.cequence.openaiscala.domain.HasType
import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsSuccess, JsValue, Json, OFormat}

private class TypeJsonWrapper[T <: HasType](val format: OFormat[T]) extends OFormat[T] {
  override def reads(json: JsValue): JsResult[T] = {
    // First read using the underlying format
    format.reads(json).flatMap { obj =>
      // Validate that the type field in JSON matches the object's type
      (json \ "type").asOpt[String] match {
        case Some(jsonType) if jsonType == obj.`type`.toString =>
          JsSuccess(obj)
        case Some(jsonType) =>
          JsError(s"Type mismatch: expected ${obj.`type`.toString}, got $jsonType")
        case None =>
          JsError("Missing type field")
      }
    }
  }

  override def writes(o: T): JsObject = {
    // Write using the underlying format, then add type field
    val json = format.writes(o)
    json ++ Json.obj("type" -> o.`type`.toString)
  }
}

object TypeJsonWrapper {
  def apply[T <: HasType](format: OFormat[T]): OFormat[T] = new TypeJsonWrapper(format)
}
