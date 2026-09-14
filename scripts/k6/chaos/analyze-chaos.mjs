#!/usr/bin/env node
// scripts/k6/chaos/run.sh 가 남긴 raw.json(k6 --out json)과 fault.log(FAULT_START/FAULT_END)를
// 대조해서 장애 전/중/후 구간별 구매 결과 분포·지연·복구 시점을 마크다운으로 뽑는다.
// analyze-batch-interference.mjs 와 같은 방식(시나리오 로그로 시간창을 잡고 raw JSON 과 대조)이지만,
// 여기서는 run.sh 가 fault.log 에 남긴 FAULT_START/FAULT_END 두 줄만 시간창 기준으로 쓴다.
//
// 사용법: node scripts/k6/chaos/analyze-chaos.mjs <raw.json> <fault.log>

import { createReadStream, readFileSync, existsSync } from 'node:fs';
import { createInterface } from 'node:readline';

const [, , rawJsonPath, faultLogPath] = process.argv;
if (!rawJsonPath || !faultLogPath) {
  console.error('사용법: node analyze-chaos.mjs <raw.json> <fault.log>');
  process.exit(1);
}

const OUTCOMES = ['success', 'sold_out', 'already', 'not_open', 'busy', 'server_error', 'conn_error', 'other'];
const ERROR_OUTCOMES = new Set(['busy', 'server_error', 'conn_error', 'other']);
const NORMAL_OUTCOMES = new Set(['success', 'sold_out', 'already']);
const TAIL_ERROR_OUTCOMES = new Set(['busy', 'server_error', 'conn_error', 'not_open']);

function fmtKst(ms) {
  if (ms == null || Number.isNaN(ms)) {
    return '-';
  }
  const kst = new Date(ms + 9 * 3600 * 1000);
  return `${kst.toISOString().replace('Z', '')}+09:00`;
}

function fmtMs(ms) {
  if (ms == null || Number.isNaN(ms)) {
    return '-';
  }
  return `${Math.round(ms)}ms`;
}

function nearestRank(sortedAsc, p) {
  if (sortedAsc.length === 0) {
    return null;
  }
  const rank = Math.ceil((p / 100) * sortedAsc.length);
  const idx = Math.min(Math.max(rank - 1, 0), sortedAsc.length - 1);
  return sortedAsc[idx];
}

function durationStats(values) {
  if (values.length === 0) {
    return { n: 0, p50: null, p95: null, p99: null, max: null };
  }
  const sorted = [...values].sort((a, b) => a - b);
  return {
    n: sorted.length,
    p50: nearestRank(sorted, 50),
    p95: nearestRank(sorted, 95),
    p99: nearestRank(sorted, 99),
    max: sorted[sorted.length - 1],
  };
}

// --- fault.log 파싱 ---
if (!existsSync(faultLogPath)) {
  console.error(`fault.log 를 찾을 수 없다: ${faultLogPath}`);
  process.exit(1);
}
const faultLogText = readFileSync(faultLogPath, 'utf8');
const faultStartMatch = faultLogText.match(/FAULT_START\s+scenario=\S+\s+ms=(\d+)/);
const faultEndMatch = faultLogText.match(/FAULT_END\s+scenario=\S+\s+ms=(\d+)/);

if (!faultStartMatch) {
  console.error('fault.log 에서 FAULT_START 를 찾지 못했다.');
  process.exit(1);
}
const faultStartMs = Number(faultStartMatch[1]);
const faultEndMs = faultEndMatch ? Number(faultEndMatch[1]) : null;

function segmentOf(epochMs) {
  if (epochMs < faultStartMs) {
    return 'before';
  }
  if (faultEndMs != null && epochMs >= faultEndMs) {
    return 'after';
  }
  if (faultEndMs == null) {
    return 'fault';
  }
  return 'fault';
}

// --- raw.json 스트리밍 파싱 ---
if (!existsSync(rawJsonPath)) {
  console.error(`raw.json 을 찾을 수 없다: ${rawJsonPath}`);
  process.exit(1);
}

const outcomePoints = []; // { epochMs, outcome, segment }
const durationPoints = []; // { epochMs, value, segment }

async function readRawJson() {
  const rl = createInterface({
    input: createReadStream(rawJsonPath, { encoding: 'utf8' }),
    crlfDelay: Infinity,
  });

  for await (const line of rl) {
    if (!line) {
      continue;
    }
    let point;
    try {
      point = JSON.parse(line);
    } catch {
      continue;
    }
    if (point.type !== 'Point') {
      continue;
    }

    const tags = (point.data && point.data.tags) || {};
    const epochMs = Date.parse(point.data.time);
    if (Number.isNaN(epochMs)) {
      continue;
    }

    if (point.metric === 'purchase_outcome') {
      const outcome = OUTCOMES.includes(tags.outcome) ? tags.outcome : 'other';
      outcomePoints.push({ epochMs, outcome, segment: segmentOf(epochMs) });
      continue;
    }

    if (point.metric === 'http_req_duration' && tags.name === 'purchase') {
      durationPoints.push({ epochMs, value: point.data.value, segment: segmentOf(epochMs) });
    }
  }
}

await readRawJson();

// --- 2. 구간별 outcome 표 ---
function emptyCounts() {
  const c = {};
  for (const o of OUTCOMES) {
    c[o] = 0;
  }
  c.total = 0;
  return c;
}

const bySegment = { before: emptyCounts(), fault: emptyCounts(), after: emptyCounts() };
for (const p of outcomePoints) {
  bySegment[p.segment][p.outcome] += 1;
  bySegment[p.segment].total += 1;
}
const overall = emptyCounts();
for (const seg of ['before', 'fault', 'after']) {
  for (const o of OUTCOMES) {
    overall[o] += bySegment[seg][o];
  }
  overall.total += bySegment[seg].total;
}

function errorRatePct(c) {
  if (c.total === 0) {
    return null;
  }
  const errCount = c.busy + c.server_error + c.conn_error + c.other;
  return (errCount / c.total) * 100;
}

function notOpenRatePct(c) {
  if (c.total === 0) {
    return null;
  }
  return (c.not_open / c.total) * 100;
}

function fmtPct(v) {
  return v == null ? '-' : `${v.toFixed(1)}%`;
}

function fmtCount(v) {
  return v === 0 ? '0' : String(v);
}

// --- 3. 구간별 지연 ---
const durBySegment = {
  before: durationStats(durationPoints.filter((p) => p.segment === 'before').map((p) => p.value)),
  fault: durationStats(durationPoints.filter((p) => p.segment === 'fault').map((p) => p.value)),
  after: durationStats(durationPoints.filter((p) => p.segment === 'after').map((p) => p.value)),
};

// --- 4. 복구 지표 ---
function firstAfter(points, minMs, outcomeSet) {
  let best = null;
  for (const p of points) {
    if (p.epochMs < minMs) {
      continue;
    }
    if (!outcomeSet.has(p.outcome)) {
      continue;
    }
    if (best == null || p.epochMs < best) {
      best = p.epochMs;
    }
  }
  return best;
}

function lastAt(points, minMs, outcomeSet) {
  let best = null;
  for (const p of points) {
    if (p.epochMs < minMs) {
      continue;
    }
    if (!outcomeSet.has(p.outcome)) {
      continue;
    }
    if (best == null || p.epochMs > best) {
      best = p.epochMs;
    }
  }
  return best;
}

const firstNormalAfterFaultStart = firstAfter(outcomePoints, faultStartMs, NORMAL_OUTCOMES);
const firstNormalAfterFaultEnd = faultEndMs != null ? firstAfter(outcomePoints, faultEndMs, NORMAL_OUTCOMES) : null;
const lastTailErrorMs = lastAt(outcomePoints, faultStartMs, TAIL_ERROR_OUTCOMES);
const lastSuccessAfterMs = lastAt(
  outcomePoints.filter((p) => p.segment === 'after'),
  faultStartMs,
  new Set(['success']),
);

const recoveryA = firstNormalAfterFaultStart != null ? firstNormalAfterFaultStart - faultStartMs : null;
const recoveryB =
  faultEndMs != null && firstNormalAfterFaultEnd != null ? firstNormalAfterFaultEnd - faultEndMs : null;
const recoveryC = lastTailErrorMs != null ? lastTailErrorMs - faultStartMs : null;
const recoveryD = lastSuccessAfterMs != null ? lastSuccessAfterMs - faultStartMs : null;

// --- 5. 1초 단위 타임라인 ---
const timelineEndAnchor = lastTailErrorMs != null ? lastTailErrorMs + 3000 : faultStartMs + 5000;
// 버킷은 정각 초가 아니라 FAULT_START 기준으로 자른다. 정각에 맞추면 +0s 행에 장애 직전 요청이 섞인다.
const windowStartMs = faultStartMs - 3000;
const windowEndMs = faultStartMs + Math.ceil((timelineEndAnchor - faultStartMs) / 1000) * 1000;

const MAX_ROWS = 60;
const totalSeconds = Math.max(Math.round((windowEndMs - windowStartMs) / 1000), 1);
const shownSeconds = Math.min(totalSeconds, MAX_ROWS);

const timelineBuckets = new Map(); // offsetSec -> { success, code409, notOpen, busy, error }
for (let i = 0; i < shownSeconds; i++) {
  const bucketStartMs = windowStartMs + i * 1000;
  const offsetSec = Math.round((bucketStartMs - faultStartMs) / 1000);
  timelineBuckets.set(offsetSec, { success: 0, code409: 0, notOpen: 0, busy: 0, error: 0 });
}

for (const p of outcomePoints) {
  if (p.epochMs < windowStartMs || p.epochMs >= windowStartMs + shownSeconds * 1000) {
    continue;
  }
  const offsetSec = Math.floor((p.epochMs - windowStartMs) / 1000) + Math.round((windowStartMs - faultStartMs) / 1000);
  const bucket = timelineBuckets.get(offsetSec);
  if (!bucket) {
    continue;
  }
  if (p.outcome === 'success') {
    bucket.success += 1;
  } else if (p.outcome === 'sold_out' || p.outcome === 'already') {
    bucket.code409 += 1;
  } else if (p.outcome === 'not_open') {
    bucket.notOpen += 1;
  } else if (p.outcome === 'busy') {
    bucket.busy += 1;
  } else if (p.outcome === 'server_error' || p.outcome === 'conn_error') {
    bucket.error += 1;
  }
}

// --- 출력 ---
const lines = [];
lines.push('# 한정반 카오스 장애 주입 분석\n');

lines.push('## 1. 장애 창');
lines.push(`- 시작: ${fmtKst(faultStartMs)} (ms=${faultStartMs})`);
lines.push(
  faultEndMs != null
    ? `- 종료: ${fmtKst(faultEndMs)} (ms=${faultEndMs})`
    : '- 종료: 기록 없음 (fault.log 에 FAULT_END 가 없다 - fault 구간을 FAULT_START 이후 전부로 본다)',
);
lines.push(`- 길이: ${faultEndMs != null ? fmtMs(faultEndMs - faultStartMs) : '-'}`);
lines.push('');

lines.push('## 2. 구간별 outcome');
lines.push(
  '| 구간 | success | sold_out | already | not_open | busy | server_error | conn_error | other | 합계 | 오류율 | not_open율 |',
);
lines.push('|---|---|---|---|---|---|---|---|---|---|---|---|');
for (const [label, key] of [
  ['before', 'before'],
  ['fault', 'fault'],
  ['after', 'after'],
  ['전체', null],
]) {
  const c = key ? bySegment[key] : overall;
  lines.push(
    `| ${label} | ${fmtCount(c.success)} | ${fmtCount(c.sold_out)} | ${fmtCount(c.already)} | ${fmtCount(c.not_open)} | `
      + `${fmtCount(c.busy)} | ${fmtCount(c.server_error)} | ${fmtCount(c.conn_error)} | ${fmtCount(c.other)} | `
      + `${fmtCount(c.total)} | ${fmtPct(errorRatePct(c))} | ${fmtPct(notOpenRatePct(c))} |`,
  );
}
lines.push('');

lines.push('## 3. 구간별 http_req_duration{name:purchase} (ms)');
lines.push('| 구간 | n | p50 | p95 | p99 | max |');
lines.push('|---|---|---|---|---|---|');
for (const [label, key] of [
  ['before', 'before'],
  ['fault', 'fault'],
  ['after', 'after'],
]) {
  const s = durBySegment[key];
  lines.push(
    `| ${label} | ${s.n} | ${fmtMs(s.p50)} | ${fmtMs(s.p95)} | ${fmtMs(s.p99)} | ${fmtMs(s.max)} |`,
  );
}
lines.push('');

lines.push('## 4. 복구 지표');
lines.push(`- (a) FAULT_START 이후 첫 정상 응답(success/sold_out/already)까지: ${fmtMs(recoveryA)}`);
lines.push(`- (b) FAULT_END 이후 첫 정상 응답까지: ${fmtMs(recoveryB)}`);
lines.push(`- (c) 마지막 오류 응답(busy/server_error/conn_error/not_open) 시각 − FAULT_START: ${fmtMs(recoveryC)}`);
lines.push(`- (d) after 구간 마지막 success 시각 − FAULT_START (대사로 복원된 재고가 팔린 시점): ${fmtMs(recoveryD)}`);
lines.push('');

lines.push('## 5. 1초 단위 타임라인');
if (totalSeconds > MAX_ROWS) {
  lines.push(`(전체 ${totalSeconds}초 중 앞 ${MAX_ROWS}초만 표시)`);
}
lines.push('| 초(FAULT_START 기준 offset) | success | 409 | not_open | busy | error(5xx+0) |');
lines.push('|---|---|---|---|---|---|');
const offsets = [...timelineBuckets.keys()].sort((a, b) => a - b);
if (offsets.length === 0) {
  lines.push('| - | - | - | - | - | - |');
} else {
  for (const offset of offsets) {
    const b = timelineBuckets.get(offset);
    lines.push(`| ${offset >= 0 ? '+' : ''}${offset}s | ${b.success} | ${b.code409} | ${b.notOpen} | ${b.busy} | ${b.error} |`);
  }
}

console.log(lines.join('\n'));
