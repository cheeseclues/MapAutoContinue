# Download the official Gradle 8.11.1 wrapper JAR and verify against Gradle's published checksum.
# This only prepares build tooling. It does not run any application code.
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$dest = Join-Path $project 'gradle\wrapper\gradle-wrapper.jar'
$expectedSha256 = '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046'
$urls = @(
  'https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar',
  'https://services.gradle.org/distributions/gradle-8.11.1-wrapper.jar'
)
foreach ($url in $urls) {
    try {
        Write-Host "Trying official Gradle source: $url"
        Invoke-WebRequest -Uri $url -OutFile $dest
        $actual = (Get-FileHash -Path $dest -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -eq $expectedSha256) {
            Write-Host 'Verified Gradle Wrapper JAR checksum. Ready for Android Studio.' -ForegroundColor Green
            exit 0
        }
        Write-Warning "Downloaded content had unexpected SHA-256: $actual"
    } catch {
        Write-Warning "Download failed: $($_.Exception.Message)"
    }
}
if (Test-Path $dest) { Remove-Item -Path $dest }
throw 'Unable to obtain and verify the official Gradle 8.11.1 Wrapper JAR.'
