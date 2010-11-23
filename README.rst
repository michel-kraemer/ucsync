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

#. `Download <https://github.com/michel-kraemer/ucsync/downloads>`_
   the distribution zip file
#. Unpack the zip file to any directory. Optionally add this directory
   to your path
#. Go to the local directory you want to synchronize
#. Create a new sub-directory called ``.ucsync``
#. Create the main configuration file ``.ucsync/config.properties`` (see
   below for all configuration options)
#. Run the ``ucsync.bat`` or ``ucsync.sh`` from the local directory you
   want to synchronize
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
