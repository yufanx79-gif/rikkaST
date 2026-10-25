param(
    [string]$UpdateJsonPath = "update.json",
    [string]$GradleFilePath = "app/build.gradle.kts"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $UpdateJsonPath)) {
    throw "Missing $UpdateJsonPath"
}
if (-not (Test-Path $GradleFilePath)) {
    throw "Missing $GradleFilePath"
}

$manifest = Get-Content $UpdateJsonPath -Raw | ConvertFrom-Json
$gradleText = Get-Content $GradleFilePath -Raw

if ($manifest.packageName -ne "me.rerere.rikkahub.st") {
    throw "packageName in update.json must be me.rerere.rikkahub.st, got: $($manifest.packageName)"
}
if ($gradleText -notmatch 'applicationId\s*=\s*"me\.rerere\.rikkahub\.st"') {
    throw "applicationId in app/build.gradle.kts must be me.rerere.rikkahub.st"
}
if (-not $manifest.version -or $manifest.version.Trim().Length -eq 0) {
    throw "version in update.json must not be empty"
}
if (-not $manifest.downloads -or $manifest.downloads.Count -eq 0) {
    throw "downloads array in update.json must not be empty"
}

foreach ($dl in $manifest.downloads) {
    if (-not $dl.url.StartsWith("https://")) {
        throw "Download URL must use https:// -> $($dl.url)"
    }
    if (-not $dl.name.EndsWith(".apk")) {
        throw "Download name must end with .apk -> $($dl.name)"
    }
    if ($dl.sha256 -and ($dl.sha256 -notmatch '^[a-fA-F0-9]{64}$')) {
        throw "Invalid sha256 format -> $($dl.sha256)"
    }
}

Write-Host "[OK] Release manifest and Gradle applicationId verified successfully." -ForegroundColor Green
