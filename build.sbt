import Dependencies._

lazy val root = project
  .in(file("."))
  .settings(skip in publish := true)
  .aggregate(core, netty, demo)

lazy val basePath     = file("").getAbsoluteFile.toPath
lazy val certsPathKey = "certsPath" -> basePath / "certs"

lazy val IntegrationTest = config("it") extend Test

lazy val core = project
  .enablePlugins(Fs2Grpc)
  .enablePlugins(BuildInfoPlugin)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.testTasks): _*)
  .settings(commonSettings)
  .settings(
    name := "sec-core",
    buildInfoPackage := "sec",
    Test / testOptions := Seq(Tests.Filter(_ endsWith "Spec")),
    IntegrationTest / testOptions := Seq(Tests.Filter(_ endsWith "ITest")),
    IntegrationTest / parallelExecution := false,
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
    libraryDependencies ++=
      compileM(cats, catsEffect, fs2, log4cats, log4catsNoop, scodecBits, circe, circeParser, scalaPb) ++
        protobufM(scalaPb) ++
        testM(
          specs2Cats,
          catsEffectTesting,
          catsEffectLaws,
          specs2ScalaCheck,
          circeGeneric,
          grpcNetty,
          tcnative,
          log4catsSlf4j,
          logback
        )
  )
  .settings(
    addBuildInfoToConfig(Test),
    Test / buildInfoKeys := buildInfoKeys.value :+ BuildInfoKey(certsPathKey),
    Test / buildInfoObject := "TestBuildInfo"
  )

lazy val netty = project
  .in(file("netty"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "sec",
    libraryDependencies ++= compileM(grpcNetty, tcnative)
  )

lazy val demo = project
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(netty)
  .settings(
    name := "demo",
    buildInfoKeys := buildInfoKeys.value :+ BuildInfoKey(certsPathKey),
    buildInfoPackage := "sec.demo",
    libraryDependencies ++= compileM(log4catsSlf4j, logback),
    skip in publish := true
  )

// General Settings

lazy val commonSettings = Seq(
  addCompilerPlugin(kindProjector),
  Compile / scalacOptions ~= devScalacOptions,
  Test / scalacOptions ~= devScalacOptions,
  IntegrationTest / scalacOptions ~= devScalacOptions,
  libraryDependencies ++= testM(catsLaws, disciplineSpecs2, specs2, specs2ScalaCheck)
)

lazy val metalsEnabled =
  scala.util.Properties.envOrElse("METALS_ENABLED", "false").toBoolean

val devScalacOptions = { options: Seq[String] =>
  if (metalsEnabled)
    options.filterNot(Set("-Wunused:locals", "-Wunused:params", "-Wunused:imports", "-Wunused:privates"))
  else options
}

inThisBuild(
  List(
    scalaVersion := "2.13.3",
    organization := "io.github.ahjohannessen",
    organizationName := "Scala EventStoreDB Client",
    developers := List(
      Developer(
        "ahjohannessen",
        "Alex Henning Johannessen",
        "ahjohannessen@gmail.com",
        url("https://github.com/ahjohannessen")
      )
    ),
    homepage := Some(url("https://github.com/ahjohannessen/sec")),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    pomIncludeRepository := { _ =>
      false
    },
    scalacOptions in (Compile, doc) ++= Seq(
      "-groups",
      "-sourcepath",
      (baseDirectory in LocalRootProject).value.getAbsolutePath,
      "-doc-source-url",
      "https://github.com/ahjohannessen/sec/blob/v" + version.value + "€{FILE_PATH}.scala"
    ),
    shellPrompt := Prompt.enrichedShellPrompt,
    // Github Actions
    githubWorkflowJavaVersions := Seq("adopt@1.11"),
    githubWorkflowTargetTags += "v*",
    githubWorkflowPublish := Seq(WorkflowStep.Sbt(List("ci-release"))),
    githubWorkflowPublishTargetBranches += RefPredicate.StartsWith(Ref.Tag("v")),
    githubWorkflowEnv ++= Map(
      "SONATYPE_USERNAME" -> s"$${{ secrets.SONATYPE_USERNAME }}",
      "SONATYPE_PASSWORD" -> s"$${{ secrets.SONATYPE_PASSWORD }}",
      "PGP_SECRET"        -> s"$${{ secrets.PGP_SECRET }}",
      "PGP_PASSPHRASE"    -> s"$${{ secrets.PGP_PASSPHRASE }}"
    )
  )
)
