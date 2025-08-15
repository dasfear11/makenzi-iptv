@ECHO OFF
SET DIR=%~dp0
SET CLASSPATH=%DIR%gradle\wrapper\gradle-wrapper.jar
IF EXIST "%JAVA_HOME%\bin\java.exe" (
  "%JAVA_HOME%\bin\java.exe" -Xmx64m -cp "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
) ELSE (
  java -Xmx64m -cp "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
)
