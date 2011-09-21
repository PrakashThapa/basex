@echo off
setlocal

REM Path to this script
set PWD=%~dp0

REM Paths to distributed files (basex.jar, basex-api.jar) or class files
set BASEX=%PWD%/../../basex/target/classes
set BASEXAPI=%PWD%/../target/classes

REM Classpath
set LIB=%PWD%/../lib
set CP=%BASEX%;%BASEXAPI%;%LIB%/commons-beanutils-1.8.2.jar;%LIB%/commons-codec-1.4.jar;%LIB%/commons-fileupload-1.2.2.jar;%LIB%/commons-io-1.4.jar;%LIB%/commons-logging-1.1.1.jar;%LIB%/jdom-1.1.jar;%LIB%/jetty-6.1.26.jar;%LIB%/jetty-util-6.1.26.jar;%LIB%/log4j-1.2.14.jar;%LIB%/lucene-analyzers-3.0.2.jar;%LIB%/milton-api-1.6.4.jar;%LIB%/mime-util-2.1.3.jar;%LIB%/resolver.jar;%LIB%/servlet-api-2.5-20081211.jar;%LIB%/slf4j-api-1.5.8.jar;%LIB%/slf4j-log4j12-1.5.6.jar;%LIB%/snowball.jar;%LIB%/tagsoup-1.2.jar;%LIB%/xmldb-api-1.0.jar;%LIB%/xqj-api-1.0.jar

REM Options for virtual machine
set VM=-Xmx512m

REM Run code
java -cp "%CP%;." %VM% org.basex.api.BaseXHTTP %*
