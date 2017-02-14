name := "content-camera-service"

version := "1.0"

scalaVersion := "2.12.1"

libraryDependencies ++= {
  Seq(
    "com.typesafe.akka" %% "akka-http" % "10.0.3",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe.akka" %% "akka-slf4j" % "2.4.17"
  )
}

assemblyJarName in assembly := "content-camera-service.jar"
