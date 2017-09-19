package com.lightbend.registrar.net

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.ask
import akka.http.scaladsl.server._
import com.lightbend.registrar.{ RegistrationHandler, Settings }
import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext

object ControlProtocolRoute {
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
      (get & pathEnd) {
        complete((registrationHandlerRef ? InspectTopics).mapTo[Set[String]])
      } ~
      (get & path(Segment)) { topic =>
        complete((registrationHandlerRef ? Inspect(topic)).mapTo[Seq[Record]])
      } ~
      (post & path(Segment) & entity(as[String])) { case (topic, name) =>
        onSuccess((registrationHandlerRef ? Register(topic, name)).mapTo[Option[Record]]) {
          case None    => complete(StatusCodes.BadRequest)
          case Some(r) => complete(r)
        }
      } ~
      (post & path(Segment / IntNumber / Segment)) { case (topic, id, name) =>
        onSuccess((registrationHandlerRef ? Refresh(topic, id, name)).mapTo[Boolean]) { updated =>
          complete(if (updated) StatusCodes.OK else StatusCodes.BadRequest)
        }
      } ~
      (delete & path(Segment / IntNumber / Segment)) { case (topic, id, name) =>
        onSuccess((registrationHandlerRef ? Remove(topic, id, name)).mapTo[Boolean]) { deleted =>
          complete(if (deleted) StatusCodes.OK else StatusCodes.BadRequest)
        }
      }
    }
  }
}
