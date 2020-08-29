
lazy val awsVersion = "2.14.7"

name := "akka-streams-test.utility"

libraryDependencies ++= Seq(
  "io.spray" %% "spray-json" % "1.3.5",
  "software.amazon.awssdk" % "kinesis" % awsVersion
)