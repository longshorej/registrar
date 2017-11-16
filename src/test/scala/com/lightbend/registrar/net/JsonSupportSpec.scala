package com.lightbend.registrar.net

import java.util.UUID

import com.lightbend.registrar.RegistrationHandler.{ Record, RefreshResult, Registration }
import com.lightbend.registrar.net.ControlProtocolRoute.{ RefreshRequest, RegistrationRequest, RemoveRequest }
import org.scalatest.{ Matchers, WordSpec }
import spray.json._
import JsonSupport._

class JsonSupportSpec extends WordSpec
  with Matchers {

  val idOne = new UUID(0, 1)
  val idTwo = new UUID(0, 2)
  val idThree = new UUID(0, 3)

  "JsonSupportSpec" should {
    "Encode Record" in {
      Record(idOne, "hello", Vector("foo", "bar"), 10000L, 60000L).toJson.compactPrint shouldEqual """{"name":"hello","expireAfter":60000,"refreshInterval":10000,"id":"00000000-0000-0000-0000-000000000001","members":["foo","bar"]}"""

      Record(idTwo, "", Vector.empty, 10000L, 60000L).toJson.compactPrint shouldEqual """{"name":"","expireAfter":60000,"refreshInterval":10000,"id":"00000000-0000-0000-0000-000000000002","members":[]}"""

      assertThrows[IllegalArgumentException] {
        Record(idThree, null, Vector.empty, 10000L, 60000L).toJson.compactPrint
      }
    }

    "Encode RefreshResult" in {
      RefreshResult(Set(Registration(idOne, "one")), Set(Registration(idTwo, "two")), 100, 6000).toJson.compactPrint shouldEqual """{"accepted":[{"id":"00000000-0000-0000-0000-000000000001","name":"one"}],"rejected":[{"id":"00000000-0000-0000-0000-000000000002","name":"two"}],"refreshInterval":100,"expireAfter":6000}"""
    }

    "Encode Registration" in {
      Registration(idOne, "one").toJson.compactPrint shouldEqual """{"id":"00000000-0000-0000-0000-000000000001","name":"one"}"""
    }

    "Encode RegistrationRequest" in {
      RegistrationRequest(idOne, "test").toJson.compactPrint shouldEqual """{"id":"00000000-0000-0000-0000-000000000001","name":"test"}"""
    }

    "Encode RefreshRequest" in {
      RefreshRequest(Set(Registration(idOne, "one"))).toJson.compactPrint shouldEqual
        """{"registrations":[{"id":"00000000-0000-0000-0000-000000000001","name":"one"}]}"""

      RefreshRequest(Set(Registration(idOne, "one"), Registration(idTwo, "two"))).toJson.compactPrint shouldEqual
        """{"registrations":[{"id":"00000000-0000-0000-0000-000000000001","name":"one"},{"id":"00000000-0000-0000-0000-000000000002","name":"two"}]}"""
    }

    "Encode RemoveRequest" in {
      RemoveRequest(idOne, "test").toJson.compactPrint shouldEqual """{"id":"00000000-0000-0000-0000-000000000001","name":"test"}"""
    }
  }
}
