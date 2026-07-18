[CmdletBinding()]
param(
    [string]$ProjectName = 'koready-local'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$seedFile = Join-Path $PSScriptRoot 'seed-local-places.sql'
$containerSeedFile = '/tmp/koready-seed-local-places.sql'

Push-Location $repositoryRoot
try {
    $containerId = (& docker compose -p $ProjectName ps --status running -q mysql).Trim()
    if (-not $containerId) {
        throw "Local Docker MySQL is not running. Run: docker compose -p $ProjectName up -d mysql"
    }

    $labels = (& docker inspect --format '{{json .Config.Labels}}' $containerId) | ConvertFrom-Json
    $serviceName = $labels.'com.docker.compose.service'
    $actualProject = $labels.'com.docker.compose.project'
    if ($serviceName -ne 'mysql' -or $actualProject -ne $ProjectName) {
        throw 'The selected container is not the KoReady Compose MySQL service.'
    }

    & docker compose -p $ProjectName cp $seedFile "mysql:$containerSeedFile"
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to copy the local seed into the MySQL container.'
    }

    & docker compose -p $ProjectName exec -T mysql sh -c `
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" < /tmp/koready-seed-local-places.sql'
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to apply the local place seed.'
    }

    Write-Host 'Local place seed applied: 10 demo places.'
}
finally {
    & docker compose -p $ProjectName exec -T mysql rm -f $containerSeedFile 2>$null | Out-Null
    Pop-Location
}
