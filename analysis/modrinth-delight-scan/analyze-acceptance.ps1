Set-Location $PSScriptRoot
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression.FileSystem

$jarSpecs = @()
foreach ($path in @('downloaded-mods.json', 'local-delight-mods.json')) {
	$full = Join-Path $PSScriptRoot $path
	if (Test-Path $full) {
		$jarSpecs += @(Get-Content $full -Raw | ConvertFrom-Json)
	}
}

$excludedTagSegmentPattern = '(?:^|_)(seed|seeds|storage|storage_block|storage_blocks|crate|crates|bag|bags|slice|slices|soup|stew|salad|jam|jelly|juice|powder|meal|flour|oil|syrup|sauce|roasted|cooked|smoked|baked|fried|stuffed|pickled|fermented|dried|pie|cake|cookie|sandwich|burger|taco|dumpling|roll|rolls|pasta|ingredient|ingredients|mix|mixture|bottle|jar|bowl|plate|cup|mug|box|boxes)(?:$|_)'
$excludedItemPathPattern = '(?:^|_)(seed|seeds|seed_bag|seeds_bag|crate|crates|bag|bags|slice|slices|soup|stew|salad|jam|jelly|juice|powder|meal|flour|oil|syrup|sauce|roasted|cooked|smoked|baked|fried|stuffed|pickled|fermented|dried|pie|cake|cookie|sandwich|burger|taco|dumpling|roll|rolls|pasta|ingredient|ingredients|mix|mixture|bottle|jar|bowl|plate|cup|mug|box|boxes)(?:$|_)'
$foodKeywords = @(
	'crop', 'vegetable', 'fruit', 'food', 'seed', 'berry',
	'meat', 'fish', 'egg', 'milk', 'cheese', 'butter', 'cream',
	'bread', 'cake', 'pie', 'soup', 'stew', 'salad', 'rice',
	'wheat', 'corn', 'potato', 'carrot', 'tomato', 'onion',
	'garlic', 'pepper', 'lettuce', 'cabbage', 'beet', 'bean',
	'apple', 'banana', 'orange', 'grape', 'melon', 'pumpkin',
	'mushroom', 'tea', 'coffee', 'juice', 'wine', 'beer',
	'sugar', 'salt', 'spice', 'herb', 'oil', 'vinegar',
	'flour', 'dough', 'pasta', 'noodle', 'dumpling', 'taco',
	'burger', 'sandwich', 'pizza', 'sushi', 'kimchi', 'tofu',
	'jam', 'jelly', 'honey', 'syrup', 'chocolate', 'candy',
	'cookie', 'biscuit', 'muffin', 'pancake', 'waffle',
	'roast', 'grill', 'fry', 'bake', 'cook', 'smoke',
	'delight', 'tasty', 'yummy', 'delicious', 'cuisine',
	'edible', 'nutrition', 'meal', 'snack', 'dish'
)
$groupBlacklist = @(
	'all', 'every', 'any', 'none',
	'crop', 'vegetable', 'food', 'fruit', 'grain',
	'block', 'item', 'ingredient', 'material', 'resource',
	'component', 'element', 'thing', 'stuff',
	'compressed', 'dense', 'tier', 'level', 'grade',
	'mix', 'mixed', 'combined', 'general', 'common',
	'various', 'multiple', 'misc', 'other',
	'edible', 'plant', 'seed', 'essence',
	'raw', 'cooked', 'processed', 'crafted',
	'output', 'input', 'result', 'product',
	'base', 'basic', 'simple', 'generic'
)
$allowedVariantSuffixes = @('red', 'green', 'yellow', 'white', 'black', 'purple', 'orange', 'brown', 'pink', 'blue', 'gold', 'golden')
$aliases = @{
	tomatoes = 'tomato'
	potatoes = 'potato'
	bellpepper = 'bell_pepper'
	hotpepper = 'hot_pepper'
	sweetpotato = 'sweet_potato'
	greenonion = 'green_onion'
	greenbean = 'green_bean'
	sweetcorn = 'sweet_corn'
	yellow_bell_pepper = 'bell_pepper_yellow'
	green_bell_pepper = 'bell_pepper_green'
	red_bell_pepper = 'bell_pepper_red'
	yellow_bellpepper = 'bell_pepper_yellow'
	green_bellpepper = 'bell_pepper_green'
	red_bellpepper = 'bell_pepper_red'
}
$suspiciousTokens = @(
	'bucket', 'buckets', 'bundle', 'bundles', 'basket', 'baskets', 'sack', 'sacks',
	'pack', 'packs', 'package', 'packages', 'can', 'cans', 'tin', 'tins',
	'barrel', 'barrels', 'cut', 'cuts', 'chunk', 'chunks', 'piece', 'pieces',
	'diced', 'chopped', 'minced', 'mashed', 'paste', 'pulp', 'broth', 'stock', 'extract'
)
$auxiliaryTagPathPrefixes = @('jei_display_results/', 'display_results/')
$auxiliaryTagSegmentPrefixes = @('can_', 'flat_on_', 'used_in_', 'works_with_', 'supports_')
$auxiliaryTagSegmentSuffixes = @('_food', '_foods', '_snack', '_snacks', '_plantable', '_feedable')

function Singularize([string] $name) {
	if ($name.EndsWith('ies') -and $name.Length -gt 3) { return $name.Substring(0, $name.Length - 3) + 'y' }
	if ($name.EndsWith('oes') -and $name.Length -gt 3) { return $name.Substring(0, $name.Length - 2) }
	if ($name.EndsWith('us') -or $name.EndsWith('ss')) { return $name }
	if ($name.EndsWith('s') -and $name.Length -gt 1) { return $name.Substring(0, $name.Length - 1) }
	return $name
}

function NormalizeGroup([string] $raw) {
	$match = [regex]::Match($raw.ToLowerInvariant(), '(?:^|/)([a-z0-9_]+)$')
	if (-not $match.Success) { return $null }

	$candidate = ([regex]::Replace($match.Groups[1].Value, '[^a-z0-9_]+', '_')).Trim('_')
	if ([string]::IsNullOrWhiteSpace($candidate)) { return $null }
	if ($aliases.ContainsKey($candidate)) { return $aliases[$candidate] }

	return Singularize $candidate
}

function IsFoodPath([string] $path) {
	if ($path -match $excludedItemPathPattern) { return $false }

	foreach ($keyword in $foodKeywords) {
		if ($path.Contains($keyword)) {
			return $true
		}
	}

	return $false
}

function IsCompatible([string] $normalizedItemName, [string] $groupName) {
	if ($normalizedItemName -eq $groupName) { return $true }
	if (-not $normalizedItemName.StartsWith($groupName + '_')) { return $false }

	$suffix = $normalizedItemName.Substring($groupName.Length + 1)
	if ([string]::IsNullOrEmpty($suffix)) { return $false }

	foreach ($part in $suffix.Split('_')) {
		if ($allowedVariantSuffixes -notcontains $part) {
			return $false
		}
	}

	return $true
}

function IsAuxiliaryTag([string] $tagPath) {
	if ($auxiliaryTagPathPrefixes | Where-Object { $tagPath.StartsWith($_) }) {
		return $true
	}

	foreach ($segment in $tagPath.Split('/')) {
		if (($auxiliaryTagSegmentPrefixes | Where-Object { $segment.StartsWith($_) }) -or ($auxiliaryTagSegmentSuffixes | Where-Object { $segment.EndsWith($_) })) {
			return $true
		}
	}

	return $false
}

$structuralPrefixes = @(
	'mineable/', 'needs_', 'fences', 'fence_gates', 'walls',
	'stairs', 'slabs', 'doors', 'trapdoors', 'buttons',
	'pressure_plates', 'signs', 'banners', 'beds'
)

$accepted = New-Object System.Collections.Generic.List[object]

foreach ($jar in $jarSpecs) {
	try {
		$zip = [System.IO.Compression.ZipFile]::OpenRead($jar.file)
	} catch {
		continue
	}

	try {
		foreach ($entry in $zip.Entries) {
			if ($entry.FullName -notmatch '^data/([^/]+)/tags/items/(.+)\.json$') {
				continue
			}

			$namespace = $Matches[1]
			$tagPath = $Matches[2].ToLowerInvariant()
			$fullTag = "${namespace}:$tagPath"

			if ($tagPath.Split('/') | Where-Object { $_ -match $excludedTagSegmentPattern }) {
				continue
			}

			if (IsAuxiliaryTag $tagPath) {
				continue
			}

			if ($structuralPrefixes | Where-Object { $tagPath.StartsWith($_) }) {
				continue
			}

			$group = NormalizeGroup $tagPath
			if ($null -eq $group -or $groupBlacklist -contains $group) {
				continue
			}

			$reader = New-Object System.IO.StreamReader($entry.Open())
			try {
				$raw = $reader.ReadToEnd()
			} finally {
				$reader.Dispose()
			}

			try {
				$json = $raw | ConvertFrom-Json
			} catch {
				continue
			}

			$values = @()
			if ($null -ne $json.values) {
				foreach ($value in $json.values) {
					if ($value -is [string] -and -not $value.StartsWith('#')) {
						$values += $value.ToLowerInvariant()
					}
				}
			}

			if ($values.Count -eq 0) {
				continue
			}

			$foodCount = 0
			$nonFoodCount = 0
			foreach ($value in $values) {
				$parts = $value -split ':', 2
				if ($parts.Count -ne 2) {
					continue
				}

				$itemPath = $parts[1]
				if (IsFoodPath $itemPath) {
					$foodCount++
				} else {
					$nonFoodCount++
				}
			}

			$total = $foodCount + $nonFoodCount
			if ($total -eq 0 -or ($foodCount / $total) -lt 0.3) {
				continue
			}

			$confidence = if ($tagPath.StartsWith('crops/') -or $tagPath.StartsWith('vegetables/')) { 'STRONG' } else { 'WEAK' }
			$considered = 0
			$acceptedMembers = New-Object System.Collections.Generic.List[string]

			foreach ($value in $values) {
				$parts = $value -split ':', 2
				if ($parts.Count -ne 2) {
					continue
				}

				$itemPath = $parts[1]
				$considered++

				if ($itemPath -match $excludedItemPathPattern) {
					continue
				}

				$normalizedItem = NormalizeGroup $itemPath
				if ($null -eq $normalizedItem) {
					continue
				}

				if (IsCompatible $normalizedItem $group) {
					$acceptedMembers.Add($value)
				}
			}

			if ($considered -eq 0 -or $acceptedMembers.Count -eq 0) {
				continue
			}

			$acceptedRatio = $acceptedMembers.Count / $considered
			$accept = (($confidence -eq 'STRONG' -and $acceptedRatio -ge 0.5) -or ($confidence -eq 'WEAK' -and $acceptedMembers.Count -eq $considered))
			if (-not $accept) {
				continue
			}

			$hitTokens = New-Object System.Collections.Generic.HashSet[string]
			foreach ($token in $suspiciousTokens) {
				$tokenPattern = '(?:^|_)' + [regex]::Escape($token) + '(?:$|_)'
				if ($group -match $tokenPattern) {
					[void] $hitTokens.Add($token)
				}

				foreach ($member in $acceptedMembers) {
					$parts = $member -split ':', 2
					if ($parts.Count -eq 2 -and $parts[1] -match $tokenPattern) {
						[void] $hitTokens.Add($token)
					}
				}
			}

			if ($hitTokens.Count -gt 0) {
				$accepted.Add([pscustomobject]@{
					jar = $jar.slug
					tag = $fullTag
					group = $group
					confidence = $confidence
					considered = $considered
					accepted = $acceptedMembers.Count
					members = @($acceptedMembers | Select-Object -First 10)
					suspicious = @($hitTokens | Sort-Object)
				})
			}
		}
	} finally {
		$zip.Dispose()
	}
}

$accepted | Sort-Object group, tag | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $PSScriptRoot 'suspicious-accepted-groups.json')
Write-Output ('SuspiciousAccepted=' + $accepted.Count)
