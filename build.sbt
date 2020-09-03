
name := "akka-streams-test"

lazy val commonSettings = Seq(
  scalaVersion := "2.13.3",
  version := "0.1",

  fork in run := true,
  javaOptions in run += "-Duser.timezone=UTC",
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
  unmanagedBase := baseDirectory.value / "lib",
  fork in run := true,
  javaOptions in run += "-Duser.timezone=UTC",
  scalafmtOnCompile := true,

  fork in Test := true,
  envVars in Test := Map("LOG_ROOT" -> "logs"),

  assemblyMergeStrategy in assembly := {
    case PathList("javax", "servlet", xs @ _*)               => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith ".html"       => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith ".conf"       => MergeStrategy.concat
    case PathList(ps @ _*) if ps.last endsWith ".jar"        => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith ".class"      => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith ".properties" => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith ".json"       => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith ".config"     => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith ".types"      => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith ".txt"        => MergeStrategy.first
    case "application.conf"                                  => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

def assemblySettings(jarName: String, mainClassName: String) = Seq(
  test in assembly := {},
  mainClass in assembly := Some(mainClassName),
  assemblyJarName in assembly := jarName
)

lazy val akkaStreamsTest = (project in file("."))
  .aggregate(
    utility,
    applications_kinesis_publisher,
    applications_simple_stream,
    applications_stream_monitoring
  )

lazy val utility = (project in file("utility")).settings(commonSettings)

lazy val applications_kinesis_publisher = (project in file("applications/kinesis-publisher"))
  .settings(commonSettings)
  .settings(assemblySettings("kinesis-publisher.jar", "matsuri2020.yabumoto.applications.kinesispublisher.Main"))
  .dependsOn(utility)
  .aggregate(utility)

lazy val applications_simple_stream = (project in file("applications/simple-stream"))
  .settings(commonSettings)
  .settings(assemblySettings("simple-stream.jar", "matsuri2020.yabumoto.applications.simplestream.Main"))
  .dependsOn(utility)
  .aggregate(utility)

lazy val applications_downstream_throttle = (project in file("applications/downstream_throttle"))
  .settings(commonSettings)
  .settings(assemblySettings("downstream-throttle.jar", "matsuri2020.yabumoto.applications.downstreamthrottle.Main"))
  .dependsOn(utility)
  .aggregate(utility)

lazy val applications_stream_monitoring = (project in file("applications/stream-monitoring"))
  .settings(commonSettings)
  .settings(assemblySettings("stream-monitoring.jar", "matsuri2020.yabumoto.applications.streammonitoring.Main"))
  .dependsOn(utility)
  .aggregate(utility)
