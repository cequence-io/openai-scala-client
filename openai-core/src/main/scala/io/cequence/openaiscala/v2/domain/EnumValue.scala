package io.cequence.openaiscala.v2.domain

trait EnumValue {

  def value: String = ""

  override def toString: String =
    if (value.nonEmpty) value else getClass.getSimpleName.stripSuffix("$")
}

abstract class NamedEnumValue(override val value: String = "") extends EnumValue
