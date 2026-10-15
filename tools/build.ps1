param(
    [string]$GradleUserHome = '',
    [switch]$Offline,
    [string[]]$Tasks = @('jar')
)
$ErrorActionPreference = 'Stop'
$taskWorkspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($GradleUserHome)) {
    if (-not [string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) {
        $GradleUserHome = $env:GRADLE_USER_HOME
    } else {
        $taskMcreatorCache = Join-Path $env:USERPROFILE '.mcreator\gradle'
        $GradleUserHome = if (Test-Path -LiteralPath (Join-Path $taskMcreatorCache 'caches\modules-2')) {
            $taskMcreatorCache
        } else {
            Join-Path $env:USERPROFILE '.gradle'
        }
    }
}
$taskArguments = @('--gradle-user-home', [IO.Path]::GetFullPath($GradleUserHome), '--no-daemon')
if ($Offline) { $taskArguments += '--offline' }
$taskArguments += $Tasks
Write-Output ('Gradle user home: '+[IO.Path]::GetFullPath($GradleUserHome))
Push-Location -LiteralPath $taskWorkspace
try {
    & (Join-Path $taskWorkspace 'gradlew.bat') @taskArguments
    $taskExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $taskExitCode
