name := "akka-grid-workers"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.3"

scalacOptions ++= Seq("-feature")

resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases"
)

libraryDependencies ++= {
  val akkaVersion = "2.3.6"
  Seq(
    "com.github.scala-incubator.io" %% "scala-io-core"      % "0.4.2",
    "com.github.scala-incubator.io" %% "scala-io-file"      % "0.4.2",
    "com.google.guava"              %  "guava"              % "16.0.1",
    "org.apache.opennlp"            %  "opennlp-tools"      % "1.5.3",
    "com.github.tototoshi"          %% "scala-csv"          % "1.0.0",
    "com.jsuereth"                  %% "scala-arm"          % "1.3",
    "com.typesafe"                  %% "scalalogging-slf4j" % "1.0.1",
    "ch.qos.logback"                %  "logback-classic"    % "1.0.13",
    "com.typesafe.akka"             %% "akka-slf4j"         % akkaVersion,
    "com.typesafe.akka"             %% "akka-actor"         % akkaVersion,
    "com.typesafe.akka"             %% "akka-cluster"       % akkaVersion,
    "com.typesafe.akka"             %% "akka-contrib"       % akkaVersion,
    "com.typesafe.akka"             %% "akka-kernel"        % akkaVersion,
    "com.typesafe.akka"             %% "akka-testkit"       % akkaVersion  % "test",
    "org.scalatest"                 %% "scalatest"          % "2.0"        % "test"
  )
}