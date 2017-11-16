package com.lightbend.registrar.net

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.typed.scaladsl.adapter._
import akka.typed.ActorRef
import akka.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.lightbend.registrar.{ RegistrationHandler, Settings }
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.scalatest.{ Matchers, WordSpec }
import scala.concurrent.Await
import scala.concurrent.duration._
import spray.json._

class ControlProtocolRouteSpec extends WordSpec
  with Matchers
  with ScalatestRouteTest {
  import ControlProtocolRoute._
  import RegistrationHandler._
  import JsonSupport._

  implicit val settings = new Settings(system.settings)
  implicit val scheduler = system.scheduler

  val idOne = new UUID(0, 1)
  val idTwo = new UUID(0, 2)
  val idThree = new UUID(0, 3)
  val idFour = new UUID(0, 4)
  val idFive = new UUID(0, 5)

  val handler = {
    val h = system.spawn(RegistrationHandler.behavior, "registration-handler")

    implicit val timeout = Timeout(1, TimeUnit.SECONDS)

    h ! EnableRegistration

    Await.result(
      for {
        _ <- h ? (Register("test1", idOne, "test1", _: ActorRef[Option[Record]]))
        _ <- h ? (Register("test1", idTwo, "test2", _: ActorRef[Option[Record]]))
        _ <- h ? (Register("test2", idThree, "test3", _: ActorRef[Option[Record]]))
        _ <- h ? (Register("test2", idFour, "test4", _: ActorRef[Option[Record]]))
      } yield {},

      5.seconds)

    h
  }

  val route = ControlProtocolRoute(handler)

  "ControlProtocolRoute" should {
    "respond to a /ping request" in {
      Get("/ping") ~> route ~> check {
        response.status.isSuccess shouldEqual true
        responseAs[String] shouldEqual "pong!"
      }
    }

    "not handle an unhandled route" in {
      Get("/non-existant") ~> route ~> check {
        handled shouldBe false
      }
    }

    "list topics" in {
      Get("/topics") ~> route ~> check {
        response.status.isSuccess shouldEqual true
        responseAs[String] shouldEqual """["test1","test2"]"""
      }
    }

    "list members of a known topic" in {
      Get("/topics/test1") ~> route ~> check {
        response.status.isSuccess shouldEqual true

        responseAs[String] shouldEqual """[{"name":"test1","expireAfter":60000,"refreshInterval":10000,"id":"00000000-0000-0000-0000-000000000001","members":["test1","test2"]},{"name":"test2","expireAfter":60000,"refreshInterval":10000,"id":"00000000-0000-0000-0000-000000000002","members":["test1","test2"]}]"""
      }
    }

    "list members of an unknown topic" in {
      Get("/topics/test999") ~> route ~> check {
        response.status.isSuccess shouldEqual true
        responseAs[String] shouldEqual """[]"""
      }
    }

    "create new member in a topic" in {
      Post("/topics/test1/register", RegistrationRequest(idFive, "test5")) ~> route ~> check {
        response.status.isSuccess shouldEqual true

        responseAs[String] shouldEqual """{"name":"test5","expireAfter":60000,"refreshInterval":10000,"id":"00000000-0000-0000-0000-000000000005","members":["test1","test2","test5"]}"""
      }
    }

    "create a new member in a topic (duplicate, same id)" in {
      Post("/topics/test1/register", RegistrationRequest(idTwo, "test2")) ~> route ~> check {
        response.status.isFailure shouldEqual false
      }
    }

    "not create a new member in a topic (duplicate, diff id)" in {
      Post("/topics/test1/register", RegistrationRequest(idThree, "test2")) ~> route ~> check {
        response.status.isFailure shouldEqual true
      }
    }

    "refresh a member" in {
      Post("/topics/test1/refresh", RefreshRequest(Set(Registration(idOne, "test1")))) ~> route ~> check {
        response.status.isSuccess shouldEqual true
        responseAs[String] shouldEqual """{"accepted":[{"id":"00000000-0000-0000-0000-000000000001","name":"test1"}],"rejected":[],"refreshInterval":10000,"expireAfter":60000}"""
      }
    }

    "not refresh an invalid member" in {
      Post("/topics/test1/refresh", RefreshRequest(Set(Registration(idFour, "test32")))) ~> route ~> check {
        response.status.isSuccess shouldEqual true
        responseAs[String] shouldEqual """{"accepted":[],"rejected":[{"id":"00000000-0000-0000-0000-000000000004","name":"test32"}],"refreshInterval":10000,"expireAfter":60000}"""
      }
    }

    "remove a member" in {
      Delete("/topics/test1/remove", RemoveRequest(idOne, "test1")) ~> route ~> check {
        response.status.isSuccess shouldEqual true
        responseAs[String] shouldEqual """OK"""
      }
    }

    "not remove an invalid member" in {
      Delete("/topics/test1/remove", RemoveRequest(idTwo, "test1")) ~> route ~> check {
        response.status.isFailure shouldEqual true
      }
    }
  }
}
