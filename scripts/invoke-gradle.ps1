[CmdletBinding()]
param(
    [string]$JdkRoot,
    [Parameter(Mandatory = $true)]
    [string[]]$Tasks
)

$ErrorActionPreference = 'Stop'
$taskRepoRoot = Split-Path -Parent $PSScriptRoot
$taskPreviousJavaHome = $env:JAVA_HOME
try {
    if ($JdkRoot) {
        if (-not (Test-Path -LiteralPath (Join-Path $JdkRoot 'bin/javac.exe') -PathType Leaf)) {
            throw '-JdkRoot must point to a JDK installation, not a JRE or bin directory.'
        }
        $env:JAVA_HOME = $JdkRoot
    }
    & (Join-Path $taskRepoRoot 'gradlew.bat') -p $taskRepoRoot --console=plain @Tasks
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE."
    }
} finally {
    $env:JAVA_HOME = $taskPreviousJavaHome
}
