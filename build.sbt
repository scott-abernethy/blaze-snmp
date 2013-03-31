name := "blaze"

version := "0.1.0"

scalaVersion := "2.10.1"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
 
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.2-M2",
  "com.typesafe.akka" %% "akka-testkit" % "2.2-M2" % "test",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test"
)
