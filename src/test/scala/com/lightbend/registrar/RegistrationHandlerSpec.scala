package com.lightbend.registrar

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestActors, TestKit }
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import scala.collection.immutable.Seq

class RegistrationHandlerSpec() extends TestKit(ActorSystem("registrar")) with ImplicitSender
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

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"))))

      handler ! RegistrationHandler.Register("test", "one")

      expectMsg(None)
    }

    "allow registrations (different topic, same name)" in {
      val handler = system.actorOf(RegistrationHandler.props)

      handler ! RegistrationHandler.EnableRegistration

      handler ! RegistrationHandler.Register("test", "one")

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"))))

      handler ! RegistrationHandler.Register("test2", "one")

      expectMsg(Some(RegistrationHandler.Record(1, "one", Seq("one"))))
    }
  }
}