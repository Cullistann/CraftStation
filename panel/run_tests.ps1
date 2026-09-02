$ErrorActionPreference = "Stop"

# Change directory to the script's directory
Set-Location -Path $PSScriptRoot

# Clean and recreate out directories
if (Test-Path out) { Remove-Item -Recurse -Force out }
if (Test-Path out-test) { Remove-Item -Recurse -Force out-test }
New-Item -ItemType Directory -Force -Path out, out-test | Out-Null

Write-Host "Compiling main sources..."
# Find main sources and wrap in double quotes
$mainSources = Get-ChildItem -Path src/main/java -Filter *.java -Recurse | ForEach-Object {
    '"' + $_.FullName.Replace('\', '/') + '"'
}
$mainSources | Out-File -FilePath sources-main.txt -Encoding ascii

& "..\java\bin\javac" -cp "lib/*" -d out -sourcepath "src/main/java" --release 25 @sources-main.txt

Write-Host "Compiling test sources..."
# Find test sources and wrap in double quotes
$testSources = Get-ChildItem -Path src/test/java -Filter *.java -Recurse | ForEach-Object {
    '"' + $_.FullName.Replace('\', '/') + '"'
}
$testSources | Out-File -FilePath sources-test.txt -Encoding ascii

& "..\java\bin\javac" -cp "lib/*;out" -d out-test -sourcepath "src/test/java" --release 25 @sources-test.txt

Write-Host "Running JUnit tests..."
& "..\java\bin\java" -jar lib/junit-platform-console-standalone-1.11.0.jar --class-path "out;out-test" --scan-class-path

# Clean up temporary files
if (Test-Path sources-main.txt) { Remove-Item sources-main.txt }
if (Test-Path sources-test.txt) { Remove-Item sources-test.txt }
