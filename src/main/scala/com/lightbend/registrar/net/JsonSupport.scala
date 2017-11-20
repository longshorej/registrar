package com.lightbend.registrar.net

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.lightbend.registrar.RegistrationHandler
import java.util.UUID
import spray.json.{ DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat, deserializationError }

object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  import ControlProtocolRoute._
  import RegistrationHandler._

  implicit val uuidFormat: JsonFormat[UUID] =
    new JsonFormat[UUID] {
      def write(x: UUID): JsValue =
        JsString(x.toString)

      def read(value: JsValue): UUID =
        value match {
          case JsString(string) =>
            UUID.fromString(string)
          case v =>
            deserializationError(s"Unable to parse as UUID: $v")
        }
    }

  implicit val recordFormat: RootJsonFormat[Record] = jsonFormat5(Record.apply)
  implicit val registrationFormat: RootJsonFormat[Registration] = jsonFormat2(Registration.apply)
  implicit val registrationRequestFormat: RootJsonFormat[RegistrationRequest] = jsonFormat2(RegistrationRequest.apply)
  implicit val refreshFormat: RootJsonFormat[RefreshResult] = jsonFormat4(RefreshResult.apply)
  implicit val refreshRequestFormat: RootJsonFormat[RefreshRequest] = jsonFormat1(RefreshRequest.apply)
  implicit val removeRequestFormat: RootJsonFormat[RemoveRequest] = jsonFormat2(RemoveRequest.apply)
}
