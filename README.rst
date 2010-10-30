================
ucsync
================

This little tool synchronizes a local directory with a remote one. It's
a perfect tool for users who just want to keep two directories in sync
without all that additional stuff that other tools provide.

- Supports SSH-2 and SFTP
- Copies new local files to the remote directory
- Copies new remote files to the local directory
- Deletes remote files deleted locally
- Deletes local files deleted remotely
- Asks the user before it does anything
- Does nothing more and nothing less

The tool is very small and fast and does exactly what it says. If you
want more control, consider using another tool like
`unison <http://www.cis.upenn.edu/~bcpierce/unison/>`_.

Usage
-----

#. Download `Ganymed SSH-2 for Java <http://www.cleondris.ch/opensource/ssh2/>`_
   (at least build 250) and unzip the JAR file
#. Go to the local directory you want to synchronize
#. Create a new sub-directory called ``.ucsync``
#. Create the main configuration file ``.ucsync/config.properties`` (see
   below for all configuration options)
#. Run the following command from the local directory you want to
   synchronize::

     scala -cp ganymed-ssh2-build250.jar ucsync.scala

   (Of course, you may have to add the path where you put the Ganymed
   jar file and ``ucsync.scala``)
#. Let ucsync do the rest.

Configuration options (.ucsync/config.properties)
-------------------------------------------------

ucsync's configuration is stored in a simple properties file. For
example::

  host=server.com
  port=22
  user=johndoe
  path=/home/johndoe/dir_in_sync
  ignore.1=idontwannhavethat.txt
  ignore.2=verybigfile.iso

This is a complete list of all available options:

host
  The remote host to connect to

port
  The remote port to connect to

user
  The user name used to login to the remote host

path
  The absolute path of the remote directory to keep in sync

ignore.x
  Absolute paths to files that should be ignored. Replace ``x`` by
  consecutive numbers to ignore more than one file (for example:
  ``ignore.1``, ``ignore.2`` and so on).

TODO
----

- Compare files by size, date or even content
- Compile a jar that contains everything needed to run the program and
  put that into the project's download section

License
-------

This work is licensed under the
`MIT license <http://www.opensource.org/licenses/mit-license.php>`_:

Copyright (c) 2010 Michel Kraemer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
