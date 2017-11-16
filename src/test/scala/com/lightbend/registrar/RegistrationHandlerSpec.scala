package com.lightbend.registrar

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import akka.typed.scaladsl.adapter._
import com.lightbend.registrar.RegistrationHandler.Record
import com.typesafe.config.ConfigFactory
import java.util.UUID
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import scala.collection.immutable.Seq

object RegistrationHandlerSpec {
  def config = ConfigFactory
    .parseString(
      s"""|registrar {
          |  registration.expire-after = 250ms
          |  registration.holding-period = 250ms
          |}
          |""".stripMargin)
    .withFallback(ConfigFactory.defaultApplication())
}

class RegistrationHandlerSpec extends TestKit(ActorSystem("registrar", RegistrationHandlerSpec.config))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  implicit val settings = new Settings(system.settings)

  val idOne = new UUID(0, 1)
  val idTwo = new UUID(0, 2)
  val idThree = new UUID(0, 3)

  "RegistrationHandler" must {
    "accept registrations (same topic, same name, same id)" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", idOne, "one", self)

      expectMsg(Some(RegistrationHandler.Record(idOne, "one", Seq("one"), 10000L, 250L)))

      handler ! RegistrationHandler.Register("test", idOne, "one", self)

      expectMsg(Some(RegistrationHandler.Record(idOne, "one", Seq("one"), 10000L, 250L)))
    }

    "reject registrations (same topic, same name, different id)" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", idOne, "one", self)

      expectMsg(Some(RegistrationHandler.Record(idOne, "one", Seq("one"), 10000L, 250L)))

      handler ! RegistrationHandler.Register("test", idTwo, "one", self)

      expectMsg(None)
    }

    "allow registrations (different topic, same name)" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", idOne, "one", self)

      expectMsg(Some(RegistrationHandler.Record(idOne, "one", Seq("one"), 10000L, 250L)))

      handler ! RegistrationHandler.Register("test2", idTwo, "one", self)

      expectMsg(Some(RegistrationHandler.Record(idTwo, "one", Seq("one"), 10000L, 250L)))

      handler ! RegistrationHandler.Inspect("test", self)

      expectMsg(Vector(Record(idOne, "one", Seq("one"), 10000L, 250L)))

      handler ! RegistrationHandler.Inspect("test2", self)

      expectMsg(Vector(Record(idTwo, "one", Seq("one"), 10000L, 250L)))
    }

    "reject registrations during holding period" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.Register("test", idOne, "one", self)

      expectMsg(None)
    }

    "allow registrations after holding period has passed" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.Register("test", idOne, "one", self)

      expectMsg(None)

      Thread.sleep(500)

      handler ! RegistrationHandler.Register("test", idOne, "one", self)

      expectMsg(Some(RegistrationHandler.Record(idOne, "one", Seq("one"), 10000L, 250L)))
    }

    "allow registrations for a topic after that topic has been refreshed, but reject unknown topics" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.Refresh("test1", Set(RegistrationHandler.Registration(idOne, "one")), self)

      expectMsg(RegistrationHandler.RefreshResult(Set(RegistrationHandler.Registration(idOne, "one")), Set.empty, 10000L, 250L))

      handler ! RegistrationHandler.Register("test1", idTwo, "two", self)

      expectMsg(Some(RegistrationHandler.Record(idTwo, "two", Seq("one", "two"), 10000L, 250L)))

      handler ! RegistrationHandler.Register("test2", idTwo, "one", self)

      expectMsg(None)
    }

    "exclude expired values" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", idOne, "one", self)

      expectMsg(Some(RegistrationHandler.Record(idOne, "one", Seq("one"), 10000L, 250L)))

      Thread.sleep(500)

      handler ! RegistrationHandler.Inspect("test", self)

      expectMsg(Vector.empty)

      handler ! RegistrationHandler.Register("test", idOne, "two", self)

      expectMsg(Some(RegistrationHandler.Record(idOne, "two", Seq("two"), 10000L, 250L)))
    }

    "allow refresh during holding period" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.Refresh("test", Set(RegistrationHandler.Registration(idOne, "one")), self)

      expectMsg(RegistrationHandler.RefreshResult(Set(RegistrationHandler.Registration(idOne, "one")), Set.empty, 10000L, 250L))
    }

    "allow refresh of some and rejection of others" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", idOne, "one", self)

      expectMsg(Some(RegistrationHandler.Record(idOne, "one", Seq("one"), 10000L, 250L)))

      handler ! RegistrationHandler.Refresh(
        "test",
        Set(RegistrationHandler.Registration(idOne, "one"), RegistrationHandler.Registration(idTwo, "two")),
        self)

      expectMsg(
        RegistrationHandler.RefreshResult(
          Set(RegistrationHandler.Registration(idOne, "one")),
          Set(RegistrationHandler.Registration(idTwo, "two")),
          10000L,
          250L))
    }

    "reject refresh after holding period has passed" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      Thread.sleep(500)

      handler ! RegistrationHandler.Refresh("test", Set(RegistrationHandler.Registration(idOne, "one")), self)

      expectMsg(RegistrationHandler.RefreshResult(Set.empty, Set(RegistrationHandler.Registration(idOne, "one")), 10000L, 250L))
    }

    "remove items" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", idOne, "one", self)

      expectMsg(Some(RegistrationHandler.Record(idOne, "one", Seq("one"), 10000L, 250L)))

      handler ! RegistrationHandler.Remove("test", idOne, "one", self)

      expectMsg(true)

      handler ! RegistrationHandler.Remove("test", idOne, "one", self)

      expectMsg(false)
    }

    "support inspecting of available topics" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test1", idOne, "one", self)

      expectMsg(Some(RegistrationHandler.Record(idOne, "one", Seq("one"), 10000L, 250L)))

      handler ! RegistrationHandler.Register("test2", idOne, "one", self)

      expectMsg(Some(RegistrationHandler.Record(idOne, "one", Seq("one"), 10000L, 250L)))

      handler ! RegistrationHandler.InspectTopics(self)

      expectMsg(Set("test1", "test2"))

      Thread.sleep(500)

      handler ! RegistrationHandler.InspectTopics(self)

      expectMsg(Set.empty[String])
    }
  }
}