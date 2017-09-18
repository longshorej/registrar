package com.lightbend.registrar

import akka.actor._
import java.time.Instant
import scala.collection.immutable.Seq

object RegistrationHandler {
  final case class Record(id: Int, name: String, members: Seq[String])

  final case class LocalRegistration(id: Int, name: String, lastUpdated: Long)

  final case class Inspect(topic: String)

  final case class Register(topic: String, name: String)

  final case class Refresh(topic: String, id: Int, name: String)

  final case class Remove(topic: String, id: Int, name: String)
}

final class RegistrationHandler extends Actor {
  import RegistrationHandler._

  override def receive: Receive = handle(currentTime(), Map.empty)

  private def handle(startTime: Long, registrations: Map[String, Seq[LocalRegistration]]): Receive = {
    val now = currentTime()

    val allowRegistration = allow(now, startTime)

    def registrationsForTopic(topic: String) =
      registrations
        .getOrElse(topic, Vector.empty)
        .filterNot(e => expired(now, e.lastUpdated))

    {
      case Inspect(topic) =>
        val rs = registrations.getOrElse(topic, Vector.empty)
        val names = rs.map(_.name)

        sender() ! rs.map(r => Record(r.id, r.name, names))

        context.become(handle(startTime, registrations))

      case Register(topic, name) =>
        val topicRegistrations = registrationsForTopic(topic)

        if (!allowRegistration || topicRegistrations.exists(_.name == name)) {
          sender() ! None
        } else {
          val registration = LocalRegistration(topicRegistrations.lastOption.fold(1)(_.id + 1), name, now)

          val newTopicRegistrations = topicRegistrations :+ registration

          sender() ! Some(Record(registration.id, name, newTopicRegistrations.map(_.name)))

          context.become(handle(startTime, registrations.updated(topic, newTopicRegistrations)))
        }

      case Refresh(topic, id, name) =>
        val topicRegistrations = registrationsForTopic(topic)

        val (updated, rs) =
          topicRegistrations.foldLeft((false, Seq.empty[LocalRegistration])) {
            case ((false, entries), entry) if entry.name == name && entry.id == id =>
              true -> (entries :+ LocalRegistration(id, name, now))

            case ((found, entries), entry) =>
              found -> (entries :+ entry)
          }

        val (updatedOrRegistered, updatedTopicRegistrations) =
          if (allowRegistration || updated)
            updated -> rs
          else
            true -> (rs :+ LocalRegistration(id, name, now)).sortBy(_.id)

        sender() ! updatedOrRegistered

        context.become(handle(startTime, registrations.updated(topic, updatedTopicRegistrations)))

      case Remove(topic, id, name) =>
        val original = registrationsForTopic(topic)
        val now = registrationsForTopic(topic).filterNot(e => e.name == name && e.id == id)

        sender() ! (original.length != now.length)

        context.become(handle(startTime, registrations.updated(topic, now)))
    }
  }

  private def allow(now: Long, startTime: Long) = now >= startTime + 0L // @TODO config
  private def currentTime(): Long = Instant.now.toEpochMilli
  private def expired(now: Long, value: Long) = now >= value + 60000L // @TODO config
}