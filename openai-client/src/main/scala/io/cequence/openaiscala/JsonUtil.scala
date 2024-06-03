//package io.cequence.openaiscala
//
//import play.api.libs.json.{Format, _}
//
//import java.util.Date
//import java.{util => ju}
//
//object JsonUtil {
//
//  implicit class JsonOps(val json: JsValue) {
//    def asSafe[T](
//      implicit fjs: Reads[T]
//    ): T =
//      try {
//        json.validate[T] match {
//          case JsSuccess(value, _) => value
//          case JsError(errors) =>
//            val errorString = errors.map { case (path, pathErrors) =>
//              s"JSON at path '${path}' contains the following errors: ${pathErrors.map(_.message).mkString(";")}"
//            }.mkString("\n")
//            throw new OpenAIScalaClientException(
//              s"Unexpected JSON:\n'${Json.prettyPrint(json)}'. Cannot be parsed due to: $errorString"
//            )
//        }
//      } catch {
//        case e: Exception =>
//          throw new OpenAIScalaClientException(
//            s"Error thrown while processing a JSON '${Json.prettyPrint(json)}'. Cause: ${e.getMessage}"
//          )
//      }
//
//    def asSafeArray[T](
//      implicit fjs: Reads[T]
//    ): Seq[T] =
//      json.asSafe[JsArray].value.map(_.asSafe[T]).toSeq
//  }
//
//  object SecDateFormat extends Format[ju.Date] {
//    override def reads(json: JsValue): JsResult[Date] = {
//      json match {
//        case JsString(s) =>
//          try {
//            val millis = s.toLong * 1000
//            JsSuccess(new ju.Date(millis))
//          } catch {
//            case _: NumberFormatException => JsError(s"$s is not a number.")
//          }
//
//        case JsNumber(n) =>
//          val millis = (n * 1000).toLong
//          JsSuccess(new ju.Date(millis))
//
//        case _ => JsError(s"String or number expected but got '$json'.")
//      }
//    }
//
//    override def writes(o: Date): JsValue =
//      JsNumber(Math.round(o.getTime.toDouble / 1000))
//  }
//
//  def toJson(value: Any): JsValue =
//    if (value == null)
//      JsNull
//    else
//      value match {
//        case x: JsValue    => x // nothing to do
//        case x: String     => JsString(x)
//        case x: BigDecimal => JsNumber(x)
//        case x: Integer    => JsNumber(BigDecimal.valueOf(x.toLong))
//        case x: Long       => JsNumber(BigDecimal.valueOf(x))
//        case x: Double     => JsNumber(BigDecimal.valueOf(x))
//        case x: Float      => JsNumber(BigDecimal.valueOf(x.toDouble))
//        case x: Boolean    => JsBoolean(x)
//        case x: ju.Date    => Json.toJson(x)
//        case x: Option[_]  => x.map(toJson).getOrElse(JsNull)
//        case x: Array[_]   => JsArray(x.map(toJson))
//        case x: Seq[_]     => JsArray(x.map(toJson))
//        case x: Map[String, _] =>
//          val jsonValues = x.map { case (fieldName, value) =>
//            (fieldName, toJson(value))
//          }
//          JsObject(jsonValues)
//        case _ =>
//          throw new IllegalArgumentException(
//            s"No JSON formatter found for the class ${value.getClass.getName}."
//          )
//      }
//
//  object StringDoubleMapFormat extends Format[Map[String, Double]] {
//    override def reads(json: JsValue): JsResult[Map[String, Double]] = {
//      val resultJsons =
//        json.asSafe[JsObject].fields.map { case (fieldName, jsValue) =>
//          (fieldName, jsValue.as[Double])
//        }
//      JsSuccess(resultJsons.toMap)
//    }
//
//    override def writes(o: Map[String, Double]): JsValue = {
//      val fields = o.map { case (fieldName, value) =>
//        (fieldName, JsNumber(value))
//      }
//      JsObject(fields)
//    }
//  }
//
//  object StringStringMapFormat extends Format[Map[String, String]] {
//    override def reads(json: JsValue): JsResult[Map[String, String]] = {
//      val resultJsons =
//        json.asSafe[JsObject].fields.map { case (fieldName, jsValue) =>
//          (fieldName, jsValue.as[String])
//        }
//      JsSuccess(resultJsons.toMap)
//    }
//
//    override def writes(o: Map[String, String]): JsValue = {
//      val fields = o.map { case (fieldName, value) =>
//        (fieldName, JsString(value))
//      }
//      JsObject(fields)
//    }
//  }
//
//  object StringAnyMapFormat extends Format[Map[String, Any]] {
//    override def reads(json: JsValue): JsResult[Map[String, Any]] = {
//      val resultJsons =
//        json.asSafe[JsObject].fields.map { case (fieldName, jsValue) =>
//          (fieldName, jsValue.toString)
//        }
//      JsSuccess(resultJsons.toMap)
//    }
//
//    override def writes(o: Map[String, Any]): JsValue = {
//      val fields = o.map { case (fieldName, value) =>
//        (fieldName, toJson(value))
//      }
//      JsObject(fields)
//    }
//  }
//
//  import play.api.libs.json._
//
//  private class EitherFormat[L, R](
//    leftFormat: Format[L],
//    rightFormat: Format[R]
//  ) extends Format[Either[L, R]] {
//
//    override def reads(json: JsValue): JsResult[Either[L, R]] = {
//      val left = leftFormat.reads(json)
//      val right = rightFormat.reads(json)
//
//      if (left.isSuccess) {
//        left.map(Left(_))
//      } else if (right.isSuccess) {
//        right.map(Right(_))
//      } else {
//        JsError(s"Unable to read Either type from JSON $json")
//      }
//    }
//
//    override def writes(o: Either[L, R]): JsValue =
//      o match {
//        case Left(value)  => leftFormat.writes(value)
//        case Right(value) => rightFormat.writes(value)
//      }
//  }
//
//  def eitherFormat[L: Format, R: Format]: Format[Either[L, R]] = {
//    val leftFormat = implicitly[Format[L]]
//    val rightFormat = implicitly[Format[R]]
//
//    new EitherFormat[L, R](leftFormat, rightFormat)
//  }
//
//  def enumFormat[T <: EnumValue](values: T*): Format[T] = {
//    val valueMap = values.map(v => v.toString -> v).toMap
//
//    val reads: Reads[T] = Reads {
//      case JsString(value) =>
//        valueMap.get(value) match {
//          case Some(v) => JsSuccess(v)
//          case None    => JsError(s"$value is not a valid enum value.")
//        }
//      case _ => JsError("String value expected")
//    }
//
//    val writes: Writes[T] = Writes { (v: T) =>
//      JsString(v.toString)
//    }
//
//    Format(reads, writes)
//  }
//}
