[CmdletBinding()]
param(
    [ValidateSet('local', 'staging')]
    [string]$Profile = 'local',

    [ValidateRange(0, 65535)]
    [int]$LocalDbPort = 0
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$dotEnvPath = Join-Path $repositoryRoot '.env.local'

function Read-DotEnv {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $values
    }
    foreach ($rawLine in Get-Content -LiteralPath $Path -Encoding utf8) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith('#') -or -not $line.Contains('=')) {
            continue
        }
        $key, $value = $line.Split('=', 2)
        $values[$key.Trim().TrimStart([char]0xFEFF)] = $value.Trim().Trim('"').Trim("'")
    }
    return $values
}

function Set-RequiredEnvironmentValue {
    param(
        [hashtable]$Values,
        [string]$Name
    )

    $current = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if (-not $current) {
        $current = $Values[$Name]
    }
    if (-not $current -or $current -like 'replace-with-*') {
        throw "Required environment value is missing: $Name"
    }
    [Environment]::SetEnvironmentVariable($Name, $current, 'Process')
}

$dotEnv = Read-DotEnv -Path $dotEnvPath
Set-RequiredEnvironmentValue -Values $dotEnv -Name 'KTO_SERVICE_KEY'

if ($Profile -eq 'local') {
    Push-Location $repositoryRoot
    try {
        $containerId = (& docker compose -p koready-local ps --status running -q mysql).Trim()
    }
    finally {
        Pop-Location
    }
    if (-not $containerId) {
        throw 'Local KoReady MySQL is not running. Run: docker compose -p koready-local up -d mysql'
    }
    $labels = (& docker inspect --format '{{json .Config.Labels}}' $containerId) | ConvertFrom-Json
    if ($labels.'com.docker.compose.project' -ne 'koready-local' -or
        $labels.'com.docker.compose.service' -ne 'mysql') {
        throw 'The selected container is not the KoReady local MySQL service.'
    }
    $containerValues = @{}
    foreach ($entry in (& docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' $containerId)) {
        if ($entry.Contains('=')) {
            $name, $value = $entry.Split('=', 2)
            $containerValues[$name] = $value
        }
    }
    foreach ($name in @('MYSQL_DATABASE', 'MYSQL_USER', 'MYSQL_PASSWORD')) {
        if (-not $containerValues[$name]) {
            throw "Local MySQL container value is missing: $name"
        }
    }
    if ($LocalDbPort -eq 0) {
        $publishedAddress = (& docker compose -p koready-local port mysql 3306).Trim()
        if ($publishedAddress -notmatch ':(\d+)$') {
            throw 'Could not detect the published KoReady MySQL port.'
        }
        $LocalDbPort = [int]$Matches[1]
    }
    $env:DB_URL = "jdbc:mysql://localhost:$LocalDbPort/$($containerValues['MYSQL_DATABASE'])?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul"
    $env:DB_USERNAME = $containerValues['MYSQL_USER']
    $env:DB_PASSWORD = $containerValues['MYSQL_PASSWORD']
}
else {
    foreach ($name in @('DB_HOST', 'DB_PORT', 'DB_DATABASE', 'DB_USERNAME', 'DB_PASSWORD')) {
        Set-RequiredEnvironmentValue -Values $dotEnv -Name $name
    }
    if (-not $env:DB_SSL_MODE) {
        $env:DB_SSL_MODE = if ($dotEnv['DB_SSL_MODE']) { $dotEnv['DB_SSL_MODE'] } else { 'require' }
    }
}

$javaCommand = Get-Command java -ErrorAction Stop
$detectedJavaHome = Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
$env:JAVA_HOME = $detectedJavaHome

Push-Location $repositoryRoot
try {
    Write-Host "Starting curated onboarding bootstrap: profile=$Profile, approvedPlaces=10"
    & .\gradlew.bat bootstrapCuratedOnboarding "-Pprofile=$Profile" --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw 'Curated onboarding bootstrap failed.'
    }
}
finally {
    Pop-Location
}
