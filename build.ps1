param([switch]$Offline, [string]$ResourceTestRoot)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a Java 25 JDK.' }
$buildArgs = @('-p', (Join-Path $PSScriptRoot 'fabric'), '--no-daemon', '--console=plain', 'build')
if ($ResourceTestRoot) { $buildArgs += @('migrationTest', "-PresourceTestRoot=$ResourceTestRoot") }
if ($Offline) { $buildArgs += '--offline' }
& (Join-Path $PSScriptRoot 'gradlew.bat') @buildArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
