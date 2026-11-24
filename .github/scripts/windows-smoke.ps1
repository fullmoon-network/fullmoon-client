$ErrorActionPreference = "Stop"

function Assert-Equal {
    param(
        [Parameter(Mandatory = $true)] $Actual,
        [Parameter(Mandatory = $true)] $Expected,
        [Parameter(Mandatory = $true)] [string] $Label
    )

    if ($Actual -ne $Expected) {
        throw "$Label expected '$Expected', got '$Actual'"
    }
}

$installers = @(Get-ChildItem "launcher/src-tauri/target/release/bundle/nsis/*.exe")
Assert-Equal $installers.Count 1 "installer count"

$catalog = Get-Content "launcher/src-tauri/resources/catalog.json" -Raw | ConvertFrom-Json
Assert-Equal @($catalog.servers).Count 1 "bundled server count"
Assert-Equal $catalog.servers[0].id "fullmoon-lobby" "bundled server id"
Assert-Equal $catalog.servers[0].address "play.fullmoon.ink" "bundled server address"

$smokeRoot = Join-Path $env:RUNNER_TEMP "fullmoon-installer-smoke"
$installRoot = Join-Path $smokeRoot "install"
$dataRoot = Join-Path $smokeRoot "profile"
$resultPath = Join-Path $PWD "windows-smoke-result.json"

if (Test-Path $smokeRoot) {
    Remove-Item $smokeRoot -Recurse -Force
}
New-Item $smokeRoot -ItemType Directory -Force | Out-Null

$installerProcess = Start-Process `
    -FilePath $installers[0].FullName `
    -ArgumentList @("/S", "/D=$installRoot") `
    -Wait `
    -PassThru
Assert-Equal $installerProcess.ExitCode 0 "silent installer exit code"

$launchers = @(Get-ChildItem $installRoot -Filter "fullmoon.exe" -Recurse)
Assert-Equal $launchers.Count 1 "installed launcher count"

$bundledMods = @(Get-ChildItem $installRoot -Filter "fullmoon-client.jar" -Recurse)
Assert-Equal $bundledMods.Count 1 "bundled mod count"
$stagedMod = Get-Item "launcher/src-tauri/resources/mods/fullmoon-client.jar"
$stagedModSha256 = (Get-FileHash $stagedMod.FullName -Algorithm SHA256).Hash.ToLower()
$bundledModSha256 = (Get-FileHash $bundledMods[0].FullName -Algorithm SHA256).Hash.ToLower()
Assert-Equal $bundledModSha256 $stagedModSha256 "installed bundled mod hash"

$env:FULLMOON_DATA_ROOT = $dataRoot
$launcherProcess = $null
$startedAt = Get-Date

try {
    $launcherProcess = Start-Process `
        -FilePath $launchers[0].FullName `
        -WorkingDirectory $installRoot `
        -Environment @{ FULLMOON_DATA_ROOT = $dataRoot } `
        -PassThru
    $instancesPath = Join-Path $dataRoot "instances.json"
    $deadline = (Get-Date).AddSeconds(90)

    while (-not (Test-Path $instancesPath) -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 500
        $launcherProcess.Refresh()
        if ($launcherProcess.HasExited) {
            throw "launcher exited before creating instances.json with code $($launcherProcess.ExitCode)"
        }
    }

    if (-not (Test-Path $instancesPath)) {
        throw "launcher did not create instances.json within 30 seconds"
    }

    $instances = @(Get-Content $instancesPath -Raw | ConvertFrom-Json)
    Assert-Equal $instances.Count 1 "first-run instance count"
    Assert-Equal $instances[0].id "fullmoon-managed" "first-run instance id"
    Assert-Equal $instances[0].quickPlayServer "play.fullmoon.ink" "quick-play server"
    Assert-Equal $instances[0].versionId "26.1.2" "managed Minecraft version"

    Start-Sleep -Seconds 3
    $launcherProcess.Refresh()
    if ($launcherProcess.HasExited) {
        throw "launcher exited during smoke test with code $($launcherProcess.ExitCode)"
    }

    $result = [ordered]@{
        installer = $installers[0].Name
        installerSha256 = (Get-FileHash $installers[0].FullName -Algorithm SHA256).Hash.ToLower()
        bundledModSha256 = $bundledModSha256
        instanceId = $instances[0].id
        versionId = $instances[0].versionId
        quickPlayServer = $instances[0].quickPlayServer
        launchSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
    }
    $result | ConvertTo-Json | Set-Content $resultPath
    Get-Content $resultPath
}
finally {
    if ($null -ne $launcherProcess) {
        $launcherProcess.Refresh()
        if (-not $launcherProcess.HasExited) {
            Stop-Process -Id $launcherProcess.Id -Force
        }
    }
}
