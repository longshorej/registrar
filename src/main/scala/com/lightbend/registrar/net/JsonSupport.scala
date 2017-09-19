package com.lightbend.registrar.net

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.lightbend.registrar.RegistrationHandler
import spray.json.DefaultJsonProtocol

object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  import RegistrationHandler._

  implicit val recordFormat = jsonFormat4(Record.apply)
}
