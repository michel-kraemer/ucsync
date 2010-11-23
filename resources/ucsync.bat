@echo off
setlocal
@for /F "delims=" %%I in ("%~dp0") do @set p=%%~fI
java -jar "%p%\@OUTPUTJAR@"
