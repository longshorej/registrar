package com.lightbend.registrar

import akka.typed._
import akka.typed.scaladsl._
import java.time.Instant
import scala.collection.immutable.Seq

object RegistrationHandler {
  sealed trait Message

  final case object EnableRegistration extends Message

  final case class Record(id: Int, name: String, members: Seq[String], refreshInterval: Long, expireAfter: Long)

  final case class LocalRegistration(id: Int, name: String, lastUpdated: Long) {
    val registration: Registration = Registration(id, name)
  }

  final case class Registration(id: Int, name: String)

  final case class InspectTopics(replyTo: ActorRef[Set[String]]) extends Message

  final case class Inspect(topic: String, replyTo: ActorRef[Seq[Record]]) extends Message

  final case class Register(topic: String, name: String, replyTo: ActorRef[Option[Record]]) extends Message

  final case class Refresh(topic: String, registrations: Set[Registration], replyTo: ActorRef[RefreshResult]) extends Message

  final case class RefreshResult(accepted: Set[Registration], rejected: Set[Registration], refreshInterval: Long, expireAfter: Long)

  final case class Remove(topic: String, id: Int, name: String, replyTo: ActorRef[Boolean]) extends Message

  def behavior(implicit settings: Settings): Behavior[Message] = handle(currentTime(), Map.empty)

  /**
   * Determines if we can register for the given `topic`. We allow registrations if we're not in the holding
   * period, or if we have a record of any registrations for that topic (acquired via refresh)
   */
  private def allowRegistration(
    topic: String,
    registrations: Map[String, Seq[LocalRegistration]],
    now: Long,
    startTime: Long)(implicit settings: Settings) =
    !inHoldingPeriod(now, startTime) || registrationsForTopic(registrations, now, topic).nonEmpty

  /**
   * When we first start up, we are in a holding period for a configurable amount of settings. During this time
   * only refresh requests will be allowed (so as to rebuild state if rescheduled). However, once we become aware
   * of a topic via refresh, we then allow registration.
   */
  private def inHoldingPeriod(now: Long, startTime: Long)(implicit settings: Settings) =
    now < startTime + settings.registration.holdingPeriod.duration.toMillis

  private def currentTime(): Long =
    Instant.now.toEpochMilli

  private def expired(now: Long, value: Long)(implicit settings: Settings) =
    now >= value + settings.registration.expireAfter.duration.toMillis

  private def hasRegistrationForTopic(registrations: Map[String, Seq[LocalRegistration]], now: Long, topic: String)(implicit settings: Settings) =
    registrations.get(topic).fold(false)(_.exists(e => !expired(now, e.lastUpdated)))

  private def registrationsForTopic(registrations: Map[String, Seq[LocalRegistration]], now: Long, topic: String)(implicit settings: Settings) =
    registrations
      .getOrElse(topic, Vector.empty)
      .filterNot(e => expired(now, e.lastUpdated))

  private def updateRegistrations(
    allRegistrations: Map[String, Seq[LocalRegistration]],
    topic: String,
    registrations: Seq[LocalRegistration]) =
    if (registrations.isEmpty)
      allRegistrations - topic
    else
      allRegistrations.updated(topic, registrations)

  private def handle(startTime: Long, registrations: Map[String, Seq[LocalRegistration]])(implicit settings: Settings): Behavior[Message] =
    Actor.immutable[Message] { (_, message) =>
      message match {
        case EnableRegistration =>
          handle(0L, registrations)

        case InspectTopics(replyTo) =>
          val now = currentTime()
          replyTo ! registrations.keySet.filter(topic => hasRegistrationForTopic(registrations, now, topic))
          Actor.same

        case Inspect(topic, replyTo) =>
          val now = currentTime()
          val rs = registrationsForTopic(registrations, now, topic)
          val names = rs.map(_.name)

          replyTo ! rs
            .map(r =>
              Record(
                r.id,
                r.name,
                names,
                settings.registration.refreshInterval.duration.toMillis,
                settings.registration.expireAfter.duration.toMillis))

          Actor.same

        case Register(topic, name, replyTo) =>
          val now = currentTime()
          val topicRegistrations = registrationsForTopic(registrations, now, topic)

          if (!allowRegistration(topic, registrations, now, startTime) || topicRegistrations.exists(_.name == name)) {
            replyTo ! None

            Actor.same
          } else {
            val registration = LocalRegistration(topicRegistrations.lastOption.fold(1)(_.id + 1), name, now)

            val newTopicRegistrations = topicRegistrations :+ registration

            replyTo ! Some(
              Record(
                registration.id,
                name,
                newTopicRegistrations.map(_.name),
                settings.registration.refreshInterval.duration.toMillis,
                settings.registration.expireAfter.duration.toMillis))

            handle(startTime, updateRegistrations(registrations, topic, newTopicRegistrations))
          }

        case Refresh(topic, names, replyTo) =>
          val now = currentTime()
          val topicRegistrations = registrationsForTopic(registrations, now, topic)
          val topicRegistrationsSet = topicRegistrations.map(_.registration).toSet
          val inHolding = inHoldingPeriod(now, startTime)

          val (accepted, rejected) =
            if (!inHolding)
              names.partition(topicRegistrationsSet.contains)
            else
              names -> Set.empty[Registration]

          val rs =
            if (!inHolding) {
              topicRegistrations.foldLeft(Seq.empty[LocalRegistration]) {
                case (entries, entry) =>
                  if (accepted.contains(entry.registration))
                    entries :+ LocalRegistration(entry.id, entry.name, now)
                  else
                    entries :+ entry
              }
            } else {
              accepted.foldLeft(topicRegistrations) {
                case (entries, entry) =>
                  entries
                    .filterNot(_.name == entry.name)
                    .:+(LocalRegistration(entry.id, entry.name, now))
              }
            }

          replyTo ! RefreshResult(
            accepted,
            rejected,
            settings.registration.refreshInterval.duration.toMillis,
            settings.registration.expireAfter.duration.toMillis)

          handle(startTime, updateRegistrations(registrations, topic, rs))

        case Remove(topic, id, name, replyTo) =>
          val now = currentTime()
          val original = registrationsForTopic(registrations, now, topic)
          val modified = original.filterNot(e => e.name == name && (id == 0 || e.id == id))

          replyTo ! (original.length != modified.length)

          handle(startTime, updateRegistrations(registrations, topic, modified))
      }
    }
}
