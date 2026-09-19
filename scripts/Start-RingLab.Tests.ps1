$scriptPath = Join-Path (Split-Path -Parent $PSScriptRoot) "Start-RingLab.ps1"
. $scriptPath -SkipMain

Describe "Start-RingLab launcher" {
    Context "Docker readiness" {
        It "fails clearly when Docker CLI is unavailable" {
            Mock Get-RequiredCommandPath { throw "Required command 'docker.exe' was not found on PATH." }
            { Ensure-DockerReady -TimeoutSeconds 2 } | Should Throw "*docker.exe*"
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

            { Ensure-DockerReady -TimeoutSeconds 2 } | Should Throw "*did not expose its local engine*"
        }
    }

    Context "JWT key safety" {
        BeforeEach {
            $script:keyFixture = Join-Path $TestDrive "backend"
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

            { Ensure-JwtKeys -BackendPath $script:keyFixture } | Should Throw "*private.pem is Valid and public.pem is Empty*"
        }

        It "preserves an existing nonempty pair" {
            $keyDirectory = Join-Path $script:keyFixture ".keys"
            New-Item -ItemType Directory -Path $keyDirectory | Out-Null
            Set-Content -LiteralPath (Join-Path $keyDirectory "private.pem") -Value "private"
            Set-Content -LiteralPath (Join-Path $keyDirectory "public.pem") -Value "public"
            Mock Invoke-NativeCommand { throw "must not regenerate" }

            Ensure-JwtKeys -BackendPath $script:keyFixture
            Assert-MockCalled Invoke-NativeCommand -Times 0
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

            { Ensure-DevelopmentService -Name "RingLab backend" -Port 8080 `
                -HealthCheck { $false } -StartAction { $script:startCount++ } } |
                Should Throw "*other-app*"
            $script:startCount | Should Be 0
        }
    }
}
