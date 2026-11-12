# Count completed event records, never phase starts or per-worker statistics.
function Get-GcLogSignals {
    param([AllowEmptyString()][string]$Logs)

    $available = $Logs -match '\[gc(?:,|\s*\])'
    if (-not $available) {
        return [pscustomobject]@{
            gcLogEvidenceAvailable = $false
            fullGcSummaryLines = $null
            explicitSystemGcFullLines = $null
            evacuationFailureCollections = $null
            evacuationFailureMentions = $null
            allocationStallEvents = $null
        }
    }
    return [pscustomobject]@{
        gcLogEvidenceAvailable = $true
        fullGcSummaryLines = [regex]::Matches($Logs, '(?m)\[gc\s*\].*Pause Full.*\d+(?:\.\d+)?ms').Count
        explicitSystemGcFullLines = [regex]::Matches($Logs, '(?m)\[gc\s*\].*Pause Full.*System\.gc\(\).*\d+(?:\.\d+)?ms').Count
        evacuationFailureCollections = @([regex]::Matches($Logs,
            '(?m)\[gc\s*\].*GC\((\d+)\).*Pause Young.*\(Evacuation Failure[^)]*\)') |
            ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique).Count
        evacuationFailureMentions = [regex]::Matches($Logs, '(?im)Evacuation Fail|To-space exhausted').Count
        allocationStallEvents = [regex]::Matches($Logs, 'Allocation Stall \(').Count
    }
}
