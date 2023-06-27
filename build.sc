import mill._, scalalib._
import coursier.maven.MavenRepository

// TODO - cross build for 2.11 and 2.12 as well
object kahanek extends ScalaModule {
  def scalaVersion = "2.13.1"
  def ivyDeps = Agg(
   ivy"com.lihaoyi::mill:0.5.6"
  )
//  def repositories = super.repositories ++ Seq(
//    MavenRepository("https://jcenter.bintray.com/")
//  )
}
