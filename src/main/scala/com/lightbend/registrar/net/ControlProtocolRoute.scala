package com.lightbend.registrar.net

import akka.actor.{ ActorSystem, Scheduler }
import akka.typed.ActorRef
import akka.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import com.lightbend.registrar.{ RegistrationHandler, Settings }

import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }

object ControlProtocolRoute {
  import Directives._
  import JsonSupport._
  import RegistrationHandler._

  /**
   * Control protocol encoding for registering. During the holding period, this will be rejected.
   * @param name
   */
  final case class RegistrationRequest(name: String)

  /**
   * Control protocol encoding for refreshing zero or more registrations. During the holding period, this will
   * open registration for their topic.
   */
  final case class RefreshRequest(registrations: Set[Registration])

  /**
   * Control protocol encoding for removing a member from the list.
   */
  final case class RemoveRequest(id: Int, name: String)

  def apply(registrationHandlerRef: ActorRef[Message])(implicit system: ActorSystem, ec: ExecutionContext, settings: Settings, scheduler: Scheduler) = {
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
          (post & path(Segment / "register") & entity(as[RegistrationRequest])) {
            case (topic, RegistrationRequest(name)) =>
              onSuccess(registrationHandlerRef ? (Register(topic, name, _: ActorRef[Option[Record]]))) {
                case None => complete(StatusCodes.BadRequest)
                case Some(r) => complete(r)
              }
          } ~
          (post & path(Segment / "refresh") & entity(as[RefreshRequest])) {
            case (topic, RefreshRequest(registrations)) =>
              complete(registrationHandlerRef ? (Refresh(topic, registrations, _: ActorRef[RefreshResult])))
          } ~
          (delete & path(Segment / "remove") & entity(as[RemoveRequest])) {
            case (topic, RemoveRequest(id, name)) =>
              onSuccess(registrationHandlerRef ? (Remove(topic, id, name, _: ActorRef[Boolean]))) { deleted =>
                complete(if (deleted) StatusCodes.OK else StatusCodes.BadRequest)
              }
          }
      }
  }
}
