$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
java -version

.\gradlew.bat --stop
.\gradlew.bat clean packageMsi packageExe