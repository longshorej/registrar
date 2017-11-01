package com.lightbend.registrar.net

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.lightbend.registrar.RegistrationHandler
import spray.json.{ DefaultJsonProtocol, RootJsonFormat }

object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  import RegistrationHandler._

  implicit val recordFormat: RootJsonFormat[Record] = jsonFormat4(Record.apply)
  implicit val registrationFormat: RootJsonFormat[Registration] = jsonFormat2(Registration.apply)
  implicit val refreshFormat: RootJsonFormat[RefreshResult] = jsonFormat2(RefreshResult.apply)
}
