import sbt._
import java.net.URL

class UcsyncProject(info: ProjectInfo) extends DefaultProject(info) {
  //download ganymed if needed
  val ganymedVersion = "build250"
  val ganymedJarPath = "ganymed-ssh2-" + ganymedVersion
  val ganymedJar = "ganymed-ssh2-" + ganymedVersion + ".jar"
  val ganymedPath = dependencyPath / ganymedJarPath / ganymedJar
  val ganymedURL = new URL("http://www.cleondris.ch/ssh2/ganymed-ssh2-" + ganymedVersion + ".zip")
  if (!ganymedPath.exists) {
    log.info("Downloading and extracting Ganymed SSH-2 for Java " + ganymedVersion + " ...")
    FileUtilities.unzip(ganymedURL, dependencyPath, ganymedJarPath + "/" + ganymedJar, log)
  }
  
  //omit scala version
  override def outputPath = "target"
  override def moduleID = "ucsync"
  
  var mcp: Option[String] = None
  override def manifestClassPath = mcp
  
  val outputJar = outputPath / defaultJarName
  
  //When "package" is called, the jar file won't have a classpath in
  //its manifest. When "package-dist" is called instead it will have
  //one. So, always delete the packaged jar file before packaging.
  lazy val deletePackagedJar = task {
    log.info("Deleting " + outputJar + " ...")
    outputJar.asFile.delete()
    None
  }
  override def packageAction = super.packageAction dependsOn deletePackagedJar
  
  //add dependencies to manifest classpath
  lazy val preparePackageDist = task {
    log.info("Preparing manifest classpath ...")
    val scalaLibraryJar = mainDependencies.scalaLibrary.get.toList
    mcp = Some("lib/" + scalaLibraryJar(0).name + " lib/" + ganymedJar)
    None
  } dependsOn compile
  lazy val packageDist = `package` dependsOn preparePackageDist
  
  //create zip file for distribution
  lazy val dist = task {
    val distPath = outputPath / "dist"
    val distLibPath = distPath / "lib"
    distPath.asFile.mkdirs()
    distLibPath.asFile.mkdirs()
    
    val scalaLibraryJar = mainDependencies.scalaLibrary
    val dependencies = scalaLibraryJar +++ ganymedPath
    
    val distZipName = artifactBaseName + ".zip"
    val distZipPath = outputPath / distZipName
    
    val resources = "resources".*** --- "resources"
    val licenses = ((".": Path) * "LICENSE*") +++ "NOTICE"
    
    resources.get.map { r =>
      //copy lanucher scripts and replace jar file within them
      FileUtilities.readString(r.asFile, log) match {
        case Left(s) =>
          //error
          Some(s)
        case Right(s) =>
          //replace @OUTPUTJAR@ by the name of the jar file and
          //write script to dist directory
          val ns = s.replaceAll("@OUTPUTJAR@", outputJar.name)
          FileUtilities.write((distPath / r.name).asFile, ns, log)
      }
    } find(_ != None) getOrElse
    FileUtilities.copyFlat(dependencies.get, distLibPath, log).left.toOption orElse
    FileUtilities.copyFlat(outputJar.get, distPath, log).left.toOption orElse
    FileUtilities.copyFlat(licenses.get, distPath, log).left.toOption orElse
    FileUtilities.zip((distPath ##).get, distZipPath, true, log)
  } dependsOn(packageDist)
}
