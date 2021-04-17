val zioVersion = "1.0.6"
val zioPreludeVersion = "1.0.0-RC3"
val zioQueryVersion = "0.2.0"
val zioSchemaVersion = "0.0.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "zio-starter",
    inThisBuild(
      List(
        organization := "com.witt3rd",
        version := "0.0.1",
        scalaVersion := "3.0.0-RC2"
      )
    ),
    addCompilerPlugin(("com.olegpy" %% "better-monadic-for" % "0.3.1").cross(CrossVersion.for3Use2_13)),
    libraryDependencies ++= Seq(
      // ZIO
      ("dev.zio" %% "zio"               % zioVersion).cross(CrossVersion.for3Use2_13),
      ("dev.zio" %% "zio-test"          % zioVersion % Test).cross(CrossVersion.for3Use2_13),
      ("dev.zio" %% "zio-test-sbt"      % zioVersion % Test).cross(CrossVersion.for3Use2_13),
      ("dev.zio" %% "zio-test-junit"    % zioVersion % Test).cross(CrossVersion.for3Use2_13),
      ("dev.zio" %% "zio-test-magnolia" % zioVersion % Test).cross(CrossVersion.for3Use2_13),
      // ZIO ecosystem
      ("dev.zio" %% "zio-prelude"       % zioPreludeVersion).cross(CrossVersion.for3Use2_13),
      ("dev.zio" %% "zio-query"         % zioQueryVersion).cross(CrossVersion.for3Use2_13),
      ("dev.zio" %% "zio-schema"        % zioSchemaVersion).cross(CrossVersion.for3Use2_13),
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
