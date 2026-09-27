# Optional local fixture. Uses the normal API and never modifies existing builds.
[CmdletBinding()]
param(
    [string]$ApiBase = 'http://127.0.0.1:8080/api',
    [Parameter(Mandatory = $true)][string]$Token
)
$ErrorActionPreference = 'Stop'
$uri = [Uri]$ApiBase
if ($uri.Scheme -ne 'http' -or $uri.Host -notin @('127.0.0.1', 'localhost', '[::1]') -or $uri.AbsolutePath.TrimEnd('/') -ne '/api') {
    throw 'This fixture is restricted to a loopback RingLab API.'
}
$ApiBase = $ApiBase.TrimEnd('/')
$headers = @{ Authorization = "Bearer $Token" }
$author = Invoke-RestMethod "$ApiBase/auth/me" -Headers $headers
$maps = Invoke-RestMethod "$ApiBase/maps"
$first = 'a0000000-0000-4000-8000-000000000001' # E-Stadium
$second = 'a0000000-0000-4000-8000-000000000002' # Rainbow Garden
$crossworld = 'a0000000-0000-4000-8000-000000000030' # Sky Road
foreach ($id in @($first, $second, $crossworld)) {
    if ($id -notin $maps.id) { throw "Required verified map is unavailable: $id" }
}
$racers = Invoke-RestMethod "$ApiBase/racers"
$racer = $racers[0]
$machines = Invoke-RestMethod "$ApiBase/machines"
$machine = $machines | Where-Object racingType -eq 'SPEED' | Select-Object -First 1
$catalogParts = Invoke-RestMethod "$ApiBase/machine-parts"
$parts = $catalogParts | Where-Object sourceMachineId -eq $machine.id
$patches = Invoke-RestMethod "$ApiBase/game-versions"
$patch = $patches | Sort-Object releasedAt -Descending | Select-Object -First 1
$examples = @(
    @{ title = '[Map Demo] General-purpose setup'; maps = @() },
    @{ title = '[Map Demo] One selected map'; maps = @($first) },
    @{ title = '[Map Demo] Several selected maps'; maps = @($first, $second, $crossworld) }
)
$existing = Invoke-RestMethod "$ApiBase/builds?authorId=$($author.id)&search=%5BMap%20Demo%5D&size=50"
foreach ($example in $examples) {
    if ($example.title -in $existing.items.title) {
        Write-Output "Already present; left unchanged: $($example.title)"
        continue
    }
    $body = @{
        title = $example.title
        description = 'Optional local UI example. These selections demonstrate author preferences; they are not performance advice.'
        racerId = $racer.id
        frontPartId = ($parts | Where-Object type -eq 'FRONT').id
        rearPartId = ($parts | Where-Object type -eq 'REAR').id
        tirePartId = ($parts | Where-Object type -eq 'TIRE').id
        gameVersionId = $patch.id
        gadgetIds = @()
        recommendedMapIds = $example.maps
    } | ConvertTo-Json -Depth 4
    $created = Invoke-RestMethod "$ApiBase/builds" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
    Write-Output "$($created.title): /builds/$($created.id)"
}
