param(
  [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$DemoPassword = "RingLabDemo!2026"

try {
  $demoUri = [System.Uri]$BaseUrl
}
catch {
  throw "BaseUrl must be a valid absolute URL."
}
if (-not $demoUri.IsAbsoluteUri -or -not $demoUri.IsLoopback) {
  throw "Demo seeding is restricted to a local loopback URL (localhost, 127.0.0.1, or ::1)."
}

function Invoke-RingLabApi {
  param(
    [Parameter(Mandatory = $true)][string]$Method,
    [Parameter(Mandatory = $true)][string]$Path,
    [object]$Body,
    [string]$Token
  )

  $headers = @{}
  if ($Token) {
    $headers.Authorization = "Bearer $Token"
  }

  $request = @{
    Uri = "$($BaseUrl.TrimEnd('/'))/api$Path"
    Method = $Method
    Headers = $headers
  }
  if ($null -ne $Body) {
    $request.ContentType = "application/json"
    $request.Body = $Body | ConvertTo-Json -Depth 10
  }

  Invoke-RestMethod @request
}

function Get-StatusCode {
  param([System.Management.Automation.ErrorRecord]$Failure)

  $response = $Failure.Exception.Response
  if ($null -ne $response -and $null -ne $response.StatusCode) {
    return [int]$response.StatusCode
  }
  return $null
}

function Get-OrCreateDemoUser {
  param([pscustomobject]$Definition)

  try {
    $session = Invoke-RingLabApi -Method POST -Path "/auth/login" -Body @{
      username = $Definition.Username
      password = $DemoPassword
    }
    Write-Host "Using existing demo account $($Definition.Username)."
    return $session
  }
  catch {
    if ((Get-StatusCode $_) -ne 401) {
      throw
    }
  }

  try {
    $session = Invoke-RingLabApi -Method POST -Path "/auth/register" -Body @{
      username = $Definition.Username
      email = $Definition.Email
      password = $DemoPassword
    }
    Write-Host "Created demo account $($Definition.Username)."
    return $session
  }
  catch {
    throw "Could not create $($Definition.Username). An account with that demo identity may already exist with a different password. No existing data was deleted."
  }
}

function New-NameIndex {
  param([object[]]$Items)

  $index = @{}
  foreach ($item in $Items) {
    $index[$item.name] = $item.id
  }
  return $index
}

function Require-GameId {
  param(
    [hashtable]$Index,
    [string]$Name,
    [string]$Collection
  )

  if (-not $Index.ContainsKey($Name)) {
    throw "The supplied $Collection dataset does not contain '$Name'. Demo data was not fully seeded."
  }
  return $Index[$Name]
}

function Get-AllComments {
  param([string]$BuildId)

  $items = @()
  $page = 0
  do {
    $result = Invoke-RingLabApi -Method GET -Path "/builds/$BuildId/comments?page=$page&size=50"
    $items += @($result.items)
    $page++
  } while ($page * 50 -lt $result.total)
  return $items
}

Write-Host "Checking RingLab at $BaseUrl..."
try {
  $racers = @(Invoke-RingLabApi -Method GET -Path "/racers")
  $machines = @(Invoke-RingLabApi -Method GET -Path "/machines")
  $gadgets = @(Invoke-RingLabApi -Method GET -Path "/gadgets")
}
catch {
  throw "RingLab is not reachable at $BaseUrl. Start PostgreSQL and the Quarkus development server, then run this script again."
}

$racerIds = New-NameIndex $racers
$machineIds = New-NameIndex $machines
$gadgetIds = New-NameIndex $gadgets

$userDefinitions = @(
  [pscustomobject]@{ Key = "amy"; Username = "ringlab_demo_amy"; Email = "ringlab_demo_amy@example.test" },
  [pscustomobject]@{ Key = "tails"; Username = "ringlab_demo_tails"; Email = "ringlab_demo_tails@example.test" },
  [pscustomobject]@{ Key = "shadow"; Username = "ringlab_demo_shadow"; Email = "ringlab_demo_shadow@example.test" }
)

$users = @{}
foreach ($definition in $userDefinitions) {
  $users[$definition.Key] = Get-OrCreateDemoUser $definition
}

$buildDefinitions = @(
  [pscustomobject]@{
    Key = "cornering"; Owner = "amy"; Title = "[DEMO] Cornering Showcase"
    Description = "A presentation build focused on a clear handling-themed loadout."
    Racer = "Amy Rose"; Machine = "Pink Cabriolet"
    Gadgets = @("Ultimate Drift Charge", "Ring Engine", "Strong Finish")
  },
  [pscustomobject]@{
    Key = "items"; Owner = "amy"; Title = "[DEMO] Item Control Practice"
    Description = "A demo combination with an ordered set of item-related gadgets."
    Racer = 'Miles "Tails" Prower'; Machine = "Whirlwind Sport"
    Gadgets = @("Item Stock Swap", "Defense Item Chance UP", "Item Mercy")
  },
  [pscustomobject]@{
    Key = "route"; Owner = "amy"; Title = "[DEMO] Ring Route Session"
    Description = "A varied community example for filtering and build-detail demonstrations."
    Racer = "Sonic the Hedgehog"; Machine = "TYPE-J Iota"
    Gadgets = @("Route Planner Bounty", "Ring Doubler", "Strong Finish")
  },
  [pscustomobject]@{
    Key = "acceleration"; Owner = "tails"; Title = "[DEMO] Acceleration Lab"
    Description = "A simple acceleration-themed setup prepared for the RingLab demo."
    Racer = 'Miles "Tails" Prower'; Machine = "Jumble Rage"
    Gadgets = @("Boost Item Chance UP", "Lucky Pair")
  },
  [pscustomobject]@{
    Key = "recovery"; Owner = "tails"; Title = "[DEMO] Power Recovery Run"
    Description = "A contrasting power build that helps make Explore filters useful."
    Racer = "Knuckles the Echidna"; Machine = "Land Smasher"
    Gadgets = @("Damage Mercy", "Ring Mercy", "Giant Rocket Punch")
  },
  [pscustomobject]@{
    Key = "boost"; Owner = "tails"; Title = "[DEMO] Boost Timing Notes"
    Description = "A presentation-ready loadout with a deliberately ordered gadget list."
    Racer = "Sonic the Hedgehog"; Machine = "TYPE-S Stream"
    Gadgets = @("Boost Item Chance UP", "Hyper Ring Engine", "Less is More")
  },
  [pscustomobject]@{
    Key = "shadow"; Owner = "shadow"; Title = "[DEMO] Dark Reaper Sprint"
    Description = "A recognizable speed pairing for score sorting and social interaction demos."
    Racer = "Shadow the Hedgehog"; Machine = "Dark Reaper"
    Gadgets = @("Attack Item Chance UP", "Double Down", "Champion Bounty")
  },
  [pscustomobject]@{
    Key = "finish"; Owner = "shadow"; Title = "[DEMO] Consistent Finish Plan"
    Description = "A second Shadow-owned build for demonstrating My Builds."
    Racer = "Big the Cat"; Machine = "Road Dragoon"
    Gadgets = @("Defense Item Chance UP", "Strong Finish")
  }
)

$builds = @{}
foreach ($definition in $buildDefinitions) {
  $owner = $users[$definition.Owner]
  $authorId = [System.Uri]::EscapeDataString([string]$owner.user.id)
  $existing = Invoke-RingLabApi -Method GET -Path "/builds?authorId=$authorId&size=50"
  $build = @($existing.items) | Where-Object { $_.title -eq $definition.Title } | Select-Object -First 1

  if ($null -eq $build) {
    $orderedGadgetIds = @(
      foreach ($gadgetName in $definition.Gadgets) {
        Require-GameId $gadgetIds $gadgetName "gadget"
      }
    )
    $build = Invoke-RingLabApi -Method POST -Path "/builds" -Token $owner.token -Body @{
      title = $definition.Title
      description = $definition.Description
      racerId = Require-GameId $racerIds $definition.Racer "racer"
      machineId = Require-GameId $machineIds $definition.Machine "machine"
      gadgetIds = $orderedGadgetIds
    }
    Write-Host "Created build $($definition.Title)."
  }
  else {
    Write-Host "Using existing build $($definition.Title)."
  }
  $builds[$definition.Key] = $build
}

$voteDefinitions = @(
  @("amy", "cornering", 1), @("tails", "cornering", 1), @("shadow", "cornering", 1),
  @("amy", "shadow", 1), @("tails", "shadow", 1), @("shadow", "shadow", 1),
  @("amy", "boost", 1), @("shadow", "boost", 1),
  @("amy", "acceleration", 1), @("tails", "acceleration", 1),
  @("tails", "route", 1),
  @("amy", "items", 1), @("shadow", "items", -1),
  @("tails", "finish", 1), @("shadow", "finish", -1),
  @("amy", "recovery", -1), @("tails", "recovery", -1), @("shadow", "recovery", 1)
)

foreach ($vote in $voteDefinitions) {
  $user = $users[$vote[0]]
  $build = $builds[$vote[1]]
  Invoke-RingLabApi -Method PUT -Path "/builds/$($build.id)/vote" -Token $user.token -Body @{
    value = $vote[2]
  } | Out-Null
}
Write-Host "Ensured $($voteDefinitions.Count) demo votes."

$commentDefinitions = @(
  @("tails", "cornering", "[DEMO] The ordered gadgets make this loadout easy to follow."),
  @("shadow", "cornering", "[DEMO] This is a strong example for the details page."),
  @("amy", "shadow", "[DEMO] Great pairing for a speed-themed showcase."),
  @("tails", "shadow", "[DEMO] I like how clearly the machine choice reads here."),
  @("shadow", "acceleration", "[DEMO] Nice contrasting setup for the collection filters."),
  @("amy", "acceleration", "[DEMO] The gadget order is presentation-friendly."),
  @("shadow", "boost", "[DEMO] This one should be easy to find with racer filtering."),
  @("amy", "recovery", "[DEMO] Useful contrast with the higher-scoring builds."),
  @("tails", "finish", "[DEMO] A concise second build for the My Builds view."),
  @("shadow", "route", "[DEMO] This gives the discussion section some activity."),
  @("tails", "items", "[DEMO] The item-focused names are easy to explain in a demo."),
  @("amy", "items", "[DEMO] Ready for Explore and Build Details walkthroughs.")
)

$createdComments = 0
foreach ($comment in $commentDefinitions) {
  $user = $users[$comment[0]]
  $build = $builds[$comment[1]]
  $existingComments = @(Get-AllComments $build.id)
  $alreadyExists = $existingComments | Where-Object {
    $_.authorId -eq $user.user.id -and $_.text -eq $comment[2]
  }
  if (-not $alreadyExists) {
    Invoke-RingLabApi -Method POST -Path "/builds/$($build.id)/comments" -Token $user.token -Body @{
      text = $comment[2]
    } | Out-Null
    $createdComments++
  }
}
Write-Host "Ensured $($commentDefinitions.Count) demo comments ($createdComments added this run)."

Write-Host "Demo dataset is ready: 3 users, 8 builds, 18 votes, and 12 comments."
Write-Host "Demo password for every account: $DemoPassword"
