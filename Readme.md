#Madness Order System

This application is written in Scala 2.13 and uses a framework known
as Akka. Akka is an actor system which provides all the tools for a
reactive architecture that is scalable to 100s of nodes distributing 
the load across shards. It also provides multi-threading handling of messages 
using a dispatcher pool of threads handling messages that are waiting in the mailboxes
of the individual actors. This allows the actors to have the illusion of single threaded
while the entire system is multi-threaded.

Also all data structures are implemented as immutable except for collection
classes that explicitly use the `mutable` instances of the collection class.
This provides an additional level of thread safety.

To run the application use the command `sbt "run --orders orders.json"`

To change the behavior of the application modify the settings in `src/main/resources/reference.conf` or
create a new file `application.conf` and copy the structure of `reference.conf` and run the application 
with the command `sbt "run -Dconfig.file=application.conf"`

To compile, test, and report unit test coverage type the command:

`sbt "test ; coverageReport"`

```
[info] Run completed in 1 second, 833 milliseconds.
[info] Total number of tests run: 20
[info] Suites: completed 5, aborted 0
[info] Tests: succeeded 20, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 3 s, completed Apr 13, 2020, 1:27:21 AM
[info] Waiting for measurement data to sync...
[info] Reading scoverage instrumentation [/Users/kamisama/Projects/akka-madness/target/scala-2.13/scoverage-data/scoverage.coverage]
[info] Reading scoverage measurements...
[info] Generating scoverage reports...
[info] Written Cobertura report [/Users/kamisama/Projects/akka-madness/target/scala-2.13/coverage-report/cobertura.xml]
[info] Written XML coverage report [/Users/kamisama/Projects/akka-madness/target/scala-2.13/scoverage-report/scoverage.xml]
[info] Written HTML coverage report [/Users/kamisama/Projects/akka-madness/target/scala-2.13/scoverage-report/index.html]
[info] Statement coverage.: 92.68%
[info] Branch coverage....: 100.00%
[info] Coverage reports completed
[info] Coverage is above minimum [92.68% > 80.0%]
[info] All done. Coverage was [92.68%]
[success] Total time: 1 s, completed Apr 13, 2020, 1:27:22 AM
```