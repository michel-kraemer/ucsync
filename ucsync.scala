// ucsync
// Copyright (c) 2010 Michel Kraemer
//
// This file is released under the terms of the MIT License.
// It is distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
// FOR A PARTICULAR PURPOSE. See the MIT License for more details.
//
// You should have received a copy of the MIT License along with
// this file; if not, goto http://www.michel-kraemer.de/en/mit-license

import java.io._
import java.util.Properties
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.io.Source
import scala.util.control.Exception._
import ch.ethz.ssh2._

/**
 * Executes a remote command
 * @param cmd the command to execute
 * @param sess the SSH session
 * @return the command's output
 */
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

/**
 * Recursively lists a directory's contents
 * @param dir the directory
 * @return a list of all files in the given directory and all its
 * subdirectories
 */
def findFiles(dir: File): List[File] = {
  val files = dir.listFiles()
  val subdirs = files filter { _.isDirectory } flatMap { findFiles }
  (files.toList filterNot { _.isDirectory }) ++ subdirs
}

/**
 * Asks the user if the given files should be copied from the
 * given source
 * @param files the files to copy
 * @param src the source ("local" or "remote")
 * @return a list of files the user confirmed to copy
 */
def askForCopy(files: List[String], src: String) =
  askFor(files, src, "Copy new")

/**
 * Asks the user if the given files should be deleted
 * @param files the files to delete
 * @param t the location of the files ("local" or "remote")
 * @return a list of files the user confirmed to delete
 */
def askForDelete(files: List[String], t: String) =
  askFor(files, t, "Delete")

/**
 * Asks the user something for every given file
 * @param files the files to ask for
 * @param t the location of the files ("local" or "remote")
 * @param pred the "something" to ask (e.g. "copy" or "delete")
 * @return a list of all files the user confirmed
 */
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

/**
 * Converts a value to a SI unit string. Adds the respective suffix
 * (e.g. "M", "k" or "bytes")
 * @param pos the value to convert
 * @return the converted value
 */
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

/**
 * Prints a ASCII progress bar
 * @param pos the current progress
 * @param size the maximum progress
 * @param blockSize the number of bytes the progress will increase
 * between two calls to this method (used to calculate the current
 * speed)
 * @param lastTime the number of milliseconds since epoch when the
 * method was called the last time
 * @return the current number of milliseconds since epoch
 */
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

/**
 * Copies the given local files to the remote directory
 * @param remotepath the remote directory
 * @param localfilestocopy the list of local files to copy
 * @param client the SFTP client used to copy files to the remote host
 * @param conn the respective SSH connection
 */
def copyLocalFiles(remotepath: String, localfilestocopy: List[String])
  (implicit client: SFTPv3Client, conn: Connection) {
  for (file <- localfilestocopy) {
    println("Copying " + file + " to remote...")
    val lastSlash = file.lastIndexOf('/')
    val localdirname = if (lastSlash > 0) file.substring(0, lastSlash) else ""
    
    //make remote directory
    implicit val sess = conn.openSession()
    try {
      exec("mkdir -p \"" + remotepath + localdirname + "\"")
    } finally {
      sess.close
    }
    
    val localfile = new File(file)
    val is = new FileInputStream(localfile)
    val handle = client.createFile(remotepath + file)
    
    //copy file to remote
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

/**
 * Copies the given remote files to the local directory
 * @param remotepath the remote directory
 * @param remotefilestocopy the list of remote files to copy
 * @param client the SFTP client used to copy files to the remote host
 */
def copyRemoteFiles(remotepath: String, remotefilestocopy: List[String])
  (implicit client: SFTPv3Client) {
  for (file <- remotefilestocopy) {
    println("Copying " + file + " from remote...")
    
    //create local directory
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
    
    //copy remote file
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

/**
 * Deletes the given local files
 * @param localfilestodelete the files to delete
 */
def deleteLocalFiles(localfilestodelete: List[String]) {
  for (file <- localfilestodelete) {
    println("Deleting local file " + file + "...")
    val f = new File(file)
    if (!f.delete())
      throw new IllegalStateException("Could not delete file")
  }
}

/**
 * Deletes the given remote files
 * @param remotepath the remote directory
 * @param remotefilestodelete the remote files to delete
 * @param client the SFTP client used to delete files
 */
def deleteRemoteFiles(remotepath: String, remotefilestodelete: List[String])
  (implicit client: SFTPv3Client) {
  for (file <- remotefilestodelete) {
    println("Deleting remote file " + file + "...")
    client.rm(remotepath + file)
  }
}

/**
 * Removes entries from <code>files</code> that start with one of the
 * prefixes in <code>ignoreFiles</code>
 * @param files the list to filter
 * @param ignoreFiles the list of prefixes
 * @return the filtered list
 */
def filterIgnoreFiles(files: List[String], ignoreFiles: List[String]) =
  files filterNot { n => ignoreFiles exists { n.startsWith } }

/**
 * Loads a file that contains a list of strings
 * @param cacheFileName the file to load
 * @return the list of strings stored in the file (one string per line)
 * or an empty list if the files does not exist
 */
def loadCacheFile(cacheFileName: String) = {
  val filesCache = new File(cacheFileName)
  if (filesCache.exists())
    Source.fromFile(filesCache).getLines().toList
  else
    List.empty[String]
}

/**
 * Saves the given list of strings to a file (one string per line)
 * @param cacheFileName the file to save the strings to
 * @param files the list of strings to save
 */
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

//load main configuration file from the current directory
val configFile = new File(configFileName)
if (!configFile.exists) {
  println("Configuration file " + configFileName + " does not exist")
  System.exit(1)
}

val props = new Properties()
props.load(new FileInputStream(configFile))

//get properties from configuration file
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

val ignoreFiles = List(configDir) ++ (props.propertyNames map { _.toString } filter
  { _.startsWith("ignore") } map { k => props.getProperty(k) }).toList

//load cached file lists
val alloldlocalfiles = loadCacheFile(localFilesCacheFileName)
val alloldremotefiles = loadCacheFile(remoteFilesCacheFileName)

//ask user for password if necessary
println("Connecting to ssh://" + (if (user.isEmpty) "" else user + "@") + host + ":" + port)
val pass = if (!user.isEmpty) {
  print("Enter password: ")
  new String(System.console.readPassword())
} else {
  ""
}

//connect to remote host
implicit val conn = new Connection("spamihilator.com", 1302)
conn.connect()
try {
  //authenticate
  val authenticated = if (!user.isEmpty) {
    conn.authenticateWithPassword(user, pass)
  } else {
    true
  }
  
  if (authenticated) {
    //read list of remote files (remove ignored ones)
    implicit val sess = conn.openSession()
    print("Reading remote files... ")
    val allremotefiles = try {
      exec("find \"" + path + "\" -type f").split("\n").toList.map { _.substring(path.length) }
    } finally {
      sess.close()
    }
    val remotefiles = filterIgnoreFiles(allremotefiles, ignoreFiles)
    println(remotefiles.size + " files.")
    
    //read list of local files (remove ignored ones)
    print("Reading local files... ")
    val alllocalfiles = findFiles(new File(".")) map { _.getPath.substring(2).replaceAll("\\\\", "/") }
    val localfiles = filterIgnoreFiles(alllocalfiles, ignoreFiles)
    println(localfiles.size + " files.")
    
    //calculate files to copy and files to delete
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
    
    //ask user what we should do
    val localfilestocopy = askForCopy(newremotefiles, "remote")
    val remotefilestocopy = askForCopy(newlocalfiles, "local")
    val localfilestodelete = askForDelete(deletedremotefiles, "local")
    val remotefilestodelete = askForDelete(deletedlocalfiles, "remote")
    
    println()
    if (localfilestocopy.isEmpty && remotefilestocopy.isEmpty &&
        localfilestodelete.isEmpty && remotefilestodelete.isEmpty) {
      println("Everything up to date.")
    } else {
      //copy files and delete files
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
    
    //cache current file lists
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
