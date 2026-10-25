param(
    [Parameter(Mandatory=$true)][string]$InstanceRoot,
    [switch]$ReplaceSameVersion
)
$ErrorActionPreference = 'Stop'
$taskWorkspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskInstance = (Resolve-Path -LiteralPath $InstanceRoot).Path.TrimEnd('\')
$taskMods = (Resolve-Path -LiteralPath (Join-Path $taskInstance 'mods')).Path
$taskReports = Join-Path $taskWorkspace 'build\reports'
$taskInventory = Get-Content -LiteralPath (Join-Path $taskReports 'package-inventory.json') -Raw -Encoding utf8 | ConvertFrom-Json
if ($taskInventory.issues.Count -gt 0 -or $taskInventory.version -notmatch '^\d+\.\d+\.\d+(?:\.\d+)?$') { throw 'Release inventory is invalid.' }
$taskSource = Join-Path $taskWorkspace ('build\libs\killstreaks-forge-1.20.1-'+$taskInventory.version+'.jar')
$taskNewHash = (Get-FileHash -LiteralPath $taskSource -Algorithm SHA256).Hash.ToLowerInvariant()
if ($taskInventory.jar.sha256 -ne $taskNewHash) { throw 'Production JAR differs from the inventory.' }
$taskModPattern = '^(?<prefix>(?:killstreakcod(?:-forge-\d+\.\d+\.\d+)?|killstreaks-(?:neoforge|forge)-\d+\.\d+\.\d+)-)(?<version>\d+\.\d+\.\d+(?:\.\d+)?)\.jar$'
$taskInstalled = @(Get-ChildItem -LiteralPath $taskMods -File -Filter '*.jar' | Where-Object { $_.Name -match $taskModPattern })
if ($taskInstalled.Count -gt 1) { throw 'Multiple killstreak JARs are installed.' }
$taskNewName = [IO.Path]::GetFileName($taskSource)
# 安装文件名统一采用当前构建产物，旧文件名用于识别和备份。
$taskNew = Join-Path $taskMods $taskNewName
$taskBackup = Join-Path $taskInstance ('renderer-mod-backups\'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+$taskInventory.version)
$taskStage = Join-Path $taskBackup ([IO.Path]::GetFileNameWithoutExtension($taskSource)+'.pending')
foreach ($taskPath in @($taskMods,$taskNew,$taskBackup,$taskStage)) {
    if (-not [IO.Path]::GetFullPath($taskPath).StartsWith($taskInstance+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected instance path.' }
}
if (Get-Process -Name java,javaw -ErrorAction SilentlyContinue) { throw 'Java is running; save and exit Minecraft before installing.' }
if (Test-Path -LiteralPath $taskNew) {
    if ($taskInstalled.Count -eq 1 -and (Get-FileHash -LiteralPath $taskNew -Algorithm SHA256).Hash.ToLowerInvariant() -eq $taskNewHash) {
        Write-Output ('Matching '+$taskInventory.version+' is already installed.')
        exit 0
    }
    # Same-version resource corrections still keep the full backup and rollback path.
    # 中文：同版本資源修訂仍完整備份舊包，並沿用失敗還原流程。
    if (-not $ReplaceSameVersion -or $taskInstalled.Count -ne 1 -or $taskInstalled[0].FullName -ne $taskNew) {
        throw 'The new filename already exists with different bytes. Use -ReplaceSameVersion for an intentional resource correction.'
    }
}
if (Test-Path -LiteralPath $taskBackup) { throw 'Backup directory already exists.' }
$taskOtherMods = @(Get-ChildItem -LiteralPath $taskMods -File -Filter '*.jar' | Where-Object { $_.Name -notmatch $taskModPattern } | ForEach-Object {
    [ordered]@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
})
$taskRetained = @()
foreach ($taskFolder in @('config','killstreakcod-skins')) {
    $taskDirectory = Join-Path $taskInstance $taskFolder
    if (Test-Path -LiteralPath $taskDirectory) { $taskRetained += @(Get-ChildItem -LiteralPath $taskDirectory -File -Recurse) }
}
$taskSaves = Join-Path $taskInstance 'saves'
if (Test-Path -LiteralPath $taskSaves) {
    foreach ($taskWorld in Get-ChildItem -LiteralPath $taskSaves -Directory) {
        $taskWorldConfig = Join-Path $taskWorld.FullName 'serverconfig\killstreakcod-server.toml'
        if (Test-Path -LiteralPath $taskWorldConfig) { $taskRetained += Get-Item -LiteralPath $taskWorldConfig }
    }
}
$taskRetainedFiles = @($taskRetained | ForEach-Object {
    [ordered]@{relative=$_.FullName.Substring($taskInstance.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
})
$taskSaved = $null
$taskOld = $null
$taskBackupRecord = $null
if ($taskInstalled.Count -eq 1) {
    $taskOld = $taskInstalled[0].FullName
    $taskSaved = Join-Path $taskBackup $taskInstalled[0].Name
    if (-not [IO.Path]::GetFullPath($taskOld).StartsWith($taskMods+'\',[StringComparison]::OrdinalIgnoreCase) -or -not [IO.Path]::GetFullPath($taskSaved).StartsWith($taskBackup+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected old release path.' }
    $taskBackupRecord = [ordered]@{path=$taskSaved;sha256=(Get-FileHash -LiteralPath $taskOld -Algorithm SHA256).Hash.ToLowerInvariant()}
}
[IO.Directory]::CreateDirectory($taskBackup) | Out-Null
Copy-Item -LiteralPath $taskSource -Destination $taskStage
if ((Get-FileHash -LiteralPath $taskStage -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskNewHash) { throw 'Staged file differs.' }
if (Get-Process -Name java,javaw -ErrorAction SilentlyContinue) { throw 'Java started; installed files have not been changed.' }
try {
    if ($taskOld) { Move-Item -LiteralPath $taskOld -Destination $taskSaved }
    Move-Item -LiteralPath $taskStage -Destination $taskNew
    if ((Get-FileHash -LiteralPath $taskNew -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskNewHash) { throw 'Installed file differs.' }
} catch {
    if (Test-Path -LiteralPath $taskNew) { Move-Item -LiteralPath $taskNew -Destination $taskStage }
    if ($taskSaved -and (Test-Path -LiteralPath $taskSaved)) { Move-Item -LiteralPath $taskSaved -Destination $taskOld }
    throw
}
$taskRecord = [ordered]@{
    version=$taskInventory.version;instance=$taskInstance
    installed=[ordered]@{path=$taskNew;sha256=$taskNewHash};backup=$taskBackupRecord
    retainedMods=$taskOtherMods;retainedFiles=$taskRetainedFiles;gameLaunched=$false
}
$taskJson = $taskRecord | ConvertTo-Json -Depth 6
$taskJson | Set-Content -LiteralPath (Join-Path $taskReports 'instance-update.json') -Encoding utf8
$taskJson | Set-Content -LiteralPath (Join-Path $taskBackup 'update.json') -Encoding utf8
Write-Output ('Installed '+$taskNew)
if ($taskSaved) { Write-Output ('Backup '+$taskSaved) }
