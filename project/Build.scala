import sbt._
import sbt.Keys._

object MyBuild extends Build {

  override lazy val settings = super.settings ++ Seq(
    name := "spire",
    organization := "org.spire-math",
    version := "0.3.0-M2",

    scalaVersion := "2.10.0-RC1",
    scalaBinaryVersion := "2.10.0-RC1",

    licenses := Seq("BSD-style" -> url("http://opensource.org/licenses/MIT")),
    homepage := Some(url("http://spire-math.org")),

    libraryDependencies ++= Seq(
      //"org.scalatest" % "scalatest_2.10.0-M7" % "1.9-2.10.0-M7-B1" % "test",
      "org.scala-lang" % "scala-reflect" % "2.10.0-RC1"
    ),

    scalacOptions ++= Seq(
      "-Yinline-warnings",
      "-deprecation",
      "-unchecked",
      "-optimize",
      "-feature"
    ),

    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },

    publishTo <<= version {
      (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT")) 
        Some("snapshots" at nexus + "content/repositories/snapshots") 
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },

    pomExtra := (
<scm>
  <url>git@github.com:non/spire.git</url>
  <connection>scm:git:git@github.com:non/spire.git</connection>
</scm>
<developers>
  <developer>
    <id>d_m</id>
    <name>Erik Osheim</name>
    <url>http://github.com/non/</url>
  </developer>
  <developer>
    <id>tixxit</id>
    <name>Tom Switzer</name>
    <url>http://github.com/tixxit/</url>
  </developer>
</developers>

    )
  )

  val key = AttributeKey[Boolean]("javaOptionsPatched")

  lazy val spire = Project("spire", file("."))
  lazy val pkg = Project("pkg", file("pkg")).aggregate(spire, deps)
  lazy val examples = Project("examples", file("examples")).dependsOn(spire)
  lazy val benchmark:Project = Project("benchmark", file("benchmark")).
    settings(benchmarkSettings: _*).
    dependsOn(spire)

  def benchmarkSettings = Seq(
    // raise memory limits here if necessary
    // TODO: this doesn't seem to be working with caliper at the moment :(
  
    javaOptions in run += "-Xmx4G",

    libraryDependencies ++= Seq(
      "com.google.guava" % "guava" % "r09",
      "com.google.code.java-allocation-instrumenter" % "java-allocation-instrumenter" % "2.0",
      "com.google.code.caliper" % "caliper" % "1.0-SNAPSHOT" from "http://n0d.es/jars/caliper-1.0-SNAPSHOT.jar",
      "com.google.code.gson" % "gson" % "1.7.1"
    ),

    // enable forking in run
    fork in run := true,

    // custom kludge to get caliper to see the right classpath

    // we need to add the runtime classpath as a "-cp" argument to the
    // `javaOptions in run`, otherwise caliper will not see the right classpath
    // and die with a ConfigurationException unfortunately `javaOptions` is a
    // SettingsKey and `fullClasspath in Runtime` is a TaskKey, so we need to
    // jump through these hoops here in order to feed the result of the latter
    // into the former
    onLoad in Global ~= { previous => state =>
      previous {
        state.get(key) match {
          case None =>
            // get the runtime classpath, turn into a colon-delimited string
            val classPath = Project.runTask(fullClasspath in Runtime in benchmark, state).get._2.toEither.right.get.files.mkString(":")
            // return a state with javaOptionsPatched = true and javaOptions set correctly
            Project.extract(state).append(Seq(javaOptions in (benchmark, run) ++= Seq("-cp", classPath)), state.put(key, true))
          case Some(_) =>
            state // the javaOptions are already patched
        }
      }
    }

    // caliper stuff stolen shamelessly from scala-benchmarking-template
  )
}
