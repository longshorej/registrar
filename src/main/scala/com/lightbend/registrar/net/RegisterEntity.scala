package com.lightbend.registrar.net

import spray.json.DefaultJsonProtocol._

object RegisterEntity {
  implicit val registerEntityFormat = jsonFormat2(RegisterEntity.apply)
}

case class RegisterEntity(topic: String, name: String)
