package com.lightbend.registrar

import akka.actor._
import java.time.Instant
import scala.collection.immutable.Seq

object RegistrationHandler {
  final case object EnableRegistration

  final case class Record(id: Int, name: String, members: Seq[String], refreshInterval: Long)

  final case class LocalRegistration(id: Int, name: String, lastUpdated: Long) {
    val registration: Registration = Registration(id, name)
  }

  final case class Registration(id: Int, name: String)

  final case object InspectTopics

  final case class Inspect(topic: String)

  final case class Register(topic: String, name: String)

  final case class Refresh(topic: String, registrations: Set[Registration])

  final case class RefreshResult(accepted: Set[Registration], rejected: Set[Registration])

  final case class Remove(topic: String, id: Int, name: String)

  def props: Props = Props[RegistrationHandler]
}

final class RegistrationHandler extends Actor {
  import RegistrationHandler._

  private val settings = new Settings(context.system.settings)

  override def receive: Receive = handle(currentTime(), Map.empty)

  private def handle(startTime: Long, registrations: Map[String, Seq[LocalRegistration]]): Receive = {
    case EnableRegistration =>
      context.become(handle(0L, registrations))

    case InspectTopics =>
      val now = currentTime()

      sender() ! registrations.keySet.filter(topic => hasRegistrationForTopic(registrations, now, topic))

    case Inspect(topic) =>
      val now = currentTime()
      val rs = registrationsForTopic(registrations, now, topic)
      val names = rs.map(_.name)

      sender() ! rs.map(r => Record(r.id, r.name, names, settings.registration.refreshInterval.duration.toMillis))

    case Register(topic, name) =>
      val now = currentTime()
      val topicRegistrations = registrationsForTopic(registrations, now, topic)

      if (!allowRegistration(now, startTime) || topicRegistrations.exists(_.name == name)) {
        sender() ! None
      } else {
        val registration = LocalRegistration(topicRegistrations.lastOption.fold(1)(_.id + 1), name, now)

        val newTopicRegistrations = topicRegistrations :+ registration

        sender() ! Some(
          Record(
            registration.id,
            name,
            newTopicRegistrations.map(_.name),
            settings.registration.refreshInterval.duration.toMillis
          )
        )

        context.become(handle(startTime, updateRegistrations(registrations, topic, newTopicRegistrations)))
      }

    case Refresh(topic, names) =>
      val now = currentTime()
      val topicRegistrations = registrationsForTopic(registrations, now, topic)
      val topicRegistrationsSet = topicRegistrations.map(_.registration).toSet
      val allowReg = allowRegistration(now, startTime)

      val (accepted, rejected) =
        if (allowReg)
          names.partition(topicRegistrationsSet.contains)
        else
          names -> Set.empty[Registration]

      val rs =
        if (allowReg) {
            topicRegistrations.foldLeft(Seq.empty[LocalRegistration]) { case (entries, entry) =>
              if (accepted.contains(entry.registration))
                entries :+ LocalRegistration(entry.id, entry.name, now)
              else
                entries :+ entry
            }
        } else {
          accepted.foldLeft(topicRegistrations) { case (entries, entry) =>
            entries
              .filterNot(_.name == entry.name)
              .:+(LocalRegistration(entry.id, entry.name, now))
          }
        }

      sender() ! RefreshResult(accepted, rejected)

      context.become(handle(startTime, updateRegistrations(registrations, topic, rs)))

    case Remove(topic, id, name) =>
      val now = currentTime()
      val original = registrationsForTopic(registrations, now, topic)
      val modified = original.filterNot(e => e.name == name && e.id == id)

      sender() ! (original.length != modified.length)

      context.become(handle(startTime, updateRegistrations(registrations, topic, modified)))
  }

  private def allowRegistration(now: Long, startTime: Long) =
    now >= startTime + settings.registration.holdingPeriod.duration.toMillis

  private def currentTime(): Long =
    Instant.now.toEpochMilli

  private def expired(now: Long, value: Long) =
    now >= value + settings.registration.expireAfter.duration.toMillis

  private def hasRegistrationForTopic(registrations: Map[String, Seq[LocalRegistration]], now: Long, topic: String) =
    registrations.get(topic).fold(false)(_.exists(e => !expired(now, e.lastUpdated)))

  private def registrationsForTopic(registrations: Map[String, Seq[LocalRegistration]], now: Long, topic: String) =
    registrations
      .getOrElse(topic, Vector.empty)
      .filterNot(e => expired(now, e.lastUpdated))

  private def updateRegistrations(allRegistrations: Map[String, Seq[LocalRegistration]],
                                  topic: String,
                                  registrations: Seq[LocalRegistration]) =
    if (registrations.isEmpty)
      allRegistrations - topic
    else
      allRegistrations.updated(topic, registrations)
}
