name := "akka-streams-test"

lazy val commonSettings = Seq(
  scalaVersion := "2.13.3",
  version := "0.1",

  fork in run := true,
  javaOptions in run += "-Duser.timezone=UTC",
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
  scalafmtOnCompile := true
)

lazy val akkaStreamsTest = (project in file("."))
  .aggregate(
    utility
  )

lazy val utility = (project in file("utility")).settings(commonSettings)
