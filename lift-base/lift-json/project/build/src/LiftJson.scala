import sbt._

class LiftJson(info: ProjectInfo) extends DefaultProject(info) {
  val paranamer  = "com.thoughtworks.paranamer" % "paranamer" % "2.0" % "compile->default"
  val junit      = "junit" % "junit" % "4.5"
  val specs      = "org.scala-tools.testing" % "specs" % "1.5.0"
  val scalacheck = "org.scala-tools.testing" % "scalacheck" % "1.5"

  override def crossScalaVersions = Set("2.7.4", "2.7.5")
  
  override def ivyXML =
    <publications>
      <artifact name="lift-json" type="jar" ext="jar"/>
    </publications>
}
