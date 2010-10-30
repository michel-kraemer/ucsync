import java.io._
import java.util.Properties
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.io.Source
import scala.util.control.Exception._
import ch.ethz.ssh2._

def exec(cmd: String)(implicit sess: Session) = {
  sess.execCommand(cmd)
  val stdout = new StreamGobbler(sess.getStdout())
  val br = new BufferedReader(new InputStreamReader(stdout))
  var line = ""
  val sb = new StringBuffer()
  while (line != null) {
    line = br.readLine()
    if (line != null) {
      sb.append(line)
      sb.append("\n")
    }
  }
  sb.toString
}

def findFiles(dir: File): List[File] = {
  val files = dir.listFiles()
  val subdirs = files filter { _.isDirectory } flatMap { findFiles }
  (files.toList filterNot { _.isDirectory }) ++ subdirs
}

def askForCopy(files: List[String], t: String) =
  askFor(files, t, "Copy new")

def askForDelete(files: List[String], t: String) =
  askFor(files, t, "Delete")

def askFor(files: List[String], t: String, pred: String) = {
  files flatMap { f =>
    @tailrec def ask(): Boolean = {
      print(pred + " " + t + " file: " + f + " [y|n] ")
      readLine.toLowerCase match {
        case "y" => true
        case "n" => false
        case _ => ask()
      }
    }
    if (ask()) List(f) else Nil
  }
}

def toSI(pos: Long) = {
  import java.util.Locale
  
  val s = if (pos >= 1024 * 1024)
    String.format(Locale.US, "%.1f", (pos / 1024.0 / 1024.0).asInstanceOf[AnyRef])
  else if (pos >= 1024)
    String.format(Locale.US, "%.1f", (pos / 1024.0).asInstanceOf[AnyRef])
  else
    pos.toString
  val t = if (s.endsWith(".0")) s.substring(0, s.length - 2) else s
  if (pos >= 1024 * 1024)
    t + "M"
  else if (pos >= 1024)
    t + "k"
  else
    t + "bytes"
}

def printProgress(pos: Long, size: Long, blockSize: Long, lastTime: Long) = {
  val width = 40
  val n2 = (pos * width / size).toInt
  val posstr = toSI(pos)
  val sizestr = toSI(size)
  val currentTime = System.currentTimeMillis
  val rate = if (currentTime - lastTime > 50) ", " + toSI(1000 * blockSize / (currentTime - lastTime)) + "/s" else ""
  val s = "\r[" + ("=" * n2) + (" " * (width - n2)) + "] " + posstr + " of " + sizestr + rate
  print(s + (" " * (79 - s.length)))
  currentTime
}

def copyLocalFiles(remotepath: String, localfilestocopy: List[String])
  (implicit client: SFTPv3Client, conn: Connection) {
  for (file <- localfilestocopy) {
    println("Copying " + file + " to remote...")
    val lastSlash = file.lastIndexOf('/')
    val localdirname = if (lastSlash > 0) file.substring(0, lastSlash) else ""
    
    implicit val sess = conn.openSession()
    try {
      exec("mkdir -p \"" + remotepath + localdirname + "\"")
    } finally {
      sess.close
    }
    
    val localfile = new File(file)
    val is = new FileInputStream(localfile)
    val handle = client.createFile(remotepath + file)
    
    val size = localfile.length
    val start = System.currentTimeMillis
    val block = 32768
    var read = block
    var pos = 0
    val buf = new Array[Byte](block)
    var last = printProgress(0, size, block, 0)
    while (read > 0) {
      read = is.read(buf)
      if (read > 0) {
        client.write(handle, pos, buf, 0, read)
        pos += read
      }
      last = printProgress(pos, size, block, last)
    }
    println()

    client.closeFile(handle)
    is.close()
  }
}

def copyRemoteFiles(remotepath: String, remotefilestocopy: List[String])
  (implicit client: SFTPv3Client) {
  for (file <- remotefilestocopy) {
    println("Copying " + file + " from remote...")
    val localdir = new File(file.substring(0, file.lastIndexOf('/')))
    if (!localdir.exists()) {
      if (!localdir.mkdirs()) {
        println("Could not create local directory")
        return
      }
    }
    val os = new FileOutputStream(file)
    val handle = client.openFileRO(remotepath + file)
    val attrs = client.fstat(handle)
    
    val size = if (attrs.size != null) attrs.size.longValue else 1
    val start = System.currentTimeMillis
    val block = 32768
    var read = block
    var pos = 0
    val buf = new Array[Byte](block)
    var last = printProgress(0, size, block, 0)
    while (read > 0) {
      read = client.read(handle, pos, buf, 0, block)
      if (read > 0) {
        os.write(buf, 0, read)
        pos += read
      }
      last = printProgress(pos, size, block, last)
    }
    println()

    client.closeFile(handle)
    os.flush()
    os.close()
  }
}

def deleteLocalFiles(localfilestodelete: List[String]) {
  for (file <- localfilestodelete) {
    println("Deleting local file " + file + "...")
    val f = new File(file)
    if (!f.delete())
      throw new IllegalStateException("Could not delete file")
  }
}

def deleteRemoteFiles(remotepath: String, remotefilestodelete: List[String])
  (implicit client: SFTPv3Client) {
  for (file <- remotefilestodelete) {
    println("Deleting remote file " + file + "...")
    client.rm(remotepath + file)
  }
}

def filterIgnoreFiles(files: List[String], ignoreFiles: List[String]) =
  files filterNot { n => ignoreFiles exists { n.startsWith } }

def loadCacheFile(cacheFileName: String) = {
  val filesCache = new File(cacheFileName)
  if (filesCache.exists())
    Source.fromFile(filesCache).getLines().toList
  else
    List.empty[String]
}

def saveCacheFile(cacheFileName: String, files: List[String]) {
  val fos = new FileOutputStream(new File(cacheFileName))
  val bw = new BufferedWriter(new OutputStreamWriter(fos))
  files foreach { l => bw.write(l); bw.newLine() }
  bw.flush()
  fos.flush()
  fos.close()
}

val configDir = ".ucsync"
val configFileName = configDir + "/" + "config.properties"
val localFilesCacheFileName = configDir + "/" + ".localfiles"
val remoteFilesCacheFileName = configDir + "/" + ".remotefiles"

val configFile = new File(configFileName)
if (!configFile.exists) {
  println("Configuration file " + configFileName + " does not exist")
  System.exit(1)
}

val props = new Properties()
props.load(new FileInputStream(configFile))

val host = props.getProperty("host", "")
if (host.isEmpty) {
  println(configFileName + " does not declare a remote host")
  println("Please provide the property 'host'")
  System.exit(1)
}

val pathProp = props.getProperty("path", "")
if (pathProp.isEmpty) {
  println(configFileName + " does not declare a remote path")
  println("Please provide the property 'path'")
  System.exit(1)
}
val path = if (pathProp.endsWith("/")) pathProp else pathProp + "/"

val user = props.getProperty("user", "")
val strPort = props.getProperty("port", "22")
val port = try { strPort.toInt } catch { case _: NumberFormatException =>
  println("Invalid port: " + strPort)
  System.exit(1)
}

val ignoreFiles = List(".ucsync") ++ (props.propertyNames map { _.toString } filter
  { _.startsWith("ignore") } map { k => props.getProperty(k) }).toList

val alloldlocalfiles = loadCacheFile(localFilesCacheFileName)
val alloldremotefiles = loadCacheFile(remoteFilesCacheFileName)

println("Connecting to ssh://" + (if (user.isEmpty) "" else user + "@") + host + ":" + port)
val pass = if (!user.isEmpty) {
  print("Enter password: ")
  new String(System.console.readPassword())
} else {
  ""
}

//get remote files
implicit val conn = new Connection("spamihilator.com", 1302)
conn.connect()
try {
  val authenticated = if (!user.isEmpty) {
    conn.authenticateWithPassword(user, pass)
  } else {
    true
  }
  
  if (authenticated) {
    implicit val sess = conn.openSession()
    print("Reading remote files... ")
    val allremotefiles = try {
      exec("find \"" + path + "\" -type f").split("\n").toList.map { _.substring(path.length) }
    } finally {
      sess.close()
    }
    val remotefiles = filterIgnoreFiles(allremotefiles, ignoreFiles)
    println(remotefiles.size + " files.")
    
    print("Reading local files... ")
    val alllocalfiles = findFiles(new File(".")) map { _.getPath.substring(2).replaceAll("\\\\", "/") }
    val localfiles = filterIgnoreFiles(alllocalfiles, ignoreFiles)
    println(localfiles.size + " files.")
    
    println("Building file list ...")
    val oldremotefiles = filterIgnoreFiles(alloldremotefiles, ignoreFiles)
    val oldlocalfiles = filterIgnoreFiles(alloldlocalfiles, ignoreFiles)
    val deletedremotefiles = oldremotefiles filterNot { remotefiles.contains }
    val deletedlocalfiles = oldlocalfiles filterNot { localfiles.contains }
    val newremotefiles = localfiles filterNot { a =>
      (remotefiles contains a) || (deletedremotefiles contains a)
    }
    val newlocalfiles = remotefiles filterNot { a =>
      (localfiles contains a) || (deletedlocalfiles contains a)
    }
    
    val localfilestocopy = askForCopy(newremotefiles, "remote")
    val remotefilestocopy = askForCopy(newlocalfiles, "local")
    val localfilestodelete = askForDelete(deletedremotefiles, "local")
    val remotefilestodelete = askForDelete(deletedlocalfiles, "remote")
    
    println()
    if (localfilestocopy.isEmpty && remotefilestocopy.isEmpty &&
        localfilestodelete.isEmpty && remotefilestodelete.isEmpty) {
      println("Everything up to date.")
    } else {
      implicit val client = new SFTPv3Client(conn)
      try {
        copyLocalFiles(path, localfilestocopy)
        copyRemoteFiles(path, remotefilestocopy)
        deleteLocalFiles(localfilestodelete)
        deleteRemoteFiles(path, remotefilestodelete)
      } finally {
        client.close()
      }
    }
    
    val localfilestosave = (alllocalfiles filterNot { localfilestodelete.contains }) ++ remotefilestocopy
    val remotefilestosave = (allremotefiles filterNot { remotefilestodelete.contains }) ++ localfilestocopy
    saveCacheFile(localFilesCacheFileName, localfilestosave)
    saveCacheFile(remoteFilesCacheFileName, remotefilestosave)
  } else {
    println("Invalid password or username")
  }
} finally {
  conn.close()
}
