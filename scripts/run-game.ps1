[CmdletBinding()]
param([string]$JdkRoot)

& (Join-Path $PSScriptRoot 'invoke-gradle.ps1') -JdkRoot $JdkRoot -Tasks @('run')
