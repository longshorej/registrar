package com.lightbend.registrar.net

import com.lightbend.registrar.RegistrationHandler.Record
import org.scalatest.{ Matchers, WordSpec }
import spray.json._

import JsonSupport._

class JsonSupportSpec extends WordSpec
                      with Matchers {
  "JsonSupportSpec" should {
    "Encode Record" in {
      Record(1, "hello", Vector("foo", "bar")).toJson.compactPrint shouldEqual """{"id":1,"name":"hello","members":["foo","bar"]}"""

      Record(2, "", Vector.empty).toJson.compactPrint shouldEqual """{"id":2,"name":"","members":[]}"""

      assertThrows[IllegalArgumentException] {
        Record(3, null, Vector.empty).toJson.compactPrint
      }
    }
  }
}
