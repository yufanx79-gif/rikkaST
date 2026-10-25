$ErrorActionPreference = 'Stop'
$buildGradle = Get-Content -Raw 'app/build.gradle.kts'
if ($buildGradle -notmatch 'applicationId\s*=\s*"me\.rerere\.rikkahub\.st"') {
    throw 'applicationId must remain me.rerere.rikkahub.st'
}
if ($buildGradle -notmatch 'versionCode\s*=\s*(239|[2-9][4-9][0-9]|[3-9][0-9]{2,})') {
    throw 'versionCode must be >= 239'
}
Write-Output '[check-release-identity] OK: me.rerere.rikkahub.st (versionCode >= 239)'
