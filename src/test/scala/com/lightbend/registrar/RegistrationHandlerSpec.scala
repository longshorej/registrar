package com.lightbend.registrar

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import akka.typed.scaladsl.adapter._
import com.lightbend.registrar.RegistrationHandler.Record
import com.typesafe.config.ConfigFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import scala.collection.immutable.Seq


object RegistrationHandlerSpec {
  def config = ConfigFactory
    .parseString(
      s"""|registrar {
          |  registration.expire-after = 250ms
          |  registration.holding-period = 250ms
          |}
          |""".stripMargin
    )
    .withFallback(ConfigFactory.defaultApplication())
}

class RegistrationHandlerSpec() extends TestKit(ActorSystem("registrar", RegistrationHandlerSpec.config))
                                with ImplicitSender
                                with WordSpecLike
                                with Matchers
                                with BeforeAndAfterAll {
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  implicit val settings = new Settings(system.settings)

  "RegistrationHandler" must {
    "reject registrations (same topic, same name)" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", "one", self)

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.Register("test", "one", self)

      expectMsg(None)
    }

    "allow registrations (different topic, same name)" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", "one", self)

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.Register("test2", "one", self)

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.Inspect("test2", self)

      expectMsg(Vector(Record(1, "one", Seq("one"), 10000L)))
    }

    "reject registrations during holding period" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.Register("test", "one", self)

      expectMsg(None)
    }

    "allow registrations after holding period has passed" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.Register("test", "one", self)

      expectMsg(None)

      Thread.sleep(500)

      handler ! RegistrationHandler.Register("test", "one", self)

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))
    }

    "exclude expired values" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", "one", self)

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      Thread.sleep(500)

      handler ! RegistrationHandler.Inspect("test", self)

      expectMsg(Vector.empty)

      handler ! RegistrationHandler.Register("test", "two", self)

      expectMsg(Some(RegistrationHandler.Record(1, "two", Seq("two"), 10000L)))
    }

    "allow refresh during holding period" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.Refresh("test", Set(RegistrationHandler.Registration(1, "one")), self)

      expectMsg(RegistrationHandler.RefreshResult(Set(RegistrationHandler.Registration(1, "one")), Set.empty))
    }

    "allow refresh of some and rejection of others" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", "one", self)

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.Refresh(
        "test",
        Set(RegistrationHandler.Registration(1, "one"), RegistrationHandler.Registration(2, "two")),
        self
      )

      expectMsg(
        RegistrationHandler.RefreshResult(
          Set(RegistrationHandler.Registration(1, "one")),
          Set(RegistrationHandler.Registration(2, "two"))
        )
      )
    }

    "reject refresh after holding period has passed" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      Thread.sleep(500)

      handler ! RegistrationHandler.Refresh("test", Set(RegistrationHandler.Registration(1, "one")), self)

      expectMsg(RegistrationHandler.RefreshResult(Set.empty, Set(RegistrationHandler.Registration(1, "one"))))
    }

    "remove items" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", "one", self)

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.Remove("test", 1, "one", self)

      expectMsg(true)

      handler ! RegistrationHandler.Remove("test", 1, "one", self)

      expectMsg(false)
    }

    "support inspecting of available topics" in {
      val handler = system.spawnAnonymous(RegistrationHandler.behavior)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test1", "one", self)

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.Register("test2", "one", self)

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.InspectTopics(self)

      expectMsg(Set("test1", "test2"))

      Thread.sleep(500)

      handler ! RegistrationHandler.InspectTopics(self)

      expectMsg(Set.empty[String])
    }
  }
}