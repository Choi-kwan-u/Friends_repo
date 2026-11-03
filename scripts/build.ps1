[CmdletBinding()]
param([string]$JdkRoot)

# Compatibility entry point: compile the game and copy its resources.
& (Join-Path $PSScriptRoot 'invoke-gradle.ps1') -JdkRoot $JdkRoot -Tasks @('classes')
