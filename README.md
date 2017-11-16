# registrar

## About

This project implements an in-memory coordinator that can be used with [reactive-lib](https://github.com/lightbend/reactive-lib)
to bootstrap Akka-cluster based applications.

Its state is kept entirely in memory and managed `RegistrationHandler`. Clients are expected to refresh their 
registration periodically (as advised by the server during registration) or else they are considered expired.

When the program starts up, it is initially in a holding period (default 60s). During this time, no new registrations
are accepted but refresh intervals are honored. Thus, this rebuilds the application state in the event of a
rescheduling.

## Development

### Starting the project

This program uses [sbt-revolver](https://github.com/spray/sbt-revolver) to fork a new JVM process on each file
change.

##### Launch on each source change

`sbt ~re-start`

##### Run all tests and relaunch on each source change

`sbt '~;test;re-start'`

## Maintenance

Enterprise Suite Platform Team <es-platform@lightbend.com>
