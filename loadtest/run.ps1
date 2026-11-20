<#
.SYNOPSIS
  TicketRush 핫시트 부하 테스트를 한 번에 실행한다: 스택 확인 → 시드 → k6 → 정합성 검증

.EXAMPLE
  ./loadtest/run.ps1                          # 기본: 1,000명 / 100석, 라벨 baseline
  ./loadtest/run.ps1 -HotSeats 10 -Label hot10  # 10석에 1,000명 (경합 극대화)
#>
param(
    [int]$Users = 1000,
    [int]$HotSeats = 100,
    [string]$Label = 'baseline',
    [int]$StartDelay = 20
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Set-Location (Join-Path $PSScriptRoot '..')

$compose = @('compose', '-f', 'deploy/local/compose.yaml')
$psql = @('exec', '-T', 'postgres', 'psql', '-U', 'ticketrush', '-d', 'ticketrush', '-v', 'ON_ERROR_STOP=1', '-f')

function Invoke-Step([string]$Title, [scriptblock]$Body) {
    Write-Host ''
    Write-Host "==> $Title" -ForegroundColor Cyan
    & $Body
}

Invoke-Step '[1/4] 스택 기동 확인 (app healthy까지 대기)' {
    docker @compose up -d --wait
    if ($LASTEXITCODE -ne 0) { throw '스택 기동 실패: docker compose -f deploy/local/compose.yaml logs app 확인' }
}

Invoke-Step '[2/4] 시드: 모든 테이블 초기화 후 사용자 1,000명 · 좌석 100석 생성' {
    docker @compose @psql /loadtest/seed/seed.sql
    if ($LASTEXITCODE -ne 0) { throw '시드 실패' }
}

$k6Exit = 0
Invoke-Step "[3/4] k6 실행: 사용자 $Users 명 / 목표 좌석 $HotSeats 석 / 라벨 $Label" {
    docker @compose run --rm `
        -e USERS=$Users -e HOT_SEATS=$HotSeats -e RUN_LABEL=$Label -e START_DELAY=$StartDelay `
        k6 run --tag testid=$Label -o experimental-prometheus-rw /scripts/scenarios/hot-seat.js
    $script:k6Exit = $LASTEXITCODE
}

Invoke-Step '[4/4] DB 정합성 검증' {
    docker @compose @psql /loadtest/seed/verify.sql
    if ($LASTEXITCODE -ne 0) { throw '검증 SQL 실행 실패' }
}

Write-Host ''
switch ($k6Exit) {
    0  { Write-Host 'k6: 모든 threshold 통과' -ForegroundColor Green }
    99 { Write-Host 'k6: 일부 threshold 실패 (기준선 측정에서는 거짓 성공 때문에 실패하는 것이 정상)' -ForegroundColor Yellow }
    default { Write-Host "k6: 실행 오류 (exit $k6Exit)" -ForegroundColor Red }
}
Write-Host 'Grafana: http://localhost:3000 → TicketRush 개요 (시간 범위 Last 15 minutes)'
Write-Host 'JSON 결과: loadtest/results/'
