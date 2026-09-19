$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
if (-not $root) {
    $root = (Get-Location).Path
}

Write-Host "=== RingLab Development ===" -ForegroundColor Cyan

# ------------------------------------------------------------
# 1. Make sure Docker is running
# ------------------------------------------------------------

Write-Host "`n[1/5] Checking Docker..."

try {
    docker info *> $null
}
catch {
    Write-Host "Docker is not running. Starting Docker Desktop..."

    $dockerDesktopCandidates = @(
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
        "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe"
    )
    $dockerDesktop = $dockerDesktopCandidates |
        Where-Object { Test-Path -LiteralPath $_ } |
        Select-Object -First 1

    if (-not $dockerDesktop) {
        throw "Docker Desktop was not found in a supported installation location."
    }

    Start-Process $dockerDesktop

    Write-Host "Waiting for Docker..."

    $ready = $false

    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2

        try {
            docker info *> $null
            $ready = $true
            break
        }
        catch {}
    }

    if (-not $ready) {
        throw "Docker did not become ready."
    }
}

Write-Host "Docker ready." -ForegroundColor Green


# ------------------------------------------------------------
# 2. Start local PostgreSQL
# ------------------------------------------------------------

Write-Host "`n[2/5] Starting PostgreSQL..."

Set-Location $root
docker compose up -d

if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed."
}

Write-Host "PostgreSQL started." -ForegroundColor Green


# ------------------------------------------------------------
# 3. Start Quarkus backend
# ------------------------------------------------------------

Write-Host "`n[3/5] Starting Quarkus backend..."

$backend = Join-Path $root "backend"

Start-Process powershell.exe `
    -WorkingDirectory $backend `
    -ArgumentList @(
        "-NoExit",
        "-Command",
        @'
if (-not (Test-Path ".keys")) {
    Write-Host "Generating local JWT keys..."
    java scripts/GenerateJwtKeys.java .keys
}

Write-Host "Starting RingLab backend..."
mvn quarkus:dev
'@
    )

Write-Host "Backend window opened." -ForegroundColor Green


# ------------------------------------------------------------
# 4. Start Vite frontend
# ------------------------------------------------------------

Write-Host "`n[4/5] Starting Vite frontend..."

$frontend = Join-Path $root "frontend"

Start-Process powershell.exe `
    -WorkingDirectory $frontend `
    -ArgumentList @(
        "-NoExit",
        "-Command",
        "npm run dev"
    )

Write-Host "Frontend window opened." -ForegroundColor Green


# ------------------------------------------------------------
# 5. Check Cloudflare Tunnel service
# ------------------------------------------------------------

Write-Host "`n[5/5] Checking Cloudflare Tunnel..."

$cloudflared = Get-Service -Name "cloudflared" -ErrorAction SilentlyContinue

if ($cloudflared) {
    if ($cloudflared.Status -ne "Running") {
        try {
            Start-Service cloudflared
            Write-Host "Cloudflare Tunnel started." -ForegroundColor Green
        }
        catch {
            Write-Warning "cloudflared is stopped and could not be started."
            Write-Warning "Run this script as Administrator if needed."
        }
    }
    else {
        Write-Host "Cloudflare Tunnel already running." -ForegroundColor Green
    }
}
else {
    Write-Warning "cloudflared Windows service was not found."
}


# ------------------------------------------------------------
# Wait briefly for Vite, then open dev site
# ------------------------------------------------------------

Write-Host "`nWaiting for frontend..."

$viteReady = $false

for ($i = 0; $i -lt 30; $i++) {
    if (Test-NetConnection 127.0.0.1 -Port 5173 -InformationLevel Quiet) {
        $viteReady = $true
        break
    }

    Start-Sleep -Seconds 1
}

if ($viteReady) {
    Write-Host "`nRingLab dev is ready!" -ForegroundColor Green
    Write-Host "Local:  http://localhost:5173"
    Write-Host "Public: https://dev.ringlabgarage.com"

    Start-Process "https://dev.ringlabgarage.com"
}
else {
    Write-Warning "Vite has not started on port 5173 yet."
    Write-Host "Check the frontend PowerShell window."
}
