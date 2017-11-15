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
      Record(1, "hello", Vector("foo", "bar"), 10000L, 60000L).toJson.compactPrint shouldEqual """{"name":"hello","expireAfter":60000,"refreshInterval":10000,"id":1,"members":["foo","bar"]}"""

      Record(2, "", Vector.empty, 10000L, 60000L).toJson.compactPrint shouldEqual """{"name":"","expireAfter":60000,"refreshInterval":10000,"id":2,"members":[]}"""

      assertThrows[IllegalArgumentException] {
        Record(3, null, Vector.empty, 10000L, 60000L).toJson.compactPrint
      }
    }

    "Encode RefreshResult" in {
      RefreshResult(Set(Registration(1, "one")), Set(Registration(2, "two")), 100, 6000).toJson.compactPrint shouldEqual """{"accepted":[{"id":1,"name":"one"}],"rejected":[{"id":2,"name":"two"}],"refreshInterval":100,"expireAfter":6000}"""
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
