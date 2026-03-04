import org.typelevel.sbt.tpolecat.DevMode

import java.net.URI

inThisBuild(
  List(
    organization := "tech.neander",
    homepage := Some(url("https://github.com/neandertech/smithy4s-fetch")),
    licenses := List(License.Apache2),
    developers := List(
      Developer(
        "velvetbaldmime",
        "Anton Sviridov",
        "contact@indoorvivants.com",
        URI.create("https://indoorvivants.com").toURL
      )
    )
  )
)

val scala213 = "2.13.18"
val scala3 = "3.3.7"
val scalaNext = "3.8.2"
val allScalaVersions = List(scala213, scala3)

val smithy4sVersion = "0.18.48"
val http4sVersion = "0.23.33"
val weaverVersion = "0.8.4"

ThisBuild / versionScheme := Some("early-semver")
ThisBuild / tpolecatOptionsMode := DevMode

val commonSettings = Seq(
  mimaPreviousArtifacts := Set.empty
)

val fetch = projectMatrix
  .in(file("modules") / "fetch")
  .jsPlatform(allScalaVersions)
  .settings(
    name := "smithy4s-fetch",
    commonSettings,
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %%% "smithy4s-core" % smithy4sVersion,
      "com.disneystreaming.smithy4s" %%% "smithy4s-json" % smithy4sVersion,
      "org.scala-js" %%% "scalajs-dom" % "2.8.1"
    ),
    libraryDependencies ++= Seq(
      "com.disneystreaming" %%% "weaver-cats" % weaverVersion % Test
    )
  )

val tests =
  projectMatrix
    .in(file("modules") / "tests")
    .settings(publish / skip := true)
    .disablePlugins(MimaPlugin)
    .dependsOn(fetch)
    // Hacky way to make projectmatrix work: we pretend to use scala3LTS, but we override it later
    .jsPlatform(List(scala3))
    .settings(
      scalaVersion := scalaNext,
      libraryDependencies ++=
        Seq(
          "com.disneystreaming" %%% "weaver-cats" % weaverVersion % Test,
          "tech.neander" %%% "smithy4s-deriving" % "0.0.3" % Test,
          "com.disneystreaming.smithy4s" %%% "smithy4s-http4s" % smithy4sVersion % Test,
          "org.http4s" %%% "http4s-ember-server" % http4sVersion % Test,
          "org.http4s" %%% "http4s-ember-client" % http4sVersion % Test
        ),
      scalaJSLinkerConfig ~= {
        _.withModuleKind(org.scalajs.linker.interface.ModuleKind.ESModule)
      }
    )

val root = project
  .in(file("."))
  .settings(publish / skip := true)
  .disablePlugins(MimaPlugin)
  .aggregate(List(fetch, tests).flatMap(_.projectRefs): _*)

addCommandAlias(
  "ci",
  "compile;test;scalafmtCheckAll;mimaReportBinaryIssues"
)
