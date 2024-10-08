ThisBuild / version := "0.1.0-SNAPSHOT"

Global / excludeLintKeys += idePackagePrefix
Global / excludeLintKeys += test / fork
Global / excludeLintKeys += run / mainClass

val scalaTestVersion = "3.2.15"
val typeSafeConfigVersion = "1.4.2"
//val logbackVersion = "1.4.7"
val sfl4sVersion = "2.0.0-alpha5"
val graphVizVersion = "0.18.1"
val jGraphTlibVersion = "1.5.2"
val guavaVersion = "31.1-jre"
val apacheSparkVersion = "3.5.0"
val scalaXmlVersion = "2.1.0"

lazy val dependencies = Seq(
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "org.scalatestplus" %% "mockito-4-2" % "3.2.12.0-RC2" % Test,
  "com.typesafe" % "config" % typeSafeConfigVersion,
  "org.yaml" % "snakeyaml" % "1.29",
  "guru.nidi" % "graphviz-java" % graphVizVersion,
  "org.jgrapht" % "jgrapht-core" % jGraphTlibVersion,
  "com.google.guava" % "guava" % guavaVersion,
  ("org.apache.spark" %% "spark-graphx" % apacheSparkVersion % "provided").cross(CrossVersion.for3Use2_13),
)

// exclude
excludeDependencies += "org.scala-lang.modules" % "scala-xml_3"

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "3.2.2",
    name := "CS441_HW2",
    idePackagePrefix := Some("com.natalia"),
    libraryDependencies ++= dependencies
  )

Compile / unmanagedJars += file("lib/netmodelsim.jar")

//// include the 'provided' Spark dependency on the classpath for sbt run
//Compile / run := Defaults.runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner).evaluated

scalacOptions ++= Seq(
  "-deprecation", // emit warning and location for usages of deprecated APIs
  "--explain-types", // explain type errors in more detail
  "-feature" // emit warning and location for usages of features that should be imported explicitly
)

compileOrder := CompileOrder.JavaThenScala
test / fork := true
run / fork := true
run / javaOptions ++= Seq(
  "-Xms8G",
  "-Xmx100G",
  "-XX:+UseG1GC"
)

Compile / mainClass := Some("com.natalia.Main")
run / mainClass := Some("com.natalia.Main")

val jarName = "randomGraphWalker.jar"
assembly / assemblyJarName := jarName


//Merging strategies
ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}

// include the 'provided' Spark dependency on the classpath for sbt run
Compile / run := Defaults.runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner).evaluated