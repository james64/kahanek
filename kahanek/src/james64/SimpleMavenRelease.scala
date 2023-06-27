import java.math.BigInteger
import java.security.MessageDigest

import mill._
import mill.api.PathRef
import mill.define.Task
import mill.scalalib.JavaModule
import mill.scalalib.publish.{Artifact, Dependency, Developer, Pom, PomSettings, Scope, VersionControl}
import os.Path

// Largely copied and edited from mill.scalalib.PublishModule

// TODO - add local publisher
// TODO - try to use http api instead of curl
// TODO - optionally upload test jars and sources jars
trait SimpleMavenPublish extends JavaModule {

  def repositoryDev : String
  def repositoryRelease : String
  def pomSettings : T[PomSettings]

  def publishVersion = T.input {
    val res = os.proc('git, 'describe, "--tags", "--dirty", "--broken", "--match", "v[0-9]*").call().out.trim
    assert(!res.endsWith("-dirty"), "Git repo is not clean")
    assert(!res.endsWith("-broken"), "Git repo is corrupt")
    res
  }

  def pom = T {
    val pom = Pom(artifactMetadata(), publishXmlDeps(), artifactId(), pomSettings())
    val pomPath = T.ctx().dest / s"${artifactId()}-${publishVersion()}.pom"
    os.write.over(pomPath, pom)
    PathRef(pomPath)
  }

  def artifactMetadata: T[Artifact] = T {
    Artifact(pomSettings().organization, artifactId(), publishVersion())
  }

  def publishXmlDeps = T.task {
    val ivyPomDeps = ivyDeps().map(resolvePublishDependency().apply(_))

    val compileIvyPomDeps = compileIvyDeps()
      .map(resolvePublishDependency().apply(_))
      .filter(!ivyPomDeps.contains(_))
      .map(_.copy(scope = Scope.Provided))

    val modulePomDeps = Task.sequence(moduleDeps.map(_.publishSelfDependency))()

    ivyPomDeps ++ compileIvyPomDeps ++ modulePomDeps.map(Dependency(_, Scope.Compile))
  }

  override def moduleDeps = Seq.empty[SimplePublish]

  def publishSelfDependency = T {
    Artifact(pomSettings().organization, artifactId(), publishVersion())
  }

  def publish(repositoryCreds: String) = T.command {
    val Artifact(group, aid, version) = artifactMetadata()
    val repository = if (version.contains("-")) repositoryDev else repositoryRelease
    val destPrefix = Vector(
      repository.replaceAll("/*$", ""),
      group.replaceAll("\\.", "/"),
      aid,
      version
    ).mkString("", "/", "/")

    val baseName = s"$aid-$version"

    val fromJar = withChecksumFiles(jar().path, s"$baseName.jar", T.ctx().dest)
    val fromPom = withChecksumFiles(pom().path, s"$baseName.pom", T.ctx().dest)

    (fromJar ++ fromPom).foreach {
      case (localFile, destFileName) => uploadFileToRepository(localFile, destPrefix, destFileName, repositoryCreds)
    }
  }

  private def withChecksumFiles(file: Path, fileName: String, dest: Path) = {
    val content = os.read.bytes(file)
    val md5path = dest / s"$fileName.md5"
    val sha1path = dest / s"$fileName.sha1"
    os.write(md5path, md5hex(content))
    os.write(sha1path, sha1hex(content))

    Vector(
      file -> fileName,
      md5path -> md5path.last,
      sha1path -> sha1path.last
    )
  }

  private def uploadFileToRepository(source: Path,
                                     destPrefix: String,
                                     destFileName: String,
                                     credentials: String) : Unit = os
    .proc('curl, "-v", "-u", credentials, "--upload-file", source, destPrefix + destFileName)
    .call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)

  private def md5hex(bytes: Array[Byte]): Array[Byte] = hexArray(md5.digest(bytes)).getBytes
  private def sha1hex(bytes: Array[Byte]): Array[Byte] = hexArray(sha1.digest(bytes)).getBytes
  private def md5 = MessageDigest.getInstance("md5")
  private def sha1 = MessageDigest.getInstance("sha1")
  private def hexArray(arr: Array[Byte]) = String.format("%0" + (arr.length << 1) + "x", new BigInteger(1, arr))
}

