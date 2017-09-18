package com.lightbend.registrar.net

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.ask
import akka.http.scaladsl.server._
import akka.util.Timeout
import com.lightbend.registrar.RegistrationHandler
import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext

object route {
  import Directives._
  import JsonSupport._
  import RegistrationHandler._

  def apply(registrationHandlerRef: ActorRef)
           (implicit ec: ExecutionContext, timeout: Timeout) =
    path("ping") {
      complete("pong!")
    } ~
    pathPrefix("topics") {
      (post & path(Segment) & entity(as[String])) { case (topic, name) =>
        complete(
          (registrationHandlerRef ? Register(topic, name)).mapTo[Option[Record]]
        )
      } ~
      (get & path(Segment)) { topic =>
        complete(
          (registrationHandlerRef ? Inspect(topic)).mapTo[Seq[Record]]
        )
      } ~
      (post & path(Segment / IntNumber / Segment)) { case (topic, id, name) =>
        onSuccess((registrationHandlerRef ? Refresh(topic, id, name)).mapTo[Boolean]) { updated =>
          if (updated)
            complete(StatusCodes.OK)
          else
            complete(StatusCodes.NotFound)
        }
      } ~
      (delete & path(Segment / IntNumber / Segment)) { case (topic, id, name) =>
        onSuccess((registrationHandlerRef ? Remove(topic, id, name)).mapTo[Boolean]) { deleted =>
          if (deleted)
            complete(StatusCodes.OK)
          else
            complete(StatusCodes.NotFound)
        }
      }
  }
}
