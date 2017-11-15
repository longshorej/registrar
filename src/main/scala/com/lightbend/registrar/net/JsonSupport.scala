package com.lightbend.registrar.net

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.lightbend.registrar.RegistrationHandler
import spray.json.{ DefaultJsonProtocol, RootJsonFormat }

object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  import ControlProtocolRoute._
  import RegistrationHandler._

  implicit val recordFormat: RootJsonFormat[Record] = jsonFormat4(Record.apply)
  implicit val registrationFormat: RootJsonFormat[Registration] = jsonFormat2(Registration.apply)
  implicit val registrationRequestFormat: RootJsonFormat[RegistrationRequest] = jsonFormat1(RegistrationRequest.apply)
  implicit val refreshFormat: RootJsonFormat[RefreshResult] = jsonFormat3(RefreshResult.apply)
  implicit val refreshRequestFormat: RootJsonFormat[RefreshRequest] = jsonFormat1(RefreshRequest.apply)
  implicit val removeRequestFormat: RootJsonFormat[RemoveRequest] = jsonFormat2(RemoveRequest.apply)
}
