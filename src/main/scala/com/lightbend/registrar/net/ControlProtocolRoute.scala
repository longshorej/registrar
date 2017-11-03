package com.lightbend.registrar.net

import akka.actor.Scheduler
import akka.typed.ActorRef
import akka.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import com.lightbend.registrar.{RegistrationHandler, Settings}

import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }

object ControlProtocolRoute {
  import Directives._
  import JsonSupport._
  import RegistrationHandler._

  def apply(registrationHandlerRef: ActorRef[Message])
           (implicit ec: ExecutionContext, settings: Settings, scheduler: Scheduler) = {
    import settings.net.askTimeout

    path("ping") {
      complete("pong!")
    } ~
    pathPrefix("topics") {
      (get & pathEnd) {
        complete(registrationHandlerRef ? (InspectTopics(_: ActorRef[Set[String]])))
      } ~
      (get & path(Segment)) { topic =>
        complete(registrationHandlerRef ? (Inspect(topic, _)): Future[Seq[RegistrationHandler.Record]])
      } ~
      (post & path(Segment / "register") & entity(as[String])) { case (topic, name) =>
        onSuccess(registrationHandlerRef ? (Register(topic, name, _: ActorRef[Option[Record]]))) {
          case None    => complete(StatusCodes.BadRequest)
          case Some(r) => complete(r)
        }
      } ~
      (post & path(Segment / "refresh") & entity(as[Set[Registration]])) { case (topic, registrations) =>
        complete(registrationHandlerRef ? (Refresh(topic, registrations, _: ActorRef[RefreshResult])))
      } ~
      (delete & path(Segment / IntNumber / Segment)) { case (topic, id, name) =>
        onSuccess(registrationHandlerRef ? (Remove(topic, id, name, _: ActorRef[Boolean]))) { deleted =>
          complete(if (deleted) StatusCodes.OK else StatusCodes.BadRequest)
        }
      }
    }
  }
}
