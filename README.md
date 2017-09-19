# registrar

This project implements an in-memory coordinator that can be used with [reactive-lib]()
to bootstrap Akka-cluster based applications.

Its state is kept entirely in memory and managed `RegistrationHandler`. Clients are expected to refresh their 
registration periodically or else it is considered expired.

When the program starts up, it is initially in a holding period (default 60s). During this time, no new registrations
are accepted but refresh intervals are honored. Thus, this rebuilds the application state in the event of a
rescheduling.

### Starting the project

`sbt ~re-start`
