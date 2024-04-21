package io.cequence.openaiscala.domain

// TODO: common model for v1 and v2
trait EnumValue {

  def value: String = ""

  override def toString: String =
    if (value.nonEmpty) value else getClass.getSimpleName.stripSuffix("$")
}

abstract class NamedEnumValue(override val value: String = "") extends EnumValue
