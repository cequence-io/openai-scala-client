import sbt.Keys.test

ThisBuild / organization := "io.cequence"
ThisBuild / scalaVersion := "2.12.15" // "2.13.10"
ThisBuild / version := "0.0.1" // -SNAPSHOT"
ThisBuild / isSnapshot := false

lazy val core = (project in file("openai-core"))

lazy val client = (project in file("openai-client"))
  .dependsOn(core)
  .aggregate(core)

lazy val guice = (project in file("openai-guice"))
  .dependsOn(client)
  .aggregate(client)
