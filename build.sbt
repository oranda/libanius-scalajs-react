import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

import sbt.Keys._

name := "Libanius Scala.js front-end"

scalaJSStage in Global := FastOptStage

skip in packageJSDependencies := false

val app = crossProject.settings(
  scalaVersion := "2.11.6",

  unmanagedSourceDirectories in Compile +=
    baseDirectory.value  / "shared" / "main" / "scala",

  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % "0.5.1",
    "com.lihaoyi" %%% "utest" % "0.3.0",
    "com.lihaoyi" %%% "upickle" % "0.2.8"
  ),
  testFrameworks += new TestFramework("utest.runner.Framework")

).jsSettings(
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.8.0",
    "com.github.japgolly.scalajs-react" %%% "core" % "0.8.3",
    "com.github.japgolly.scalajs-react" %%% "extra" % "0.8.3",
    "com.lihaoyi" %%% "scalarx" % "0.2.8"
  ),
  // React itself (react-with-addons.js can be react.js, react.min.js, react-with-addons.min.js)
  jsDependencies += "org.webjars" % "react" % "0.13.1" / "react-with-addons.js" commonJSName "React",
  skip in packageJSDependencies := false // creates app-jsdeps.js with the react JS lib inside

).jvmSettings(
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http"   % "10.1.3",
    "com.typesafe.akka" %% "akka-stream" % "2.5.12"
  )
)

lazy val appJS = app.js.settings(

  // include the libanius core JAR
  unmanagedBase <<= baseDirectory(_ / "../shared/lib")
)

lazy val appJVM = app.jvm.settings(


  version := "0.4",

  // JS files like app-fastopt.js and app-jsdeps.js need to be copied to the server
  (resources in Compile) += (fastOptJS in (appJS, Compile)).value.data,
  (resources in Compile) += (packageJSDependencies in (appJS, Compile)).value,

  // copy resources like quiz.css to the server
  resourceDirectory in Compile <<= baseDirectory(_ / "../shared/src/main/resources"),

  // allow the server to access shared source
  unmanagedSourceDirectories in Compile <+= baseDirectory(_ / "../shared/src/main/scala"),

  // application.conf too must be in the classpath
  unmanagedResourceDirectories in Compile <+= baseDirectory(_ / "../jvm/src/main/resources"),

  // include the libanius core JAR
  unmanagedBase <<= baseDirectory(_ / "../shared/lib")

).enablePlugins(JavaAppPackaging)
