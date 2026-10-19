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
        $GradleUserHome = Join-Path $env:USERPROFILE '.gradle'
    }
}
$taskArguments = @('--gradle-user-home', [IO.Path]::GetFullPath($GradleUserHome), '--no-daemon')
# Java does not automatically inherit the Windows HTTP proxy used by the launcher.
$taskProxy = Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction SilentlyContinue
if ($taskProxy.ProxyEnable -eq 1 -and $taskProxy.ProxyServer -match '^([^:;=]+):(\d+)$') {
    $taskArguments += @('-Dhttps.proxyHost='+$Matches[1], '-Dhttps.proxyPort='+$Matches[2],
        '-Dhttp.proxyHost='+$Matches[1], '-Dhttp.proxyPort='+$Matches[2])
}
if ($Offline) { $taskArguments += @('--offline', '-Dnet.minecraftforge.gradle.check.certs=false') }
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
