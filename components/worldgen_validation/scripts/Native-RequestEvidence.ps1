function Read-NativeRequestSidecar {
    param([Parameter(Mandatory)][string]$RawPath, [Parameter(Mandatory)][object]$RawResult)
    Set-StrictMode -Version Latest
    $ErrorActionPreference = 'Stop'
    $proof = Get-Content -LiteralPath ($RawPath + '.requests.json') -Raw | ConvertFrom-Json
    if ($proof.sidecarVersion -ne 1 -or $proof.rawResult -cne [IO.Path]::GetFullPath($RawPath) -or
            $proof.rawResultSha256 -cne (Get-FileHash -LiteralPath $RawPath).Hash -or
            !$proof.bodyReturned -or !$proof.serverThread -or $proof.retainedChunks -ne 0 -or $proof.retainedServer -or
            $proof.observedJoins -ne $RawResult.returnedFullChunks) {
        throw 'Invalid native request sidecar, observer cleanup or changed raw result'
    }
    return $proof
}
