package io.cequence.openaiscala.domain

abstract class EnumValue(value: String = "") {

  override def toString: String =
    if (value.nonEmpty) value else getClass.getSimpleName.stripSuffix("$")
}
