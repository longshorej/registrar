package com.lightbend.registrar

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import scala.io.StdIn

object Registrar {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("registrar")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    val settings = new Settings(system)

    val registrationHandlerRef = system.actorOf(RegistrationHandler.props, "registration-handler")

    val bindingFuture =
      Http().bindAndHandle(net.route(registrationHandlerRef, settings), settings.net.bindInterface, settings.net.bindPort)

    system.log.info(s"Listening on ${settings.net.bindInterface}:${settings.net.bindPort}")

    StdIn.readLine()

    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
