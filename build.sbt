import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.core.ProblemFilters._
import Dependencies._

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / lintUnusedKeysOnLoad := false

lazy val Scala2 = "2.13.10"
lazy val Scala3 = "3.2.2"

lazy val sec = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin)
  .aggregate(core, `fs2-core`, `fs2-netty`, tsc, tests)

//==== Core ============================================================================================================

lazy val core = project
  .in(file("core"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name := "sec-core",
    libraryDependencies ++=
      compileM(grpcApi, grpcStub, grpcProtobuf, grpcCore) ++
        compileM(cats, scodecBits, ip4s, unum, circe, scalaPb) ++
        protobufM(scalaPb),
    Compile / PB.protoSources := Seq((LocalRootProject / baseDirectory).value / "protobuf"),
    Compile / PB.targets := Seq(scalapb.gen(flatPackage = true, grpc = false) -> (Compile / sourceManaged).value)
  )
  .settings(
    mimaBinaryIssueFilters ++= Seq(
      // Generated code not for end users
      ProblemFilters.exclude[DirectAbstractMethodProblem]("scalapb.GeneratedFileObject.scalaDescriptor"),
      ProblemFilters.exclude[DirectAbstractMethodProblem]("scalapb.GeneratedFileObject.javaDescriptor"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.eventstore.dbclient.proto.shared.*")
    )
  )

//==== FS2 =============================================================================================================

lazy val `fs2-core` = project
  .in(file("fs2"))
  .enablePlugins(AutomateHeaderPlugin, Fs2Grpc)
  .settings(commonSettings)
  .settings(
    name := "sec-fs2",
    libraryDependencies ++=
      compileM(grpcApi, grpcStub, grpcProtobuf, grpcCore) ++
        compileM(cats, catsEffect, fs2, ip4s, log4cats, log4catsNoop, scodecBits, circe, circeParser),
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
    Compile / PB.protoSources := Seq((LocalRootProject / baseDirectory).value / "protobuf")
  )
  .settings(
    mimaBinaryIssueFilters ++= Seq(
      // Generated code not for end users
      ProblemFilters.exclude[DirectAbstractMethodProblem]("scalapb.grpc.ServiceCompanion.scalaDescriptor"),
      ProblemFilters.exclude[DirectAbstractMethodProblem]("scalapb.grpc.ServiceCompanion.javaDescriptor"),
      ProblemFilters.exclude[DirectAbstractMethodProblem]("scalapb.GeneratedFileObject.scalaDescriptor"),
      ProblemFilters.exclude[DirectAbstractMethodProblem]("scalapb.GeneratedFileObject.javaDescriptor"),
      //
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.eventstore.dbclient.proto.shared.*"),
      ProblemFilters.exclude[ReversedMissingMethodProblem]("com.eventstore.dbclient.proto.streams.*"),
      //
      ProblemFilters.exclude[DirectMissingMethodProblem]("sec.api.cluster.Notifier#gossip.*")
    )
  )
  .dependsOn(core)

lazy val `fs2-netty` = project
  .in(file("fs2-netty"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(name := "sec-fs2-client", libraryDependencies ++= compileM(grpcNetty))
  .dependsOn(`fs2-core`, tsc)

//==== Config ==========================================================================================================

lazy val tsc = project
  .in(file("tsc"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(name := "sec-tsc", libraryDependencies ++= compileM(tsConfig))
  .dependsOn(`fs2-core`)

//==== Tests ===========================================================================================================

lazy val SingleNodeITest = config("sit") extend Test
lazy val ClusterITest    = config("cit") extend Test

lazy val tests = project
  .in(file("tests"))
  .enablePlugins(BuildInfoPlugin, AutomateHeaderPlugin, NoPublishPlugin)
  .configs(SingleNodeITest, ClusterITest)
  .settings(commonSettings)
  .settings(inConfig(SingleNodeITest)(Defaults.testSettings))
  .settings(inConfig(ClusterITest)(Defaults.testSettings))
  .settings(
    logBuffered := false,
    parallelExecution := true,
    buildInfoPackage := "sec",
    buildInfoKeys := Seq(BuildInfoKey("certsPath" -> file("").getAbsoluteFile.toPath / "certs")),
    Test / headerSources ++= (SingleNodeITest / sources).value ++ (ClusterITest / sources).value,
    libraryDependencies :=
      compileM(
        fs2Io,
        catsLaws,
        scalaCheck,
        munit,
        munitEffect,
        munitDiscipline,
        catsEffectTestkit,
        log4catsSlf4j,
        log4catsTesting,
        logback
      )
  )
  .dependsOn(core, `fs2-netty`)

//==== Docs ============================================================================================================

lazy val docs = project
  .in(file("sec-docs"))
  .enablePlugins(MdocPlugin, DocusaurusPlugin, NoPublishPlugin)
  .dependsOn(`fs2-netty`)
  .settings(
    scalaVersion := Scala2,
    crossScalaVersions := Nil,
    moduleName := "sec-docs",
    mdocIn := file("docs"),
    mdocVariables := Map(
      "libName"       -> "sec",
      "libVersion"    -> version.value.takeWhile(c => !(c == '+' || c == '-')), // strip off the SNAPSHOT business
      "libGithubRepo" -> "https://github.com/ahjohannessen/sec",
      "grpcVersion"   -> versions.grpc,
      "esdb"          -> "EventStoreDB"
    )
  )

//==== Common ==========================================================================================================

lazy val commonSettings = Seq(
  scalacOptions ++= Seq("-release:8"),
  Compile / doc / scalacOptions ~=
    (_.filterNot(_ == "-Xfatal-warnings"))
)

inThisBuild(
  List(
    scalaVersion := Scala2,
    crossScalaVersions := Seq(Scala3, Scala2),
    versionScheme := Some("early-semver"),
    tlBaseVersion := "0.25",
    tlSonatypeUseLegacyHost := false,
    javacOptions ++= Seq("-target", "8", "-source", "8"),
    organization := "io.github.ahjohannessen",
    organizationName := "Scala EventStoreDB Client",
    startYear := Some(2020),
    developers +=
      tlGitHubDev("ahjohannessen", "Alex Henning Johannessen"),
    shellPrompt := Prompt.enrichedShellPrompt
  )
)

//==== Github Actions ==================================================================================================

addCommandAlias("compileTests", "tests / Test / compile; tests / Sit / compile; tests / Cit / compile;")
addCommandAlias("compileDocs", "docs/mdoc")

def scalaCondition(version: String) = s"contains(matrix.scala, '$version')"
val docsOnMain                      = "github.ref == 'refs/heads/main'"

inThisBuild(
  List(
    githubWorkflowTargetBranches := Seq("main"),
    githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17")),
    githubWorkflowBuildPreamble += WorkflowStep.Run(
      name     = Some("Start Single Node"),
      commands = List("pushd .docker", "./single-node.sh up -d", "popd"),
      cond     = Some(scalaCondition(Scala2)),
      env = Map(
        "SEC_GENCERT_CERTS_ROOT" -> "${{ github.workspace }}"
      )
    ),
    githubWorkflowBuild := Seq(
      WorkflowStep.Sbt(
        name     = Some("Compile docs"),
        commands = List("compileDocs"),
        cond     = Some(scalaCondition(Scala2))
      ),
      WorkflowStep.Sbt(
        name     = Some("Regular tests"),
        commands = List("compileTests", "tests/test")
      ),
      WorkflowStep.Use(
        UseRef.Public("nick-invision", "retry", "v2"),
        name = Some("Single node integration tests"),
        params = Map(
          "timeout_minutes" -> "20",
          "max_attempts"    -> "3",
          "command"         -> "sbt ++${{ matrix.scala }} 'tests / Sit / test'"
        ),
        env = Map(
          "SEC_SIT_CERTS_PATH" -> "${{ github.workspace }}/certs",
          "SEC_SIT_AUTHORITY"  -> "es.sec.local"
        ),
        cond = Some(scalaCondition(Scala2))
      )
    ),
    githubWorkflowBuildPostamble += WorkflowStep.Run(
      name     = Some("Stop Single Node"),
      commands = List("pushd .docker", "./single-node.sh down", "popd"),
      cond     = Some(s"always() && ${scalaCondition(Scala2)}")
    ),
    githubWorkflowBuildPostamble ++= Seq(
      WorkflowStep.Run(
        name     = Some("Start Cluster Nodes"),
        commands = List("pushd .docker", "./cluster.sh up -d", "popd"),
        cond     = Some(scalaCondition(Scala2)),
        env = Map(
          "SEC_GENCERT_CERTS_ROOT" -> "${{ github.workspace }}"
        )
      ),
      WorkflowStep.Use(
        UseRef.Public("nick-invision", "retry", "v2"),
        name = Some("Cluster integration tests"),
        params = Map(
          "timeout_minutes" -> "10",
          "max_attempts"    -> "10",
          "command"         -> "sbt ++${{ matrix.scala }} 'tests / Cit / test'"
        ),
        env = Map(
          "SEC_CIT_CERTS_PATH" -> "${{ github.workspace }}/certs",
          "SEC_CIT_AUTHORITY"  -> "es.sec.local"
        ),
        cond = Some(scalaCondition(Scala2))
      ),
      WorkflowStep.Run(
        name     = Some("Stop Cluster Nodes"),
        commands = List("pushd .docker", "./cluster.sh down", "popd"),
        cond     = Some(s"always() && ${scalaCondition(Scala2)}")
      )
    ),
    githubWorkflowPublish ++= Seq(
      WorkflowStep.Sbt(
        name     = Some("Compile docs"),
        commands = List("compileDocs"),
        cond     = Some(scalaCondition(Scala2))
      ),
      WorkflowStep.Sbt(
        List("docs/docusaurusPublishGhpages"),
        env = Map(
          "GIT_DEPLOY_KEY" -> "${{ secrets.GIT_DEPLOY_KEY }}"
        ),
        cond = Some(s"${scalaCondition(Scala2)} && $docsOnMain")
      )
    )
  )
)
