
lazy val akkaVersion = "2.6.8"
lazy val awsVersion = "2.14.7"

name := "akka-streams-test.utility"

libraryDependencies ++= Seq(
  "io.spray" %% "spray-json" % "1.3.5",
  "ch.qos.logback"  %  "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "software.amazon.awssdk" % "kinesis" % awsVersion,
  "software.amazon.kinesis" % "amazon-kinesis-client" % "2.3.0"
)