package com.lightbend.registrar

import akka.actor.ActorSystem
import akka.util.Timeout
import java.time.Duration
import java.util.concurrent.TimeUnit

class Settings(settings: ActorSystem.Settings) {
  private lazy val config = settings.config
  private lazy val registrar = config.getConfig("registrar")

  object net {
    implicit val askTimeout = akkaTimeout(registrar.getDuration("net.ask-timeout"))
    val bindInterface = registrar.getString("net.bind-interface")
    val bindPort = registrar.getInt("net.bind-port")
  }

  object registration {
    val expireAfter = akkaTimeout(registrar.getDuration("registration.expire-after"))
    val holdingPeriod = akkaTimeout(registrar.getDuration("registration.holding-period"))
  }

  private def akkaTimeout(duration: Duration): Timeout = Timeout(duration.toMillis, TimeUnit.MILLISECONDS)
}
