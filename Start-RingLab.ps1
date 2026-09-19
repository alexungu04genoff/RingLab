[CmdletBinding()]
param(
    [switch]$SkipMain,
    [int]$DockerTimeoutSeconds = 120,
    [int]$PostgresTimeoutSeconds = 90,
    [int]$ApplicationTimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

$script:RepositoryRoot = $PSScriptRoot
if (-not $script:RepositoryRoot) {
    $script:RepositoryRoot = (Get-Location).Path
}

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$ArgumentList = @(),
        [string]$WorkingDirectory
    )

    $originalLocation = Get-Location
    $originalErrorPreference = $ErrorActionPreference
    try {
        if ($WorkingDirectory) {
            Set-Location -LiteralPath $WorkingDirectory
        }
        # Windows PowerShell 5.1 wraps native stderr as nonterminating ErrorRecord objects.
        # Capture that diagnostic stream and decide success only from the native exit code.
        $ErrorActionPreference = "Continue"
        $lines = @(& $FilePath @ArgumentList 2>&1)
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $originalErrorPreference
        if ($WorkingDirectory) {
            Set-Location -LiteralPath $originalLocation
        }
    }

    [pscustomobject]@{
        ExitCode = $exitCode
        Output = (($lines | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine).Trim()
    }
}

function Get-RequiredCommandPath {
    param([Parameter(Mandatory = $true)][string]$Name)

    $command = Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $command) {
        throw "Required command '$Name' was not found on PATH. Install it and open a new terminal."
    }
    return $command.Source
}

function Wait-ForCondition {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Probe,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [int]$PollSeconds = 2
    )

    $poll = [Math]::Max(1, $PollSeconds)
    $attempts = [Math]::Max(1, [int][Math]::Ceiling($TimeoutSeconds / $poll) + 1)
    for ($attempt = 0; $attempt -lt $attempts; $attempt++) {
        if (& $Probe) {
            return $true
        }
        if ($attempt -lt ($attempts - 1)) {
            Start-Sleep -Seconds $poll
        }
    }
    return $false
}

function Assert-LocalDockerContext {
    param([Parameter(Mandatory = $true)][string]$DockerPath)

    $context = Invoke-NativeCommand -FilePath $DockerPath -ArgumentList @(
        "context", "inspect", "--format", "{{.Endpoints.docker.Host}}"
    )
    if ($context.ExitCode -ne 0) {
        throw "Docker context inspection failed: $($context.Output)"
    }

    $endpoint = $context.Output.Trim()
    if ($endpoint -notmatch '^npipe:') {
        throw "Docker is using non-local endpoint '$endpoint'. Switch to the local Docker Desktop context before starting RingLab."
    }
    return $endpoint
}

function Test-DockerEngineReady {
    param([Parameter(Mandatory = $true)][string]$DockerPath)

    $probe = Invoke-NativeCommand -FilePath $DockerPath -ArgumentList @(
        "info", "--format", "{{.ServerVersion}}"
    )
    return $probe.ExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($probe.Output)
}

function Get-DockerDesktopPath {
    $candidates = @()
    if ($env:ProgramFiles) {
        $candidates += (Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe")
    }
    if (${env:ProgramW6432}) {
        $candidates += (Join-Path ${env:ProgramW6432} "Docker\Docker\Docker Desktop.exe")
    }
    if ($env:LOCALAPPDATA) {
        $candidates += (Join-Path $env:LOCALAPPDATA "Programs\Docker\Docker\Docker Desktop.exe")
        $candidates += (Join-Path $env:LOCALAPPDATA "Programs\DockerDesktop\Docker Desktop.exe")
    }
    return $candidates | Select-Object -Unique | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}

function Start-DockerDesktop {
    param([Parameter(Mandatory = $true)][string]$DockerDesktopPath)
    Start-Process -FilePath $DockerDesktopPath | Out-Null
}

function Ensure-DockerReady {
    param([int]$TimeoutSeconds = 120)

    $docker = Get-RequiredCommandPath "docker.exe"
    $compose = Invoke-NativeCommand -FilePath $docker -ArgumentList @("compose", "version")
    if ($compose.ExitCode -ne 0) {
        throw "Docker Compose is unavailable: $($compose.Output)"
    }

    Assert-LocalDockerContext -DockerPath $docker | Out-Null
    if (Test-DockerEngineReady -DockerPath $docker) {
        Write-Host "Docker Desktop engine is ready." -ForegroundColor Green
        return $docker
    }

    $desktop = Get-DockerDesktopPath
    if (-not $desktop) {
        throw "The local Docker engine is unavailable and Docker Desktop was not found in a supported installation path. Start Docker Desktop manually, then rerun .\Start-RingLab.ps1."
    }

    Write-Host "Docker engine is unavailable. Starting Docker Desktop..."
    Start-DockerDesktop -DockerDesktopPath $desktop
    $ready = Wait-ForCondition -TimeoutSeconds $TimeoutSeconds -PollSeconds 2 -Probe {
        Test-DockerEngineReady -DockerPath $docker
    }
    if (-not $ready) {
        throw "Docker Desktop did not expose its local engine within $TimeoutSeconds seconds. Open Docker Desktop and resolve its startup error, then rerun .\Start-RingLab.ps1."
    }

    Write-Host "Docker Desktop engine is ready." -ForegroundColor Green
    return $docker
}

function Test-PostgresHealthy {
    param(
        [Parameter(Mandatory = $true)][string]$DockerPath,
        [Parameter(Mandatory = $true)][string]$ComposePath
    )

    $container = Invoke-NativeCommand -FilePath $DockerPath -ArgumentList @(
        "compose", "-f", $ComposePath, "ps", "-q", "postgres"
    )
    if ($container.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($container.Output)) {
        return $false
    }

    $containerId = ($container.Output -split '\r?\n')[0].Trim()
    $health = Invoke-NativeCommand -FilePath $DockerPath -ArgumentList @(
        "inspect", "--format", "{{.State.Health.Status}}", $containerId
    )
    $script:LastPostgresHealth = $health.Output
    return $health.ExitCode -eq 0 -and $health.Output.Trim() -eq "healthy"
}

function Ensure-PostgresReady {
    param(
        [Parameter(Mandatory = $true)][string]$DockerPath,
        [int]$TimeoutSeconds = 90
    )

    $composePath = Join-Path $script:RepositoryRoot "compose.yaml"
    if (-not (Test-Path -LiteralPath $composePath -PathType Leaf)) {
        throw "Local Compose file not found: $composePath"
    }

    $start = Invoke-NativeCommand -FilePath $DockerPath -ArgumentList @(
        "compose", "-f", $composePath, "up", "-d", "postgres"
    ) -WorkingDirectory $script:RepositoryRoot
    if ($start.ExitCode -ne 0) {
        throw "Local PostgreSQL failed to start: $($start.Output)"
    }

    $healthy = Wait-ForCondition -TimeoutSeconds $TimeoutSeconds -PollSeconds 2 -Probe {
        Test-PostgresHealthy -DockerPath $DockerPath -ComposePath $composePath
    }
    if (-not $healthy) {
        $detail = if ($script:LastPostgresHealth) { $script:LastPostgresHealth } else { "no container health status was returned" }
        throw "Local PostgreSQL did not become healthy within $TimeoutSeconds seconds ($detail). Inspect it with: docker compose -f `"$composePath`" ps"
    }

    Write-Host "Local PostgreSQL is healthy." -ForegroundColor Green
}

function Get-KeyFileState {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return "Missing"
    }
    if ((Get-Item -LiteralPath $Path).Length -le 0) {
        return "Empty"
    }
    return "Valid"
}

function Ensure-JwtKeys {
    param([Parameter(Mandatory = $true)][string]$BackendPath)

    $privateKey = Join-Path $BackendPath ".keys\private.pem"
    $publicKey = Join-Path $BackendPath ".keys\public.pem"
    $privateState = Get-KeyFileState -Path $privateKey
    $publicState = Get-KeyFileState -Path $publicKey

    if ($privateState -eq "Valid" -and $publicState -eq "Valid") {
        Write-Host "Existing local JWT key pair preserved." -ForegroundColor Green
        return
    }
    if ($privateState -ne "Missing" -or $publicState -ne "Missing") {
        throw "Local JWT key pair is unsafe to use: private.pem is $privateState and public.pem is $publicState. Restore the missing/empty file; the launcher will not replace an existing key."
    }

    $java = Get-RequiredCommandPath "java.exe"
    Write-Host "Generating the initial local JWT key pair..."
    $generated = Invoke-NativeCommand -FilePath $java -ArgumentList @(
        "scripts/GenerateJwtKeys.java", ".keys"
    ) -WorkingDirectory $BackendPath
    if ($generated.ExitCode -ne 0) {
        throw "JWT key generation failed with exit code $($generated.ExitCode)."
    }
    if ((Get-KeyFileState -Path $privateKey) -ne "Valid" -or (Get-KeyFileState -Path $publicKey) -ne "Valid") {
        throw "JWT key generation exited successfully but did not create nonempty private.pem and public.pem files."
    }
    Write-Host "Local JWT key pair generated." -ForegroundColor Green
}

function Invoke-HttpGet {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [int]$TimeoutSeconds = 5
    )

    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec $TimeoutSeconds
        return [pscustomobject]@{
            Success = $response.StatusCode -ge 200 -and $response.StatusCode -lt 300
            StatusCode = $response.StatusCode
            Content = [string]$response.Content
            Error = $null
        }
    }
    catch {
        return [pscustomobject]@{
            Success = $false
            StatusCode = $null
            Content = ""
            Error = $_.Exception.Message
        }
    }
}

function Test-RacerEndpoint {
    param([Parameter(Mandatory = $true)][string]$Uri)

    $response = Invoke-HttpGet -Uri $Uri
    if (-not $response.Success) {
        $script:LastHttpFailure = "$Uri failed: $($response.Error)"
        return $false
    }
    try {
        # PS 5.1 can wrap a directly piped JSON array as one nested item inside @(...).
        $parsed = $response.Content | ConvertFrom-Json
        $items = @($parsed)
        $valid = $items.Count -gt 0 -and
            $null -ne $items[0].PSObject.Properties["id"] -and
            $null -ne $items[0].PSObject.Properties["name"] -and
            $null -ne $items[0].PSObject.Properties["racingType"]
        if (-not $valid) {
            $script:LastHttpFailure = "$Uri returned JSON, but not the expected racer-array shape."
        }
        return $valid
    }
    catch {
        $script:LastHttpFailure = "$Uri did not return valid JSON: $($_.Exception.Message)"
        return $false
    }
}

function Test-RingLabFrontend {
    param([Parameter(Mandatory = $true)][string]$Uri)

    $response = Invoke-HttpGet -Uri $Uri
    if (-not $response.Success) {
        $script:LastHttpFailure = "$Uri failed: $($response.Error)"
        return $false
    }
    $valid = $response.Content -match '<title>RingLab' -and $response.Content -match '<div id="root"'
    if (-not $valid) {
        $script:LastHttpFailure = "$Uri responded, but it was not the RingLab frontend."
    }
    return $valid
}

function Get-ListeningPortOwner {
    param([Parameter(Mandatory = $true)][int]$Port)

    $command = Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue
    if (-not $command) {
        return @()
    }
    return @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)
}

function Test-TcpPortOpen {
    param([Parameter(Mandatory = $true)][int]$Port)

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connection = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        return $connection.AsyncWaitHandle.WaitOne(500) -and $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Close()
    }
}

function Get-PortConflictDescription {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [int[]]$ProcessIds
    )

    if (-not $ProcessIds -or $ProcessIds.Count -eq 0) {
        return "an unidentified process"
    }
    $descriptions = foreach ($processId in $ProcessIds) {
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($process) { "$($process.ProcessName) (PID $processId)" } else { "PID $processId" }
    }
    return $descriptions -join ", "
}

function Ensure-DevelopmentService {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][scriptblock]$HealthCheck,
        [Parameter(Mandatory = $true)][scriptblock]$StartAction
    )

    if (& $HealthCheck) {
        Write-Host "$Name is already healthy; reusing it." -ForegroundColor Green
        return "Reused"
    }

    $owners = @(Get-ListeningPortOwner -Port $Port)
    if ($owners.Count -gt 0 -or (Test-TcpPortOpen -Port $Port)) {
        $owner = Get-PortConflictDescription -Port $Port -ProcessIds $owners
        throw "$Name cannot start because port $Port is occupied by $owner and the RingLab health check failed. Stop or reconfigure that application; the launcher will not terminate it."
    }

    & $StartAction
    Write-Host "$Name terminal opened." -ForegroundColor Green
    return "Started"
}

function Get-DevelopmentShell {
    $shell = Get-Command "pwsh.exe" -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $shell) {
        $shell = Get-Command "powershell.exe" -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    }
    if (-not $shell) {
        throw "Neither PowerShell 7 nor Windows PowerShell 5.1 was found for the development terminals."
    }
    return $shell.Source
}

function Start-RingLabTerminal {
    param(
        [Parameter(Mandatory = $true)][string]$Title,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$CommandPath,
        [Parameter(Mandatory = $true)][string[]]$CommandArguments
    )

    $shell = Get-DevelopmentShell
    $escapedTitle = $Title.Replace("'", "''")
    $escapedCommand = $CommandPath.Replace("'", "''")
    $quotedArguments = @($CommandArguments | ForEach-Object { "'" + $_.Replace("'", "''") + "'" })
    $command = "`$Host.UI.RawUI.WindowTitle = '$escapedTitle'; & '$escapedCommand' $($quotedArguments -join ' ')"
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
    Start-Process -FilePath $shell -WorkingDirectory $WorkingDirectory -ArgumentList @(
        "-NoExit", "-NoProfile", "-EncodedCommand", $encoded
    ) | Out-Null
}

function Start-Backend {
    param([Parameter(Mandatory = $true)][string]$BackendPath)
    $maven = Get-RequiredCommandPath "mvn.cmd"
    Start-RingLabTerminal -Title "RingLab backend" -WorkingDirectory $BackendPath `
        -CommandPath $maven -CommandArguments @("quarkus:dev")
}

function Start-Frontend {
    param([Parameter(Mandatory = $true)][string]$FrontendPath)
    $npm = Get-RequiredCommandPath "npm.cmd"
    if (-not (Test-Path -LiteralPath (Join-Path $FrontendPath "node_modules") -PathType Container)) {
        throw "Frontend dependencies are missing. Run 'npm ci' in '$FrontendPath', then rerun .\Start-RingLab.ps1."
    }
    Start-RingLabTerminal -Title "RingLab frontend" -WorkingDirectory $FrontendPath `
        -CommandPath $npm -CommandArguments @("run", "dev")
}

function Get-LocalReadiness {
    param(
        [Parameter(Mandatory = $true)][string]$DockerPath,
        [Parameter(Mandatory = $true)][string]$ComposePath
    )

    [pscustomobject]@{
        Postgres = Test-PostgresHealthy -DockerPath $DockerPath -ComposePath $ComposePath
        Backend = Test-RacerEndpoint -Uri "http://127.0.0.1:8080/api/racers"
        Frontend = Test-RingLabFrontend -Uri "http://127.0.0.1:5173/"
        FrontendProxy = Test-RacerEndpoint -Uri "http://127.0.0.1:5173/api/racers"
    }
}

function Wait-ForLocalReadiness {
    param(
        [Parameter(Mandatory = $true)][string]$DockerPath,
        [Parameter(Mandatory = $true)][string]$ComposePath,
        [int]$TimeoutSeconds = 180
    )

    $ready = Wait-ForCondition -TimeoutSeconds $TimeoutSeconds -PollSeconds 2 -Probe {
        $script:LastLocalReadiness = Get-LocalReadiness -DockerPath $DockerPath -ComposePath $ComposePath
        $script:LastLocalReadiness.Postgres -and $script:LastLocalReadiness.Backend -and
            $script:LastLocalReadiness.Frontend -and $script:LastLocalReadiness.FrontendProxy
    }
    if ($ready) {
        return
    }

    $failed = @()
    if (-not $script:LastLocalReadiness.Postgres) { $failed += "PostgreSQL health" }
    if (-not $script:LastLocalReadiness.Backend) { $failed += "backend /api/racers" }
    if (-not $script:LastLocalReadiness.Frontend) { $failed += "frontend page" }
    if (-not $script:LastLocalReadiness.FrontendProxy) { $failed += "frontend /api proxy" }
    $detail = if ($script:LastHttpFailure) { " Last HTTP failure: $($script:LastHttpFailure)" } else { "" }
    throw "Local RingLab readiness timed out after $TimeoutSeconds seconds. Failed: $($failed -join ', ').$detail Check the visible backend and frontend terminal logs."
}

function Ensure-CloudflaredService {
    $service = Get-Service -Name "cloudflared" -ErrorAction SilentlyContinue
    if (-not $service) {
        Write-Warning "Cloudflare Tunnel service is not installed. Local development can still run."
        return [pscustomobject]@{ Available = $false; Running = $false; Detail = "service not installed" }
    }
    if ($service.Status -eq "Running") {
        Write-Host "Cloudflare Tunnel is running." -ForegroundColor Green
        return [pscustomobject]@{ Available = $true; Running = $true; Detail = "running" }
    }

    try {
        Start-Service -Name "cloudflared"
        $running = Wait-ForCondition -TimeoutSeconds 15 -PollSeconds 1 -Probe {
            (Get-Service -Name "cloudflared" -ErrorAction SilentlyContinue).Status -eq "Running"
        }
        if ($running) {
            Write-Host "Cloudflare Tunnel started." -ForegroundColor Green
            return [pscustomobject]@{ Available = $true; Running = $true; Detail = "started" }
        }
        Write-Warning "Cloudflare Tunnel did not reach Running state. Local development is ready, but public dev may be unavailable."
        return [pscustomobject]@{ Available = $true; Running = $false; Detail = "start timed out" }
    }
    catch {
        $instruction = "Start only the tunnel service from an elevated PowerShell: Start-Service -Name cloudflared"
        Write-Warning "Cloudflare Tunnel could not be started: $($_.Exception.Message) $instruction"
        return [pscustomobject]@{ Available = $true; Running = $false; Detail = $instruction }
    }
}

function Test-PublicDevelopment {
    $frontendReady = Test-RingLabFrontend -Uri "https://dev.ringlabgarage.com/"
    $frontendFailure = $script:LastHttpFailure
    $apiReady = Test-RacerEndpoint -Uri "https://dev.ringlabgarage.com/api/racers"
    $apiFailure = $script:LastHttpFailure
    [pscustomobject]@{
        Ready = $frontendReady -and $apiReady
        Detail = if ($frontendReady -and $apiReady) { "public frontend and API responded" }
            elseif (-not $frontendReady) { $frontendFailure }
            else { $apiFailure }
    }
}

function Start-RingLabDevelopment {
    Write-Host "=== RingLab Local Development ===" -ForegroundColor Cyan

    Write-Host "`n[1/6] Checking local Docker Desktop..."
    $docker = Ensure-DockerReady -TimeoutSeconds $DockerTimeoutSeconds

    Write-Host "`n[2/6] Starting local PostgreSQL from compose.yaml..."
    Ensure-PostgresReady -DockerPath $docker -TimeoutSeconds $PostgresTimeoutSeconds

    $backend = Join-Path $script:RepositoryRoot "backend"
    $frontend = Join-Path $script:RepositoryRoot "frontend"
    Write-Host "`n[3/6] Checking local JWT keys..."
    Ensure-JwtKeys -BackendPath $backend

    Write-Host "`n[4/6] Starting or reusing RingLab services..."
    Ensure-DevelopmentService -Name "RingLab backend" -Port 8080 `
        -HealthCheck { Test-RacerEndpoint -Uri "http://127.0.0.1:8080/api/racers" } `
        -StartAction { Start-Backend -BackendPath $backend } | Out-Null
    Ensure-DevelopmentService -Name "RingLab frontend" -Port 5173 `
        -HealthCheck { Test-RingLabFrontend -Uri "http://127.0.0.1:5173/" } `
        -StartAction { Start-Frontend -FrontendPath $frontend } | Out-Null

    Write-Host "`n[5/6] Verifying database, backend, frontend, and proxy..."
    $composePath = Join-Path $script:RepositoryRoot "compose.yaml"
    Wait-ForLocalReadiness -DockerPath $docker -ComposePath $composePath `
        -TimeoutSeconds $ApplicationTimeoutSeconds
    Write-Host "Local development ready: http://localhost:5173" -ForegroundColor Green

    Write-Host "`n[6/6] Checking optional Cloudflare Tunnel and public dev..."
    Ensure-CloudflaredService | Out-Null
    $public = Test-PublicDevelopment
    if ($public.Ready) {
        Write-Host "Local and public development ready." -ForegroundColor Green
        Write-Host "Local:  http://localhost:5173"
        Write-Host "Public: https://dev.ringlabgarage.com"
        Start-Process "https://dev.ringlabgarage.com" | Out-Null
    }
    else {
        Write-Warning "Local development ready; public dev unavailable. $($public.Detail)"
        Start-Process "http://localhost:5173" | Out-Null
    }
}

if (-not $SkipMain) {
    try {
        Start-RingLabDevelopment
    }
    catch {
        Write-Error $_.Exception.Message
        exit 1
    }
}
