@echo off
rem Builds Origin and starts it on http://localhost:8080 (embedded Apache Tomcat).
rem Needs: JDK 17+, Maven, and MySQL running with the settings in config\origin.properties.
setlocal
cd /d "%~dp0"

rem Auto-detect JDK if JAVA_HOME is invalid or uninstalled
if not defined JAVA_HOME set "JAVA_HOME="
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME="
if not defined JAVA_HOME (
  if exist "C:\Program Files\Java\jdk-26.0.2\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-26.0.2"
  if not defined JAVA_HOME if exist "C:\Program Files\Java\latest\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\latest"
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

rem Auto-detect Maven if not in PATH
where mvn >nul 2>&1
if errorlevel 1 (
  if exist "C:\Dev\apache-maven-3.9.9\bin\mvn.cmd" set "PATH=C:\Dev\apache-maven-3.9.9\bin;%PATH%"
)

rem Check if MySQL is running on port 3306; auto-start if needed
netstat -ano | findstr ":3306 " >nul 2>&1
if errorlevel 1 (
  echo MySQL is not running on port 3306. Starting MySQL...
  if exist "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe" (
    start "" /B "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe" --datadir=D:\mysql-data
    timeout /t 3 /nobreak >nul
  )
)

if not exist config\origin.properties (
  copy config\origin.properties.example config\origin.properties >nul
  echo Created config\origin.properties. Put your MySQL password in it, then run this again.
  exit /b 1
)

call mvn -q compile dependency:build-classpath -Dmdep.outputFile=target\classpath.txt -Dmdep.includeScope=compile
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

for /f "usebackq delims=" %%a in ("target\classpath.txt") do set "DEPS=%%a"
java -cp "target\classes;%DEPS%" np.edu.origin.Launcher

