@echo off
setlocal
set APP_HOME=%~dp0
if "%JAVA_HOME%"=="" (
  set JAVA_EXE=java
) else (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
)
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if not exist "%CLASSPATH%" (
  echo Gradle wrapper JAR not found. Downloading...
  powershell -Command "Invoke-WebRequest -Uri https://repo1.maven.org/maven2/org/gradle/gradle-wrapper/8.10/gradle-wrapper-8.10.jar -OutFile '%CLASSPATH%'"
)
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
