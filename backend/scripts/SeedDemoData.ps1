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
  $racers = @(Invoke-RingLabApi -Method GET -Path "/racers" | ForEach-Object { $_ })
  $machines = @(Invoke-RingLabApi -Method GET -Path "/machines" | ForEach-Object { $_ })
  $parts = @(Invoke-RingLabApi -Method GET -Path "/machine-parts" | ForEach-Object { $_ })
  $gadgets = @(Invoke-RingLabApi -Method GET -Path "/gadgets" | ForEach-Object { $_ })
  $versions = @(Invoke-RingLabApi -Method GET -Path "/game-versions" | ForEach-Object { $_ })
}
catch {
  throw "RingLab is not reachable at $BaseUrl. Start PostgreSQL and the Quarkus development server, then run this script again."
}

$racerIds = @{}
$versionIds = @{}
foreach ($version in $versions) {
  $versionIds[$version.version] = $version.id
}
foreach ($racer in $racers) {
  [void]($racerIds[$racer.name] = $racer.id)
}
$partIds = @{}
foreach ($part in $parts) {
  [void]($partIds["$($part.sourceMachineName)/$($part.type)"] = $part.id)
}
$gadgetIds = @{}
foreach ($gadget in $gadgets) {
  [void]($gadgetIds[$gadget.name] = $gadget.id)
}

$userDefinitions = @(
  [pscustomobject]@{ Key = "amy"; Username = "ringlab_demo_amy"; Email = "ringlab_demo_amy@example.test" },
  [pscustomobject]@{ Key = "tails"; Username = "ringlab_demo_tails"; Email = "ringlab_demo_tails@example.test" },
  [pscustomobject]@{ Key = "shadow"; Username = "ringlab_demo_shadow"; Email = "ringlab_demo_shadow@example.test" },
  [pscustomobject]@{ Key = "sonic"; Username = "ringlab_demo_sonic"; Email = "ringlab_demo_sonic@example.test" },
  [pscustomobject]@{ Key = "knuckles"; Username = "ringlab_demo_knuckles"; Email = "ringlab_demo_knuckles@example.test" },
  [pscustomobject]@{ Key = "rouge"; Username = "ringlab_demo_rouge"; Email = "ringlab_demo_rouge@example.test" },
  [pscustomobject]@{ Key = "cream"; Username = "ringlab_demo_cream"; Email = "ringlab_demo_cream@example.test" },
  [pscustomobject]@{ Key = "blaze"; Username = "ringlab_demo_blaze"; Email = "ringlab_demo_blaze@example.test" },
  [pscustomobject]@{ Key = "silver"; Username = "ringlab_demo_silver"; Email = "ringlab_demo_silver@example.test" },
  [pscustomobject]@{ Key = "vector"; Username = "ringlab_demo_vector"; Email = "ringlab_demo_vector@example.test" }
)
foreach ($number in 1..50) {
  $suffix = $number.ToString("00")
  $userDefinitions += [pscustomobject]@{
    Key = "member$suffix"
    Username = "ringlab_demo_member_$suffix"
    Email = "ringlab_demo_member_$suffix@example.test"
  }
}

$users = @{}
foreach ($definition in $userDefinitions) {
  $users[$definition.Key] = Get-OrCreateDemoUser $definition
}
$allDemoVoters = @($userDefinitions | ForEach-Object { $_.Key })
if ($allDemoVoters.Count -ne 60) {
  throw "The demo vote dataset requires exactly 60 local accounts."
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
  },
  [pscustomobject]@{
    Key = "amy-drift"; Owner = "amy"; Title = "[DEMO] Drift Line Routine"
    Description = "A compact handling example for a build comparison walkthrough."
    Racer = "Amy Rose"; Machine = "Neo Lightron"
    Gadgets = @("Ultimate Drift Charge", "Less is More", "Route Planner Bounty")
  },
  [pscustomobject]@{
    Key = "tails-grid"; Owner = "tails"; Title = "[DEMO] Starting Grid Notes"
    Description = "A varied setup that keeps the presentation collection feeling active."
    Racer = 'Miles "Tails" Prower'; Machine = "Whirlwind Sport"
    Gadgets = @("Item Stock Plus", "Lucky Pair", "Champion Bounty")
  },
  [pscustomobject]@{
    Key = "shadow-laps"; Owner = "shadow"; Title = "[DEMO] Night Circuit Notes"
    Description = "A second speed-themed community build with a different ordered loadout."
    Racer = "Shadow the Hedgehog"; Machine = "Road Dragoon"
    Gadgets = @("Attack Item Chance UP", "Hazard Item Chance UP", "Strong Finish")
  },
  [pscustomobject]@{
    Key = "sonic-speed"; Owner = "sonic"; Title = "[DEMO] Speedster Session"
    Description = "A clear stock-machine example for the Game Collection and Explore views."
    Racer = "Sonic the Hedgehog"; Machine = "Speedster Lightning"
    Gadgets = @("Boost Item Chance UP", "Hyper Ring Engine", "Champion Bounty")
  },
  [pscustomobject]@{
    Key = "sonic-boost"; Owner = "sonic"; Title = "[DEMO] Boost Route Notes"
    Description = "A short build description suited to a Build Details walkthrough."
    Racer = "Sonic the Hedgehog"; Machine = "TYPE-J Iota"
    Gadgets = @("Route Planner Bounty", "Boost Item Chance UP")
  },
  [pscustomobject]@{
    Key = "sonic-route"; Owner = "sonic"; Title = "[DEMO] Balanced Start"
    Description = "A contrasting build for filter and score-order demonstrations."
    Racer = "Knuckles the Echidna"; Machine = "Jumble Rage"
    Gadgets = @("Ring Mercy", "Item Stock Swap", "Strong Finish")
  },
  [pscustomobject]@{
    Key = "knuckles-power"; Owner = "knuckles"; Title = "[DEMO] Power Lineup"
    Description = "A concise power-oriented entry for the expanded demo collection."
    Racer = "Knuckles the Echidna"; Machine = "Land Smasher"
    Gadgets = @("Giant Rocket Punch", "Damage Mercy", "Double Down")
  },
  [pscustomobject]@{
    Key = "knuckles-endurance"; Owner = "knuckles"; Title = "[DEMO] Steady Lap Plan"
    Description = "A lower-activity build included to make the community feel uneven."
    Racer = "Big the Cat"; Machine = "Road Dragoon"
    Gadgets = @("Ring Mercy", "Defense Item Chance UP")
  },
  [pscustomobject]@{
    Key = "knuckles-ring"; Owner = "knuckles"; Title = "[DEMO] Ring Collection Notes"
    Description = "A small-sample build for later confidence-aware ranking demonstrations."
    Racer = "Amy Rose"; Machine = "Pink Cabriolet"
    Gadgets = @("Ring Doubler", "Ring Engine")
  }
)

$builds = @{}
# Three existing examples demonstrate mixed sources; all other definitions stay stock-style.
$mixedRearSources = @{
  "route" = "Dark Reaper"
  "boost" = "Speedster Lightning"
  "amy-drift" = "TYPE-S Stream"
}
$versionMix = @("1.4.1", "1.4.1", "1.3.1", "1.4.1", "1.2.2", "1.4.1", "1.3.1", "1.4.1", "1.2.0", $null)
$buildIndex = 0
foreach ($definition in $buildDefinitions) {
  $versionName = $versionMix[$buildIndex % $versionMix.Count]
  $gameVersionId = if ($null -eq $versionName) { $null } else {
    Require-GameId $versionIds $versionName "game version"
  }
  $buildIndex++
  $owner = $users[$definition.Owner]
  $authorId = [System.Uri]::EscapeDataString([string]$owner.user.id)
  $existing = Invoke-RingLabApi -Method GET -Path "/builds?authorId=$authorId&size=50"
  $build = @($existing.items) | Where-Object { $_.title -eq $definition.Title } | Select-Object -First 1
  $rearSource = $definition.Machine
  if ($mixedRearSources.ContainsKey($definition.Key)) {
    $rearSource = $mixedRearSources[$definition.Key]
  }
  $frontPartId = Require-GameId $partIds "$($definition.Machine)/FRONT" "machine part"
  $rearPartId = Require-GameId $partIds "$rearSource/REAR" "machine part"
  $tirePartId = Require-GameId $partIds "$($definition.Machine)/TIRE" "machine part"

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
      frontPartId = $frontPartId
      rearPartId = $rearPartId
      tirePartId = $tirePartId
      gameVersionId = $gameVersionId
      gadgetIds = $orderedGadgetIds
    }
    Write-Host "Created build $($definition.Title)."
  }
  else {
    if ($build.gameVersion.id -ne $gameVersionId) {
      $build = Invoke-RingLabApi -Method PUT -Path "/builds/$($build.id)" -Token $owner.token -Body @{
        title = $build.title
        description = $build.description
        racerId = $build.racer.id
        frontPartId = $build.frontPart.id
        rearPartId = $build.rearPart.id
        tirePartId = $build.tirePart.id
        gameVersionId = $gameVersionId
        gadgetIds = @($build.gadgets | ForEach-Object { $_.id })
      }
    }
    Write-Host "Using existing build $($definition.Title)."
  }
  $builds[$definition.Key] = $build
}

$votePatterns = @(
  [pscustomobject]@{ Build = "cornering"; Up = @("amy", "tails", "shadow"); Down = @() },
  [pscustomobject]@{ Build = "items"; Up = @("amy", "tails", "shadow", "sonic", "knuckles", "rouge", "cream", "blaze", "silver"); Down = @("vector") },
  # Same raw score comparison: 15/0 ranks above 25/10 under Wilson confidence.
  [pscustomobject]@{ Build = "route"; Up = $allDemoVoters[0..14]; Down = @() },
  [pscustomobject]@{ Build = "amy-drift"; Up = @("amy", "tails", "shadow", "sonic", "knuckles", "rouge", "cream", "blaze"); Down = @("silver", "vector") },
  [pscustomobject]@{ Build = "acceleration"; Up = $allDemoVoters[0..19]; Down = $allDemoVoters[20..59] },
  [pscustomobject]@{ Build = "recovery"; Up = $allDemoVoters[0..19]; Down = $allDemoVoters[20..59] },
  [pscustomobject]@{ Build = "boost"; Up = $allDemoVoters[0..24]; Down = $allDemoVoters[25..34] },
  [pscustomobject]@{ Build = "tails-grid"; Up = @("amy", "tails", "shadow", "sonic", "knuckles", "rouge", "cream", "blaze"); Down = @("silver", "vector") },
  # Raw score comparison: 40/20 ranks below the 8/0 sonic-speed build under Wilson confidence.
  [pscustomobject]@{ Build = "shadow"; Up = $allDemoVoters[0..39]; Down = $allDemoVoters[40..59] },
  [pscustomobject]@{ Build = "finish"; Up = @("amy", "tails", "shadow", "sonic", "knuckles", "rouge", "cream", "blaze"); Down = @("vector") },
  [pscustomobject]@{ Build = "shadow-laps"; Up = @("amy", "tails", "shadow", "sonic", "knuckles", "rouge", "cream"); Down = @("silver", "vector") },
  [pscustomobject]@{ Build = "sonic-speed"; Up = @("amy", "tails", "shadow", "sonic", "knuckles", "rouge", "cream", "blaze"); Down = @() },
  [pscustomobject]@{ Build = "sonic-boost"; Up = @("amy", "tails", "shadow", "sonic"); Down = @("vector") },
  [pscustomobject]@{ Build = "sonic-route"; Up = @("amy", "tails", "sonic"); Down = @("vector") },
  [pscustomobject]@{ Build = "knuckles-power"; Up = @("amy", "tails", "knuckles"); Down = @() },
  [pscustomobject]@{ Build = "knuckles-endurance"; Up = @("sonic", "knuckles"); Down = @() },
  [pscustomobject]@{ Build = "knuckles-ring"; Up = @("knuckles"); Down = @() }
)

$voteDefinitions = @()
foreach ($pattern in $votePatterns) {
  foreach ($user in $pattern.Up) {
    $voteDefinitions += ,@($user, $pattern.Build, 1)
  }
  foreach ($user in $pattern.Down) {
    $voteDefinitions += ,@($user, $pattern.Build, -1)
  }
}

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
  @("amy", "items", "[DEMO] Ready for Explore and Build Details walkthroughs."),
  @("rouge", "cornering", "[DEMO] The compact vote sample is useful for comparison."),
  @("cream", "cornering", "[DEMO] This is easy to present in a short walkthrough."),
  @("blaze", "cornering", "[DEMO] Clear racer and machine choices here."),
  @("silver", "shadow", "[DEMO] The score sorting makes this one stand out."),
  @("vector", "shadow", "[DEMO] Good example of an active discussion."),
  @("rouge", "route", "[DEMO] This gives the filters another useful result."),
  @("knuckles", "route", "[DEMO] The gadget list stays easy to scan."),
  @("cream", "boost", "[DEMO] A nice contrast with the lower-scoring entries."),
  @("vector", "boost", "[DEMO] Helpful for a score-sort presentation."),
  @("amy", "sonic-speed", "[DEMO] This makes a clear stock-machine example."),
  @("tails", "sonic-speed", "[DEMO] The loadout order reads well on the details page."),
  @("rouge", "sonic-speed", "[DEMO] A strong high-confidence sample for later ranking work."),
  @("sonic", "shadow-laps", "[DEMO] The second speed build makes browsing feel less uniform."),
  @("blaze", "shadow-laps", "[DEMO] This is a useful comparison build."),
  @("tails", "amy-drift", "[DEMO] The ordered gadgets are clear at a glance."),
  @("silver", "amy-drift", "[DEMO] Good candidate for a build-detail demo."),
  @("vector", "tails-grid", "[DEMO] This keeps the My Builds view active."),
  @("cream", "knuckles-power", "[DEMO] A short note is enough for this smaller sample.")
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

Write-Host "Demo dataset is ready: 60 users (5 build owners and 55 voter-only accounts), 17 builds, $($voteDefinitions.Count) votes, and $($commentDefinitions.Count) comments."
Write-Host "Demo password for every account: $DemoPassword"
