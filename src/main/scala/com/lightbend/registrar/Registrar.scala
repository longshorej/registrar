package com.lightbend.registrar

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import scala.concurrent.duration._
import scala.io.StdIn

object Registrar {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("registrar")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    implicit val timeout = Timeout(5.seconds) // @TODO config

    val registrationHandlerRef = system.actorOf(RegistrationHandler.props, "registration-handler")

    val bindingFuture = Http().bindAndHandle(net.route(registrationHandlerRef), "0.0.0.0", 8080) // @TODO config


    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
