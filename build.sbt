import sbt.Keys._

enablePlugins(ScalaJSPlugin)

name := "Libanius Scala.js front-end"

scalaJSStage in Global := FastOptStage

skip in packageJSDependencies := false

val app = crossProject.settings(
  unmanagedSourceDirectories in Compile +=
    baseDirectory.value  / "shared" / "main" / "scala",

  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % "0.5.1",
    "com.lihaoyi" %%% "utest" % "0.3.0",
    "com.lihaoyi" %%% "upickle" % "0.2.8"
  ),
  scalaVersion := "2.11.6",
  testFrameworks += new TestFramework("utest.runner.Framework")
).jsSettings(
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.8.0",
    "com.github.japgolly.scalajs-react" %%% "core" % "0.8.3",
    "com.github.japgolly.scalajs-react" %%% "extra" % "0.8.3",
    "com.lihaoyi" %%% "scalarx" % "0.2.8"
  ),
  // React itself
  //   (react-with-addons.js can be react.js, react.min.js, react-with-addons.min.js)
  jsDependencies += "org.webjars" % "react" % "0.13.1" / "react-with-addons.js" commonJSName "React",
  skip in packageJSDependencies := false // creates app-jsdeps.js with the react JS lib inside

).jvmSettings(
  libraryDependencies ++= Seq(
    "io.spray" %% "spray-can" % "1.3.2",
    "io.spray" %% "spray-routing" % "1.3.2",
    "com.typesafe.akka" %% "akka-actor" % "2.3.6",
    "org.scalaz" %% "scalaz-core" % "7.1.2"
  )
)

lazy val appJS = app.js.settings(

  // include the libanius core JAR
  unmanagedBase <<= baseDirectory(_ / "../shared/lib")
)

lazy val appJVM = app.jvm.settings(

  // JS files like app-fastopt.js and app-jsdeps.js need to be copied to the server
  (resources in Compile) += (fastOptJS in (appJS, Compile)).value.data,
  (resources in Compile) += (packageJSDependencies in (appJS, Compile)).value,

  // copy resources like quiz.css to the server
  resourceDirectory in Compile <<= baseDirectory(_ / "../shared/src/main/resources"),

  unmanagedSourceDirectories in Compile <+= baseDirectory(_ / "../shared/src/main/scala"),

  // application.conf too must be in the classpath
  unmanagedResourceDirectories in Compile <+= baseDirectory(_ / "../jvm/src/main/resources"),

  // include the libanius core JAR
  unmanagedBase <<= baseDirectory(_ / "../shared/lib")
)
