package com.lightbend.registrar.net

import com.lightbend.registrar.RegistrationHandler.{ Record, RefreshResult, Registration }
import com.lightbend.registrar.net.ControlProtocolRoute.{ RegistrationRequest, RefreshRequest, RemoveRequest }
import org.scalatest.{ Matchers, WordSpec }
import spray.json._
import JsonSupport._

class JsonSupportSpec extends WordSpec
  with Matchers {

  "JsonSupportSpec" should {
    "Encode Record" in {
      Record(1, "hello", Vector("foo", "bar"), 10000L).toJson.compactPrint shouldEqual """{"id":1,"name":"hello","members":["foo","bar"],"refreshInterval":10000}"""

      Record(2, "", Vector.empty, 10000L).toJson.compactPrint shouldEqual """{"id":2,"name":"","members":[],"refreshInterval":10000}"""

      assertThrows[IllegalArgumentException] {
        Record(3, null, Vector.empty, 10000L).toJson.compactPrint
      }
    }

    "Encode RefreshResult" in {
      RefreshResult(Set(Registration(1, "one")), Set(Registration(2, "two")), 100).toJson.compactPrint shouldEqual """{"accepted":[{"id":1,"name":"one"}],"rejected":[{"id":2,"name":"two"}],"refreshInterval":100}"""
    }

    "Encode Registration" in {
      Registration(1, "one").toJson.compactPrint shouldEqual """{"id":1,"name":"one"}"""
    }

    "Encode RegistrationRequest" in {
      RegistrationRequest("test").toJson.compactPrint shouldEqual """{"name":"test"}"""
    }

    "Encode RefreshRequest" in {
      RefreshRequest(Set(Registration(1, "one"))).toJson.compactPrint shouldEqual
        """{"registrations":[{"id":1,"name":"one"}]}"""

      RefreshRequest(Set(Registration(1, "one"), Registration(2, "two"))).toJson.compactPrint shouldEqual
        """{"registrations":[{"id":1,"name":"one"},{"id":2,"name":"two"}]}"""
    }

    "Encode RemoveRequest" in {
      RemoveRequest(1, "test").toJson.compactPrint shouldEqual """{"id":1,"name":"test"}"""
    }
  }
}
