Set-Location $PSScriptRoot
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$sourceArchiveDir = Join-Path $PSScriptRoot 'source-archives'
New-Item -ItemType Directory -Force -Path $sourceArchiveDir | Out-Null

$headers = @{
	'User-Agent' = 'GitHub-Copilot'
	'Accept' = 'application/vnd.github+json'
}

function NormalizeCachedList($data) {
	if ($null -eq $data) {
		return @()
	}

	if ($data -is [array]) {
		return $data
	}

	if ($data.PSObject.Properties.Name -contains 'value') {
		return (NormalizeCachedList $data.value)
	}

	return @($data)
}

function NormalizeCachedRepoSummaries($data) {
	$data = NormalizeCachedList $data
	if ($data.Count -eq 1) {
		$first = $data[0]
		if ($first.PSObject.Properties.Name -contains 'full_name' -and $first.full_name -is [array]) {
			$fullNames = $first.full_name
			$htmlUrls = $first.html_url
			$defaultBranches = $first.default_branch
			$descriptions = $first.description
			$rows = for ($index = 0; $index -lt $fullNames.Count; $index++) {
				[pscustomobject]@{
					full_name = $fullNames[$index]
					html_url = $htmlUrls[$index]
					default_branch = $defaultBranches[$index]
					description = $descriptions[$index]
				}
			}

			return $rows
		}
	}

	return $data
}

$repoSummaryPath = Join-Path $PSScriptRoot 'repo-summaries.json'
$releaseAssetPath = Join-Path $PSScriptRoot 'release-assets.json'

if (Test-Path $repoSummaryPath) {
	$cachedRepoItems = Get-Content $repoSummaryPath -Raw | ConvertFrom-Json
	if ($cachedRepoItems.PSObject.Properties.Name -contains 'full_name' -and $cachedRepoItems.full_name -is [array]) {
		$repoItems = for ($index = 0; $index -lt $cachedRepoItems.full_name.Count; $index++) {
			[pscustomobject]@{
				full_name = $cachedRepoItems.full_name[$index]
				html_url = $cachedRepoItems.html_url[$index]
				default_branch = $cachedRepoItems.default_branch[$index]
				description = $cachedRepoItems.description[$index]
			}
		}
	} else {
		$repoItems = @(NormalizeCachedList $cachedRepoItems)
	}
} else {
	$query = [uri]::EscapeDataString('"Farmer''s Delight" addon')
	$searchResponse = Invoke-RestMethod -Headers $headers -Uri "https://api.github.com/search/repositories?q=$query&sort=updated&order=desc&per_page=20"
	$repoItems = @($searchResponse.items)
}

$repoSummaries = New-Object System.Collections.Generic.List[object]
$releaseAssets = New-Object System.Collections.Generic.List[object]
$tagEntries = New-Object System.Collections.Generic.List[object]

if (Test-Path $releaseAssetPath) {
	foreach ($asset in @(NormalizeCachedList (Get-Content $releaseAssetPath -Raw | ConvertFrom-Json))) {
		$releaseAssets.Add($asset)
	}
}

foreach ($repo in $repoItems) {
	$repoSummary = [pscustomobject]@{
		full_name = $repo.full_name
		html_url = $repo.html_url
		default_branch = $repo.default_branch
		description = $repo.description
	}
	$repoSummaries.Add($repoSummary)

	if (-not (Test-Path $releaseAssetPath)) {
		try {
			$releaseResponse = Invoke-RestMethod -Headers $headers -Uri "https://api.github.com/repos/$($repo.full_name)/releases?per_page=5"
			foreach ($release in @($releaseResponse)) {
				foreach ($asset in @($release.assets)) {
					$releaseAssets.Add([pscustomobject]@{
						repo = $repo.full_name
						release = $release.tag_name
						asset_name = $asset.name
						asset_url = $asset.browser_download_url
						size = $asset.size
					})
				}
			}
		} catch {
		}
	}

	if ([string]::IsNullOrWhiteSpace($repo.default_branch)) {
		continue
	}

	$fullName = [string] $repo.full_name
	$branchName = [string] $repo.default_branch
	$encodedBranch = [uri]::EscapeDataString($branchName)
	$safeRepoName = ($fullName -replace '[^a-zA-Z0-9._-]', '_')
	$safeBranchName = ($branchName -replace '[^a-zA-Z0-9._-]', '_')
	$archivePath = Join-Path $sourceArchiveDir ($safeRepoName + '-' + $safeBranchName + '.zip')
	$archiveUrl = "https://codeload.github.com/$fullName/zip/refs/heads/$encodedBranch"

	try {
		if (-not (Test-Path $archivePath)) {
			Invoke-WebRequest -Headers $headers -Uri $archiveUrl -OutFile $archivePath
		}
	} catch {
		continue
	}

	try {
		$zip = [System.IO.Compression.ZipFile]::OpenRead($archivePath)
	} catch {
		continue
	}

	try {
		foreach ($entry in $zip.Entries) {
			if ($entry.FullName -notmatch '(?:^|/)data/.+/tags/items/.+\.json$') {
				continue
			}

			$tagEntries.Add([pscustomobject]@{
				repo = $repo.full_name
				path = $entry.FullName
				url = $repo.html_url
			})
		}
	} finally {
		$zip.Dispose()
	}
}

$repoSummaries.ToArray() | Sort-Object full_name | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $repoSummaryPath
$releaseAssets.ToArray() | Sort-Object repo, release, asset_name -Unique | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $releaseAssetPath
$tagEntries.ToArray() | Sort-Object repo, path | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $PSScriptRoot 'tag-entries.json')

Write-Output ('Repos=' + $repoSummaries.Count)
Write-Output ('ReleaseAssets=' + $releaseAssets.Count)
Write-Output ('TagEntries=' + $tagEntries.Count)