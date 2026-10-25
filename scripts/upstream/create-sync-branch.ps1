param(
    [string]$Tag = '3.1.0'
)
$ErrorActionPreference = 'Stop'
git fetch upstream --tags --prune
git switch -c "sync/rikkahub-$Tag" origin/main
Write-Output "Created sync/rikkahub-$Tag from origin/main"
