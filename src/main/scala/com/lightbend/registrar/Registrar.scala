package com.lightbend.registrar

import akka.actor.ActorSystem
import akka.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.lightbend.rp.common.SocketBinding

object Registrar {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("registrar")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    implicit val settings = new Settings(system.settings)
    implicit val scheduler = system.scheduler

    val registrationHandlerRef = system.spawn(RegistrationHandler.behavior, "registration-handler")
    val host = SocketBinding.bindHost("http", settings.net.bindInterface)
    val port = SocketBinding.bindPort("http", settings.net.bindPort)
    val route = net.ControlProtocolRoute(registrationHandlerRef)

    Http().bindAndHandle(route, host, port)

    system.log.info(s"Listening on $host:$port")
  }
}
