$scriptPath = Join-Path (Split-Path -Parent $PSScriptRoot) "Start-RingLab.ps1"
. $scriptPath -SkipMain

Describe "Start-RingLab launcher" {
    Context "PowerShell compatibility" {
        It "uses a successful native exit code even when the command writes status to stderr" {
            $cmd = Get-RequiredCommandPath "cmd.exe"
            $result = Invoke-NativeCommand -FilePath $cmd -ArgumentList @(
                "/c", "echo normal-status 1>&2 & exit /b 0"
            )
            $result.ExitCode | Should Be 0
            $result.Output | Should Match "normal-status"
        }

        It "recognizes a racer JSON array" {
            Mock Invoke-HttpGet {
                [pscustomobject]@{
                    Success = $true
                    StatusCode = 200
                    Content = '[{"id":"racer-1","name":"Amy Rose","racingType":"HANDLING"}]'
                    Error = $null
                }
            }
            Test-RacerEndpoint -Uri "http://localhost/api/racers" | Should Be $true
        }
    }

    Context "Docker readiness" {
        It "fails clearly when Docker CLI is unavailable" {
            Mock Get-RequiredCommandPath { throw "Required command 'docker.exe' was not found on PATH." }
            $message = ""
            try { Ensure-DockerReady -TimeoutSeconds 2 } catch { $message = $_.Exception.Message }
            $message | Should Match "docker.exe"
        }

        It "waits until the local Docker engine becomes ready" {
            Mock Get-RequiredCommandPath { "docker.exe" }
            Mock Invoke-NativeCommand { [pscustomobject]@{ ExitCode = 0; Output = "ok" } }
            Mock Assert-LocalDockerContext { "npipe:////./pipe/dockerDesktopLinuxEngine" }
            $script:dockerProbes = 0
            Mock Test-DockerEngineReady {
                $script:dockerProbes++
                return $script:dockerProbes -ge 3
            }
            Mock Get-DockerDesktopPath { "C:\Docker\Docker Desktop.exe" }
            Mock Start-DockerDesktop {}
            Mock Start-Sleep {}

            Ensure-DockerReady -TimeoutSeconds 4 | Should Be "docker.exe"
            Assert-MockCalled Start-DockerDesktop -Times 1
        }

        It "times out instead of claiming Docker is ready" {
            Mock Get-RequiredCommandPath { "docker.exe" }
            Mock Invoke-NativeCommand { [pscustomobject]@{ ExitCode = 0; Output = "ok" } }
            Mock Assert-LocalDockerContext { "npipe:////./pipe/dockerDesktopLinuxEngine" }
            Mock Test-DockerEngineReady { $false }
            Mock Get-DockerDesktopPath { "C:\Docker\Docker Desktop.exe" }
            Mock Start-DockerDesktop {}
            Mock Start-Sleep {}

            $message = ""
            try { Ensure-DockerReady -TimeoutSeconds 2 } catch { $message = $_.Exception.Message }
            $message | Should Match "did not expose its local engine"
        }
    }

    Context "JWT key safety" {
        BeforeEach {
            $script:keyFixture = Join-Path $TestDrive ([Guid]::NewGuid().ToString())
            New-Item -ItemType Directory -Path $script:keyFixture | Out-Null
        }

        It "generates a pair only when both files are absent" {
            Mock Get-RequiredCommandPath { "java.exe" }
            Mock Invoke-NativeCommand {
                $keyDirectory = Join-Path $script:keyFixture ".keys"
                New-Item -ItemType Directory -Path $keyDirectory | Out-Null
                Set-Content -LiteralPath (Join-Path $keyDirectory "private.pem") -Value "private"
                Set-Content -LiteralPath (Join-Path $keyDirectory "public.pem") -Value "public"
                [pscustomobject]@{ ExitCode = 0; Output = "" }
            }

            Ensure-JwtKeys -BackendPath $script:keyFixture
            (Get-Item (Join-Path $script:keyFixture ".keys\private.pem")).Length | Should BeGreaterThan 0
            Assert-MockCalled Invoke-NativeCommand -Times 1
        }

        It "rejects a partial or empty pair" {
            $keyDirectory = Join-Path $script:keyFixture ".keys"
            New-Item -ItemType Directory -Path $keyDirectory | Out-Null
            Set-Content -LiteralPath (Join-Path $keyDirectory "private.pem") -Value "private"
            New-Item -ItemType File -Path (Join-Path $keyDirectory "public.pem") | Out-Null

            $message = ""
            try { Ensure-JwtKeys -BackendPath $script:keyFixture } catch { $message = $_.Exception.Message }
            $message | Should Match "private.pem is Valid and public.pem is Empty"
        }

        It "preserves an existing nonempty pair" {
            $keyDirectory = Join-Path $script:keyFixture ".keys"
            New-Item -ItemType Directory -Path $keyDirectory | Out-Null
            Set-Content -LiteralPath (Join-Path $keyDirectory "private.pem") -Value "private"
            Set-Content -LiteralPath (Join-Path $keyDirectory "public.pem") -Value "public"
            Mock Invoke-NativeCommand { throw "must not regenerate" }

            Ensure-JwtKeys -BackendPath $script:keyFixture
            (Get-Content -LiteralPath (Join-Path $keyDirectory "private.pem")) | Should Be "private"
            (Get-Content -LiteralPath (Join-Path $keyDirectory "public.pem")) | Should Be "public"
        }
    }

    Context "service detection and readiness" {
        It "reports backend failure even when the frontend is healthy" {
            Mock Test-PostgresHealthy { $true }
            Mock Test-RacerEndpoint {
                param($Uri)
                return $Uri -like "*:5173/*"
            }
            Mock Test-RingLabFrontend { $true }

            $state = Get-LocalReadiness -DockerPath "docker.exe" -ComposePath "compose.yaml"
            $state.Postgres | Should Be $true
            $state.Backend | Should Be $false
            $state.Frontend | Should Be $true
            $state.FrontendProxy | Should Be $true
        }

        It "treats missing cloudflared as optional" {
            Mock Get-Service { $null }
            $result = Ensure-CloudflaredService
            $result.Available | Should Be $false
            $result.Running | Should Be $false
        }

        It "reuses a healthy service without starting a duplicate" {
            $script:startCount = 0
            $result = Ensure-DevelopmentService -Name "RingLab frontend" -Port 5173 `
                -HealthCheck { $true } -StartAction { $script:startCount++ }
            $result | Should Be "Reused"
            $script:startCount | Should Be 0
        }

        It "rejects an unrelated process occupying a required port" {
            Mock Get-ListeningPortOwner { @(4242) }
            Mock Test-TcpPortOpen { $true }
            Mock Get-PortConflictDescription { "other-app (PID 4242)" }
            $script:startCount = 0

            $message = ""
            try {
                Ensure-DevelopmentService -Name "RingLab backend" -Port 8080 `
                    -HealthCheck { $false } -StartAction { $script:startCount++ }
            }
            catch { $message = $_.Exception.Message }
            $message | Should Match "other-app"
            $script:startCount | Should Be 0
        }
    }
}
