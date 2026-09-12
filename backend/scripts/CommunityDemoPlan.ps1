# Pure, offline fixture planning. Percentages are editorial choices, not player statistics.
function Get-CommunityDemoExpansion {
  param([object[]]$Users)

  $random = [System.Random]::new(20260912)
  $sonic = 'Sonic the Hedgehog'
  $tails = 'Miles "Tails" Prower'
  $shadow = 'Shadow the Hedgehog'
  $amy = 'Amy Rose'
  $knuckles = 'Knuckles the Echidna'
  # Each author has a small recurring cast and their own titles/voice. The 43 additions
  # complement the legacy 17: the complete plan contains 36 main, 18 other, 6 guest builds.
  $profiles = @(
    @{ Key = 'amy'; Racers = @($amy, $amy, $tails); Titles = @('Amy is still my first pick', 'Another evening with Amy', 'Borrowing a Tails setup'); Note = 'Usually an Amy player. Saving this one so I can come back to it.' },
    @{ Key = 'tails'; Racers = @($tails, $tails); Titles = @('Tails and a notebook full of ideas', 'Keeping it simple with Tails'); Note = 'I keep returning to Tails, even when I mean to try someone else.' },
    @{ Key = 'shadow'; Racers = @($shadow, $shadow, $sonic); Titles = @('The Shadow setup I keep returning to', 'Shadow with a different machine mix', 'Fine, one evening as Sonic'); Note = 'Mostly a Shadow main. This is another combination from my saved list.' },
    @{ Key = 'sonic'; Racers = @($sonic, $sonic, $tails, $sonic); Titles = @('Sonic for the weekend', 'One more Sonic experiment', 'Team Sonic, different driver', 'Back to the blue blur'); Note = 'Sonic is my default pick. I would rather try another machine combination than give him up.' },
    @{ Key = 'knuckles'; Racers = @($knuckles, $knuckles); Titles = @('Knuckles stays on the character select', 'My other Knuckles loadout'); Note = 'Still picking Knuckles. No claim that this is the best setup; I just wanted to share mine.' },
    @{ Key = 'rouge'; Racers = @('Rouge the Bat', 'Rouge the Bat'); Titles = @('A little more Rouge on the grid', 'Rouge, take two'); Note = 'Here for Rouge. Keeping a couple of different configurations saved.' },
    @{ Key = 'cream'; Racers = @('Cream & Cheese'); Titles = @('A spot on the grid for Cream'); Note = 'Just happy to have Cream and Cheese here.' },
    @{ Key = 'blaze'; Racers = @('Blaze the Cat', 'Blaze the Cat'); Titles = @('Blaze deserves more builds', 'My second saved Blaze setup'); Note = 'Blaze is always on my shortlist. This is the combination I wanted to save today.' },
    @{ Key = 'silver'; Racers = @('Silver the Hedgehog', 'Silver the Hedgehog'); Titles = @('Silver fans checking in', 'Still experimenting with Silver'); Note = 'I tend to stick with Silver and change the rest around him.' },
    @{ Key = 'vector'; Racers = @('Vector the Crocodile'); Titles = @('Someone has to bring Vector'); Note = 'A small contribution from the Chaotix corner.' },
    @{ Key = 'blueblur'; Racers = @($sonic, $tails, $sonic); Titles = @('My usual Sonic pick', 'Taking Tails out for a change', 'Sonic, but a different garage visit'); Note = 'Team Sonic is where I spend most of my time.' },
    @{ Key = 'ultimatefan'; Racers = @($shadow, $shadow); Titles = @('Another Shadow main reporting in', 'Shadow and my spare machine setup'); Note = 'The character choice was never in question.' },
    @{ Key = 'rosegrid'; Racers = @($amy, $amy); Titles = @('Pink on the starting grid', 'Amy with my alternate loadout'); Note = 'I mostly play Amy. Comparing a few saved configurations before choosing a regular one.' },
    @{ Key = 'metalhead'; Racers = @('Metal Sonic', 'Metal Sonic', 'Metal Sonic'); Titles = @('Metal Sonic gets a garage slot', 'A second idea for Metal', 'Back to my original Metal pick'); Note = 'Metal Sonic fan first, build tinkerer second.' },
    @{ Key = 'chaotix'; Racers = @('Espio the Chameleon', 'Vector the Crocodile'); Titles = @('Espio, finally on my saved list', 'Switching detectives for a night'); Note = 'Sharing a couple of Chaotix picks. Would like to see more of them here.' },
    @{ Key = 'eggman'; Racers = @('Dr. Eggman', 'Dr. Eggman'); Titles = @('The doctor joins the grid', 'Eggman has another idea'); Note = 'Someone needs to represent the Eggman fans.' },
    @{ Key = 'bigfan'; Racers = @('Big the Cat'); Titles = @('A quiet evening with Big'); Note = 'Big is my comfort pick. Nothing more complicated than that.' },
    @{ Key = 'phantom'; Racers = @('Joker', 'Joker'); Titles = @('A guest in the Sonic garage', 'Keeping a second Joker setup'); Note = 'Came for Joker, stayed to browse everyone else''s builds.' },
    @{ Key = 'megafan'; Racers = @('Mega Man', 'Mega Man'); Titles = @('The other blue character', 'Mega Man with a new combination'); Note = 'Mostly here for Mega Man, but I appreciate all the Sonic experiments.' },
    @{ Key = 'crossover'; Racers = @('SpongeBob SquarePants', 'Joker'); Titles = @('A very different kind of weekend pick', 'Trying another guest character'); Note = 'I like hopping between guest characters. This is my latest saved combination.' }
  )
  $machines = @('Speedster Lightning', 'Dark Reaper', 'Whirlwind Sport', 'Pink Cabriolet',
    'Neo Lightron', 'Land Smasher', 'Road Dragoon', 'TYPE-J Iota', 'TYPE-S Stream', 'Jumble Rage')
  $gadgetSets = @(
    @('Ring Engine', 'Strong Finish'), @('Inventory Swap', 'Lucky Pair', 'Item Mercy'),
    @('Boost Item Chance UP', 'Hyper Ring Engine'), @('Damage Mercy', 'Ring Mercy'),
    @('Route Planner Bounty', 'Ring Doubler', 'Strong Finish'), @('Attack Item Chance UP', 'Double Down'),
    @('Ultimate Charge', 'Less is More'), @('Defense Item Chance UP', 'Item Stock Plus', 'Lucky Pair')
  )
  # Attention varies independently of approval. Even a Sonic main can post an unpopular
  # experiment, and a guest can post a well-received build. Include overlooked entries.
  $reception = @(@(40,1), @(3,0), @(1,0), @(20,40), @(0,0), @(7,2), @(2,0), @(0,1),
    @(16,2), @(5,5), @(28,4), @(1,0), @(0,0), @(3,2), @(6,1), @(0,0), @(10,3))
  $versions = @('1.4.1', '1.4.1', '1.3.1', $null, '1.4.1', '1.2.2', '1.2.0')
  $builds = @()
  foreach ($profile in $profiles) {
    for ($index = 0; $index -lt $profile.Titles.Count; $index++) {
      $number = $builds.Count
      $machine = $machines[$random.Next($machines.Count)]
      $rear = $machine
      if ($number % 3 -eq 1) {
        $alternatives = @($machines | Where-Object { $_ -ne $machine })
        $rear = $alternatives[$random.Next($alternatives.Count)]
      }
      $gadgets = @($gadgetSets[$random.Next($gadgetSets.Count)])
      $description = $profile.Note
      if ($index % 2 -eq 0) {
        $description += " Front and tires from $machine; rear from $rear. I am keeping $($gadgets[0]) first in this saved list so it is easy to compare with my other entries."
      }
      $counts = $reception[$number % $reception.Count]
      # A well-received guest build is intentional; Sonic popularity is not a quality bonus.
      if ($profile.Key -eq 'phantom' -and $index -eq 0) { $counts = @(18, 1) }
      $builds += [pscustomobject]@{
        Key = "community-$($profile.Key)-$($index + 1)"; Owner = $profile.Key
        Title = $profile.Titles[$index]; Description = $description
        Racer = $profile.Racers[$index]; Machine = $machine; RearMachine = $rear
        Gadgets = $gadgets; Version = $versions[$number % $versions.Count]
        Upvotes = $counts[0]; Downvotes = $counts[1]
      }
    }
  }

  $votes = @()
  $comments = @()
  foreach ($build in $builds) {
    # Active members get more tickets. Fans of this racer are more likely to notice
    # the build, but do not automatically approve it. Owners never vote on their new builds.
    $tickets = @()
    for ($index = 0; $index -lt $Users.Count; $index++) {
      $user = $Users[$index]
      if ($user.Key -eq $build.Owner) { continue }
      $profile = $profiles | Where-Object { $_.Key -eq $user.Key } | Select-Object -First 1
      $favorites = if ($null -ne $profile) { $profile.Racers } else { $profiles[$index % $profiles.Count].Racers }
      $weight = if ($index % 5 -eq 0) { 4 } else { 1 }
      if ($favorites -contains $build.Racer) { $weight += 3 }
      for ($ticket = 0; $ticket -lt $weight; $ticket++) { $tickets += $user.Key }
    }
    $participants = @()
    $total = $build.Upvotes + $build.Downvotes
    for ($index = 0; $index -lt $total; $index++) {
      $voter = $tickets[$random.Next($tickets.Count)]
      $participants += $voter
      $tickets = @($tickets | Where-Object { $_ -ne $voter })
    }
    # Shuffle selected readers before assigning approval, so affinity affects attention only.
    for ($index = $participants.Count - 1; $index -gt 0; $index--) {
      $other = $random.Next($index + 1)
      $participants[$index], $participants[$other] = $participants[$other], $participants[$index]
    }
    for ($index = 0; $index -lt $participants.Count; $index++) {
      $value = if ($index -lt $build.Upvotes) { 1 } else { -1 }
      $votes += ,@($participants[$index], $build.Key, $value)
    }
    if ($total -lt 4) { continue }
    $comments += ,@($participants[0], $build.Key, "Always good to see another $($build.Racer) build here.")
    if ($total -ge 10) {
      $comments += ,@($participants[1], $build.Key, "What made you pick the $($build.RearMachine) rear for this one?")
      $comments += ,@($build.Owner, $build.Key, "Mostly curiosity. I wanted to save a combination with $($build.RearMachine) and compare it with my other picks.")
    }
    if ($total -ge 30) {
      $comments += ,@($participants[2], $build.Key, "I usually pick a different machine, but I like seeing what other people are trying.")
      $comments += ,@($participants[3], $build.Key, "Saved this to look at later. Still deciding what to try next.")
    }
  }
  return [pscustomobject]@{ Builds = $builds; Votes = $votes; Comments = $comments }
}
