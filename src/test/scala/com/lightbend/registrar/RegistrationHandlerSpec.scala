package com.lightbend.registrar

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
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

  "RegistrationHandler" must {
    "reject registrations (same topic, same name)" in {
      val handler = system.actorOf(RegistrationHandler.props)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", "one")

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.Register("test", "one")

      expectMsg(None)
    }

    "allow registrations (different topic, same name)" in {
      val handler = system.actorOf(RegistrationHandler.props)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", "one")

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.Register("test2", "one")

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.Inspect("test2")

      expectMsg(Vector(Record(1, "one", Seq("one"), 10000L)))
    }

    "reject registrations during holding period" in {
      val handler = system.actorOf(RegistrationHandler.props)

      handler ! RegistrationHandler.Register("test", "one")

      expectMsg(None)
    }

    "allow registrations after holding period has passed" in {
      val handler = system.actorOf(RegistrationHandler.props)

      handler ! RegistrationHandler.Register("test", "one")

      expectMsg(None)

      Thread.sleep(500)

      handler ! RegistrationHandler.Register("test", "one")

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))
    }

    "exclude expired values" in {
      val handler = system.actorOf(RegistrationHandler.props)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", "one")

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      Thread.sleep(500)

      handler ! RegistrationHandler.Inspect("test")

      expectMsg(Vector.empty)

      handler ! RegistrationHandler.Register("test", "two")

      expectMsg(Some(RegistrationHandler.Record(1, "two", Seq("two"), 10000L)))
    }

    "allow refresh during holding period" in {
      val handler = system.actorOf(RegistrationHandler.props)

      handler ! RegistrationHandler.Refresh("test", Set(RegistrationHandler.Registration(1, "one")))

      expectMsg(RegistrationHandler.RefreshResult(Set(RegistrationHandler.Registration(1, "one")), Set.empty))
    }

    "allow refresh of some and rejection of others" in {
      val handler = system.actorOf(RegistrationHandler.props)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", "one")

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.Refresh(
        "test",
        Set(RegistrationHandler.Registration(1, "one"), RegistrationHandler.Registration(2, "two"))
      )

      expectMsg(
        RegistrationHandler.RefreshResult(
          Set(RegistrationHandler.Registration(1, "one")),
          Set(RegistrationHandler.Registration(2, "two"))
        )
      )
    }

    "reject refresh after holding period has passed" in {
      val handler = system.actorOf(RegistrationHandler.props)

      Thread.sleep(500)

      handler ! RegistrationHandler.Refresh("test", Set(RegistrationHandler.Registration(1, "one")))

      expectMsg(RegistrationHandler.RefreshResult(Set.empty, Set(RegistrationHandler.Registration(1, "one"))))
    }

    "remove items" in {
      val handler = system.actorOf(RegistrationHandler.props)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", "one")

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.Remove("test", 1, "one")

      expectMsg(true)

      handler ! RegistrationHandler.Remove("test", 1, "one")

      expectMsg(false)
    }

    "support inspecting of available topics" in {
      val handler = system.actorOf(RegistrationHandler.props)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test1", "one")

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.Register("test2", "one")

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"), 10000L)))

      handler ! RegistrationHandler.InspectTopics

      expectMsg(Set("test1", "test2"))

      Thread.sleep(500)

      handler ! RegistrationHandler.InspectTopics

      expectMsg(Set.empty[String])
    }
  }
}