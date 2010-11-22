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
}
