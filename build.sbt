import scalariform.formatter.preferences._

name := "akka-madness"

version := "0.1"

scalaVersion := "2.13.1"

scalacOptions ++= Seq("-deprecation", "-feature")

mainClass in (Compile, run) := Some("akka.madness.OrderSystem")

coverageMinimum := 80
coverageFailOnMinimum := true
coverageEnabled := true

scalariformPreferences := scalariformPreferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentConstructorArguments, true)
  .setPreference(DanglingCloseParenthesis, Preserve)

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-actor
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % "2.6.4"
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.6.4"

libraryDependencies += "io.circe" %% "circe-parser" % "0.13.0"
libraryDependencies += "io.circe" %% "circe-generic" % "0.13.0"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.30"

libraryDependencies += "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.4" % Test
libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.0" % Test
libraryDependencies += "org.scalamock" %% "scalamock" % "4.4.0" % Test
