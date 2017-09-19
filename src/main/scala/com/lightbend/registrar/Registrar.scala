package com.lightbend.registrar

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

object Registrar {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("registrar")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    val settings = new Settings(system.settings)

    val registrationHandlerRef = system.actorOf(RegistrationHandler.props, "registration-handler")

    Http().bindAndHandle(
      net.ControlProtocolRoute(registrationHandlerRef, settings),
      settings.net.bindInterface,
      settings.net.bindPort)

    system.log.info(s"Listening on ${settings.net.bindInterface}:${settings.net.bindPort}")
  }
}
