#!/usr/bin/env node
// batch-interference.js 실행 결과를 사후 분석한다.
//
// batch-interference.js 는 요청 시점에 "배치가 지금 돌고 있는지" 알 방법이 없다(k6 VU 는 서로 메모리를
// 공유하지 않는다). 그래서 trigger 시나리오가 자신의 시작/종료 시각을 콘솔 로그(AGG_BATCH_INTERVAL)에
// 남기고, baseline 요청의 정확한 타임스탬프는 k6 raw JSON 출력(--out json)에서 가져온 뒤, 이 스크립트가
// 두 소스를 시각으로 대조해서 각 baseline 요청이 "배치가 실제로 도는 순간"이었는지를 사후에 복원한다.
//
// 사용법:
//   node scripts/k6/analyze-batch-interference.mjs <raw-json-output> <k6-stdout-log>
//
// <raw-json-output> : k6 run --out json=<file> 로 남긴 NDJSON
// <k6-stdout-log>   : k6 콘솔 출력을 리다이렉트한 로그 파일(TEST_START_MS/AGG_BATCH_INTERVAL 파싱용)

import { readFileSync } from 'node:fs';

const [, , rawJsonPath, logPath] = process.argv;
if (!rawJsonPath || !logPath) {
  console.error('사용법: node analyze-batch-interference.mjs <raw-json-output> <k6-stdout-log>');
  process.exit(1);
}

const TRIGGER_START_MS = Number(process.env.TRIGGER_START_MS || 120000); // 기본 2분, 스크립트의 TRIGGER_START 와 맞춘다

function percentile(sortedAsc, p) {
  if (sortedAsc.length === 0) {
    return NaN;
  }
  const idx = (p / 100) * (sortedAsc.length - 1);
  const lower = Math.floor(idx);
  const upper = Math.ceil(idx);
  if (lower === upper) {
    return sortedAsc[lower];
  }
  const weight = idx - lower;
  return sortedAsc[lower] * (1 - weight) + sortedAsc[upper] * weight;
}

function summarize(values) {
  if (values.length === 0) {
    return { count: 0, avg: NaN, p50: NaN, p95: NaN, p99: NaN, max: NaN };
  }
  const sorted = [...values].sort((a, b) => a - b);
  const sum = sorted.reduce((acc, v) => acc + v, 0);
  return {
    count: sorted.length,
    avg: sum / sorted.length,
    p50: percentile(sorted, 50),
    p95: percentile(sorted, 95),
    p99: percentile(sorted, 99),
    max: sorted[sorted.length - 1],
  };
}

// --- 로그 파일 파싱: TEST_START_MS, AGG_BATCH_INTERVAL, AGG_BATCH_LOOP_DONE ---
const logText = readFileSync(logPath, 'utf8');

const testStartMatch = logText.match(/TEST_START_MS=(\d+)/);
if (!testStartMatch) {
  console.error('로그에서 TEST_START_MS 를 찾지 못했다. batch-interference.js 의 setup() 출력이 로그에 있는지 확인해라.');
  process.exit(1);
}
const testStartMs = Number(testStartMatch[1]);

const intervalRegex = /AGG_BATCH_INTERVAL seq=(\d+) startMs=(\d+) endMs=(\d+) durationMs=([\d.]+) status=(\d+)/g;
const intervals = [];
let match;
while ((match = intervalRegex.exec(logText)) !== null) {
  intervals.push({
    seq: Number(match[1]),
    startMs: Number(match[2]),
    endMs: Number(match[3]),
    durationMs: Number(match[4]),
    status: Number(match[5]),
  });
}
if (intervals.length === 0) {
  console.error('로그에서 AGG_BATCH_INTERVAL 을 하나도 찾지 못했다. trigger 시나리오가 실행됐는지 확인해라.');
  process.exit(1);
}

const loopDoneMatch = logText.match(/AGG_BATCH_LOOP_DONE totalCalls=(\d+)/);
const totalCalls = loopDoneMatch ? Number(loopDoneMatch[1]) : intervals.length;

const statusCounts = intervals.reduce((acc, iv) => {
  acc[iv.status] = (acc[iv.status] || 0) + 1;
  return acc;
}, {});

// --- raw JSON 파싱: metric=http_req_duration, tags.name=baseline-products 포인트만 ---
const rawLines = readFileSync(rawJsonPath, 'utf8').split('\n').filter(Boolean);

const pretestDurations = [];
const duringBatchDurations = [];
const idleDurations = []; // posttest 이지만 기록된 배치 구간 밖(트리거 호출 사이 틈)

let minTime = Infinity;
let maxTime = -Infinity;

for (const line of rawLines) {
  let point;
  try {
    point = JSON.parse(line);
  } catch {
    continue;
  }
  if (point.type !== 'Point' || point.metric !== 'http_req_duration') {
    continue;
  }
  const tags = point.data.tags || {};
  if (tags.name !== 'baseline-products') {
    continue;
  }

  const epochMs = Date.parse(point.data.time);
  const value = point.data.value;
  minTime = Math.min(minTime, epochMs);
  maxTime = Math.max(maxTime, epochMs);

  if (epochMs < testStartMs + TRIGGER_START_MS) {
    pretestDurations.push(value);
    continue;
  }

  const inBatch = intervals.some((iv) => epochMs >= iv.startMs && epochMs <= iv.endMs);
  if (inBatch) {
    duringBatchDurations.push(value);
  } else {
    idleDurations.push(value);
  }
}

if (pretestDurations.length === 0 && duringBatchDurations.length === 0) {
  console.error('raw JSON 에서 baseline-products 포인트를 찾지 못했다. --out json 경로가 맞는지 확인해라.');
  process.exit(1);
}

// --- 배치 구간이 posttest 구간을 실제로 얼마나 채웠는지 ---
const posttestWindowStart = testStartMs + TRIGGER_START_MS;
const posttestWindowEnd = maxTime; // baseline 트래픽이 실제로 관측된 마지막 시각까지
const posttestWindowMs = Math.max(posttestWindowEnd - posttestWindowStart, 0);

// interval 들을 병합해서 순수 커버리지(겹침 없음, 이 시나리오는 순차라 겹치지 않지만 방어적으로 병합)
const sortedIntervals = [...intervals].sort((a, b) => a.startMs - b.startMs);
const merged = [];
for (const iv of sortedIntervals) {
  const last = merged[merged.length - 1];
  if (last && iv.startMs <= last.end + 1) {
    last.end = Math.max(last.end, iv.endMs);
  } else {
    merged.push({ start: iv.startMs, end: iv.endMs });
  }
}
const coveredMs = merged.reduce((acc, iv) => acc + (iv.end - iv.start), 0);
const coverageRatio = posttestWindowMs > 0 ? (coveredMs / posttestWindowMs) * 100 : 0;

const gaps = [];
for (let i = 1; i < sortedIntervals.length; i++) {
  gaps.push(sortedIntervals[i].startMs - sortedIntervals[i - 1].endMs);
}
const maxGapMs = gaps.length > 0 ? Math.max(...gaps) : 0;

// --- 출력 ---
const pretest = summarize(pretestDurations);
const duringBatch = summarize(duringBatchDurations);
const idle = summarize(idleDurations);

const result = {
  testStartMs,
  triggerStartMs: TRIGGER_START_MS,
  posttestWindowMs,
  batchCalls: totalCalls,
  batchStatusCounts: statusCounts,
  batchCoverage: {
    coveredMs: Math.round(coveredMs),
    posttestWindowMs: Math.round(posttestWindowMs),
    coverageRatioPct: Number(coverageRatio.toFixed(2)),
    maxGapMs: Math.round(maxGapMs),
    intervalCount: intervals.length,
    mergedIntervalCount: merged.length,
  },
  pretest,
  duringBatch,
  posttestIdle: idle,
};

console.log(JSON.stringify(result, null, 2));

console.log('\n--- 사람이 읽는 요약 ---');
console.log(`배치 호출 횟수: ${totalCalls}회 (status 분포: ${JSON.stringify(statusCounts)})`);
console.log(`posttest 구간 길이: ${(posttestWindowMs / 1000).toFixed(1)}s, 배치가 실제로 돈 시간(병합): `
    + `${(coveredMs / 1000).toFixed(1)}s (커버리지 ${coverageRatio.toFixed(1)}%, 최대 공백 ${maxGapMs}ms)`);
console.log(`pretest(평시)      n=${pretest.count} p95=${pretest.p95?.toFixed(3)}ms avg=${pretest.avg?.toFixed(3)}ms `
    + `max=${pretest.max?.toFixed(3)}ms`);
console.log(`duringBatch(배치중) n=${duringBatch.count} p95=${duringBatch.p95?.toFixed(3)}ms `
    + `avg=${duringBatch.avg?.toFixed(3)}ms max=${duringBatch.max?.toFixed(3)}ms`);
console.log(`posttestIdle(공백) n=${idle.count} p95=${idle.p95?.toFixed(3)}ms avg=${idle.avg?.toFixed(3)}ms`);

if (pretest.p95 && duringBatch.p95) {
  const deltaPct = ((duringBatch.p95 - pretest.p95) / pretest.p95) * 100;
  console.log(`\nduringBatch p95 vs pretest p95 변화율: ${deltaPct >= 0 ? '+' : ''}${deltaPct.toFixed(2)}%`);
}
