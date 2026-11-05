# After packageMsi: ./scripts/verify-sign-windows.ps1 -ExpectedVersion 1.39.0 -PackagePath <one.msi>
# Zero WIN_PFX secrets means explicitly unsigned; either secret requires both.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$ExpectedVersion,

    [Parameter(Mandatory = $true, Position = 1)]
    [string]$PackagePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-MsiProductVersion {
    param([string]$Path)
    $installer = $null
    $database = $null
    $view = $null
    $record = $null
    try {
        $installer = New-Object -ComObject WindowsInstaller.Installer
        $database = $installer.GetType().InvokeMember(
            'OpenDatabase', [Reflection.BindingFlags]::InvokeMethod, $null, $installer,
            [object[]]@($Path, 0)) # msiOpenDatabaseModeReadOnly
        $view = $database.GetType().InvokeMember(
            'OpenView', [Reflection.BindingFlags]::InvokeMethod, $null, $database,
            [object[]]@('SELECT `Value` FROM `Property` WHERE `Property` = ''ProductVersion'''))
        $null = $view.GetType().InvokeMember(
            'Execute', [Reflection.BindingFlags]::InvokeMethod, $null, $view, $null)
        $record = $view.GetType().InvokeMember(
            'Fetch', [Reflection.BindingFlags]::InvokeMethod, $null, $view, $null)
        if ($null -eq $record) {
            throw 'MSI has no ProductVersion property.'
        }
        return [string]$record.GetType().InvokeMember(
            'StringData', [Reflection.BindingFlags]::GetProperty, $null, $record,
            [object[]]@(1))
    }
    finally {
        if ($null -ne $view) {
            try {
                $null = $view.GetType().InvokeMember(
                    'Close', [Reflection.BindingFlags]::InvokeMethod, $null, $view, $null)
            }
            catch { } # Still release every COM object if Close fails.
        }
        foreach ($comObject in @($record, $view, $database, $installer)) {
            if ($null -ne $comObject -and [Runtime.InteropServices.Marshal]::IsComObject($comObject)) {
                $null = [Runtime.InteropServices.Marshal]::FinalReleaseComObject($comObject)
            }
        }
    }
}

function Find-SignTool {
    $command = Get-Command signtool.exe -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -ne $command -and (Test-Path -LiteralPath $command.Source -PathType Leaf)) {
        return $command.Source
    }

    $architecture = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'arm64' }
        elseif ([Environment]::Is64BitOperatingSystem) { 'x64' } else { 'x86' }
    $architectures = @($architecture, 'x64', 'x86') | Select-Object -Unique
    $binRoots = [Collections.Generic.List[string]]::new()
    foreach ($configured in @($env:WindowsSdkVerBinPath, $env:WindowsSdkBinPath)) {
        if (-not [string]::IsNullOrWhiteSpace($configured)) {
            $binRoots.Add($configured)
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($env:WindowsSdkDir)) {
        $binRoots.Add((Join-Path $env:WindowsSdkDir 'bin'))
    }
    foreach ($programFiles in @(${env:ProgramFiles(x86)}, $env:ProgramFiles)) {
        if (-not [string]::IsNullOrWhiteSpace($programFiles)) {
            foreach ($sdk in @('10', '8.1')) {
                $binRoots.Add((Join-Path $programFiles "Windows Kits/$sdk/bin"))
            }
        }
    }
    foreach ($registryPath in @(
        'HKLM:\SOFTWARE\Microsoft\Windows Kits\Installed Roots',
        'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows Kits\Installed Roots'
    )) {
        $roots = Get-ItemProperty -LiteralPath $registryPath -ErrorAction SilentlyContinue
        if ($null -ne $roots) {
            foreach ($rootName in @('KitsRoot10', 'KitsRoot81')) {
                $property = $roots.PSObject.Properties[$rootName]
                if ($null -ne $property -and -not [string]::IsNullOrWhiteSpace($property.Value)) {
                    $binRoots.Add((Join-Path $property.Value 'bin'))
                }
            }
        }
    }

    foreach ($binRoot in ($binRoots | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $binRoot -PathType Container)) { continue }
        # Only enumerate immediate SDK version directories, never the drive.
        $versions = Get-ChildItem -LiteralPath $binRoot -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^\d+\.\d+\.\d+\.\d+$' } |
            Sort-Object { [version]$_.Name } -Descending
        $directories = @($versions | ForEach-Object { $_.FullName }) + @($binRoot)
        foreach ($directory in $directories) {
            foreach ($arch in $architectures) {
                $candidate = Join-Path $directory "$arch/signtool.exe"
                if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
            }
            $candidate = Join-Path $directory 'signtool.exe'
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        }
    }
    throw 'Signing secrets configured but signtool.exe was not found in PATH or a Windows SDK.'
}

$tempDirectory = $null
$pfxPath = $null
$stage = 'release verification'
$failed = $false
try {
    $hasCertificate = -not [string]::IsNullOrEmpty($env:WIN_PFX_BASE64)
    $hasPassword = -not [string]::IsNullOrEmpty($env:WIN_PFX_PASSWORD)
    if ($hasCertificate -ne $hasPassword) {
        throw 'Both WIN_PFX_BASE64 and WIN_PFX_PASSWORD must be configured, or neither.'
    }
    if ($ExpectedVersion -notmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') {
        throw 'ExpectedVersion must be numeric major.minor.patch without v.'
    }
    $version = [version]$ExpectedVersion
    if ($version.Major -gt 255 -or $version.Minor -gt 255 -or $version.Build -gt 65535) {
        throw 'ExpectedVersion exceeds MSI ProductVersion limits (255.255.65535).'
    }
    if (-not (Test-Path -LiteralPath $PackagePath -PathType Leaf) -or
        [IO.Path]::GetExtension($PackagePath) -ine '.msi') {
        throw 'PackagePath must identify one existing MSI.'
    }
    $package = (Get-Item -LiteralPath $PackagePath).FullName
    $stage = 'MSI ProductVersion verification'
    $productVersion = Read-MsiProductVersion -Path $package
    if ($productVersion -cne $ExpectedVersion) {
        throw 'Embedded MSI ProductVersion does not match ExpectedVersion.'
    }
    Write-Host "MSI embedded ProductVersion verified: $ExpectedVersion"

    if ($hasCertificate) {
        $stage = 'signtool discovery'
        $signTool = Find-SignTool
        $stage = 'temporary certificate preparation'
        $tempRoot = if ([string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
            [IO.Path]::GetTempPath()
        } else { $env:RUNNER_TEMP }
        if (-not (Test-Path -LiteralPath $tempRoot -PathType Container)) {
            throw 'Runner temporary directory does not exist.'
        }
        $tempDirectory = Join-Path $tempRoot ('matrix-win-sign-' + [Guid]::NewGuid().ToString('N'))
        $null = New-Item -ItemType Directory -Path $tempDirectory
        # Restrict access before decoding/writing the PKCS#12 certificate.
        $acl = [Security.AccessControl.DirectorySecurity]::new()
        $acl.SetAccessRuleProtection($true, $false)
        $currentUser = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $rule = [Security.AccessControl.FileSystemAccessRule]::new(
            $currentUser, [Security.AccessControl.FileSystemRights]::FullControl,
            [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit',
            [Security.AccessControl.PropagationFlags]::None,
            [Security.AccessControl.AccessControlType]::Allow)
        $acl.AddAccessRule($rule)
        Set-Acl -LiteralPath $tempDirectory -AclObject $acl
        $pfxPath = Join-Path $tempDirectory 'certificate.pfx'
        $certificateBytes = [Convert]::FromBase64String($env:WIN_PFX_BASE64)
        try {
            if ($certificateBytes.Length -eq 0) { throw 'Decoded certificate is empty.' }
            [IO.File]::WriteAllBytes($pfxPath, $certificateBytes)
        }
        finally {
            [Array]::Clear($certificateBytes, 0, $certificateBytes.Length)
        }
        $stage = 'MSI Authenticode signing'
        # Suppress tool diagnostics: they may contain sensitive arguments.
        & $signTool sign /fd SHA256 /td SHA256 /tr 'https://timestamp.digicert.com' /f $pfxPath /p $env:WIN_PFX_PASSWORD $package *> $null
        if ($LASTEXITCODE -ne 0) { throw 'signtool sign failed.' }
        $stage = 'MSI Authenticode verification'
        & $signTool verify /pa $package *> $null
        if ($LASTEXITCODE -ne 0) { throw 'signtool verify /pa failed.' }
        Write-Host 'MSI signed and signtool verify /pa passed.'
    }
    else {
        Write-Host 'Windows release explicitly unsigned: no WIN_PFX secrets configured.'
    }
}
catch {
    # Do not expose exception text from certificate decoding or native commands.
    # Report known validations, which never contain credential values.
    $safeMessages = @(
        'Both WIN_PFX_BASE64 and WIN_PFX_PASSWORD must be configured, or neither.',
        'ExpectedVersion must be numeric major.minor.patch without v.',
        'ExpectedVersion exceeds MSI ProductVersion limits (255.255.65535).',
        'PackagePath must identify one existing MSI.',
        'Embedded MSI ProductVersion does not match ExpectedVersion.',
        'Signing secrets configured but signtool.exe was not found in PATH or a Windows SDK.'
    )
    if ($safeMessages -ccontains $_.Exception.Message) {
        [Console]::Error.WriteLine($_.Exception.Message)
    }
    else {
        [Console]::Error.WriteLine("Windows helper failed during $stage; credential diagnostics suppressed.")
    }
    $failed = $true
}
finally {
    if ($null -ne $tempDirectory) {
        try {
            if ($null -ne $pfxPath -and (Test-Path -LiteralPath $pfxPath)) {
                Remove-Item -LiteralPath $pfxPath -Force
            }
            Remove-Item -LiteralPath $tempDirectory -Force -Recurse
        }
        catch {
            [Console]::Error.WriteLine('Failed to remove temporary signing certificate directory.')
            $failed = $true
        }
    }
}
if ($failed) { exit 1 }
