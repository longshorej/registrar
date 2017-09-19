package com.lightbend.registrar.net

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.ask
import akka.http.scaladsl.server._
import com.lightbend.registrar.{RegistrationHandler, Settings}
import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext

object route {
  import Directives._
  import JsonSupport._
  import RegistrationHandler._

  def apply(registrationHandlerRef: ActorRef, settings: Settings)
           (implicit ec: ExecutionContext) = {
    import settings.net.askTimeout

    path("ping") {
      complete("pong!")
    } ~
    pathPrefix("topics") {
      (post & path(Segment) & entity(as[String])) { case (topic, name) =>
        complete((registrationHandlerRef ? Register(topic, name)).mapTo[Option[Record]])
      } ~
      (get & path(Segment)) { topic =>
        complete((registrationHandlerRef ? Inspect(topic)).mapTo[Seq[Record]])
      } ~
      (post & path(Segment / IntNumber / Segment)) { case (topic, id, name) =>
        onSuccess((registrationHandlerRef ? Refresh(topic, id, name)).mapTo[Boolean]) { updated =>
          complete(if (updated) StatusCodes.OK else StatusCodes.NotFound)
        }
      } ~
      (delete & path(Segment / IntNumber / Segment)) { case (topic, id, name) =>
        onSuccess((registrationHandlerRef ? Remove(topic, id, name)).mapTo[Boolean]) { deleted =>
          complete(if (deleted) StatusCodes.OK else StatusCodes.NotFound)
        }
      }
    }
  }
}
