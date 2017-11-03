package com.lightbend.registrar.net

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.ask
import akka.typed.scaladsl.adapter._
import akka.util.Timeout
import com.lightbend.registrar.{RegistrationHandler, Settings}
import java.util.concurrent.TimeUnit

import akka.typed.ActorRef
import akka.typed.scaladsl.AskPattern._
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._
import spray.json._

class ControlProtocolRouteSpec extends WordSpec
                               with Matchers
                               with ScalatestRouteTest {
  import RegistrationHandler._
  import JsonSupport._

  implicit val settings = new Settings(system.settings)
  implicit val scheduler = system.scheduler

  val handler = {
    val h = system.spawn(RegistrationHandler.behavior, "registration-handler")

    implicit val timeout = Timeout(1, TimeUnit.SECONDS)

    h ! EnableRegistration

    Await.result(
      for {
        _ <- h ? (Register("test1", "test1", _: ActorRef[Option[Record]]))
        _ <- h ? (Register("test1", "test2", _: ActorRef[Option[Record]]))
        _ <- h ? (Register("test2", "test3", _: ActorRef[Option[Record]]))
        _ <- h ? (Register("test2", "test4", _: ActorRef[Option[Record]]))
      } yield {},

      5.seconds
    )

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
        responseAs[String] shouldEqual """[{"id":1,"name":"test1","members":["test1","test2"],"refreshInterval":10000},{"id":2,"name":"test2","members":["test1","test2"],"refreshInterval":10000}]"""
      }
    }

    "list members of an unknown topic" in {
      Get("/topics/test999") ~> route ~> check {
        response.status.isSuccess shouldEqual true
        responseAs[String] shouldEqual """[]"""
      }
    }

    "create new member in a topic" in {
      Post("/topics/test1/register", "test5") ~> route ~> check {
        response.status.isSuccess shouldEqual true
        responseAs[String] shouldEqual """{"id":3,"name":"test5","members":["test1","test2","test5"],"refreshInterval":10000}"""
      }
    }

    "not create a new member in a topic (duplicate)" in {
      Post("/topics/test1/register", "test2") ~> route ~> check {
        response.status.isFailure shouldEqual true
      }
    }

    "refresh a member" in {
      Post("/topics/test1/refresh", Set(Registration(1, "test1"))) ~> route ~> check {
        response.status.isSuccess shouldEqual true
        responseAs[String] shouldEqual """{"accepted":[{"id":1,"name":"test1"}],"rejected":[]}"""
      }
    }

    "not refresh an invalid member" in {
      Post("/topics/test1/refresh", Vector(Registration(12345, "test32"))) ~> route ~> check {
        response.status.isSuccess shouldEqual true
        responseAs[String] shouldEqual """{"accepted":[],"rejected":[{"id":12345,"name":"test32"}]}"""
      }
    }

    "remove a member" in {
      Delete("/topics/test1/1/test1") ~> route ~> check {
        response.status.isSuccess shouldEqual true
        responseAs[String] shouldEqual """OK"""
      }
    }

    "not remove an invalid member" in {
      Delete("/topics/test1/12345/test1") ~> route ~> check {
        response.status.isFailure shouldEqual true
      }
    }
  }
}
