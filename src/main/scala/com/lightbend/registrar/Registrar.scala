package com.lightbend.registrar

import akka.actor.ActorSystem
import akka.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

object Registrar {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("registrar")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    implicit val settings = new Settings(system.settings)
    implicit val scheduler = system.scheduler

    val registrationHandlerRef = system.spawn(RegistrationHandler.behavior, "registration-handler")

    Http().bindAndHandle(
      net.ControlProtocolRoute(registrationHandlerRef),
      settings.net.bindInterface,
      settings.net.bindPort)

    system.log.info(s"Listening on ${settings.net.bindInterface}:${settings.net.bindPort}")
  }
}
