#!/usr/bin/env python3
# 사용법: python3 analyze-circuit-lag.py <run.sh 가 만든 chaos 결과 디렉터리>
#
# run.sh 가 남긴 fault.log(FAULT_START, THREAD_DUMP 마커)와 컨테이너별
# backend-full-<container>.log(docker logs 전체)를 읽어, 인스턴스마다 Redis 연결
# 이벤트·한정반 Redis 선점 실패 지연·서킷 상태 전이·스레드 덤프를 FAULT_START 기준
# 오프셋(초)으로 나란히 출력한다. 2대 서킷 감지 시차의 원인(호출이 어디서 얼마나
# 걸려 실패하는지, 그 순간 요청 스레드가 어디서 멈춰 있는지)을 가르기 위한 진단 도구다.

import re
import sys
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

KST = timezone(timedelta(hours=9))

BOOT_LOG_RE = re.compile(
	# Boot 3.x 콘솔 패턴은 [앱이름] [스레드] 두 묶음이 붙고, 컨테이너 시각은 UTC(Z)다.
	r'^(?P<ts>\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}\.\d{3}(?:Z|[+-]\d{2}:\d{2})?)\s+\S+\s+\d+\s+---\s+'
	r'(?:\[[^\]]*]\s+)+\S+\s*:\s*(?P<msg>.*)$'
)

RESERVE_FAIL_RE = re.compile(r'한정반 Redis 선점 실패.*elapsedMs=(\d+)')
CIRCUIT_ACCUM_RE = re.compile(r'한정반 Redis 서킷 실패 누적 (\d+)/(\d+)')
CIRCUIT_OPEN_MARK = '한정반 Redis 서킷 OPEN'
CIRCUIT_CLOSED_MARK = '한정반 Redis 서킷 CLOSED 복귀'
CONN_EVENT_KEYWORDS = ('Redis 연결 활성화', 'Redis 연결 종료', 'Redis 재연결 실패', 'Redis 재연결 시도')

FAULT_START_RE = re.compile(r'^FAULT_START scenario=\S+ ms=(?P<ms>\d+)$')
THREAD_DUMP_MARKER_RE = re.compile(r'^THREAD_DUMP container=(?P<container>\S+) offset=(?P<offset>\d+) ms=(?P<ms>\d+)$')

THREAD_HEADER_RE = re.compile(r'^"([^"]+)"')
STATE_RE = re.compile(r'java\.lang\.Thread\.State:\s+(\S+)')
LOCK_LINE_RE = re.compile(r'-\s+(waiting to lock|parking to wait for|waiting on)\s+(.*)$')
FRAME_RE = re.compile(r'^\s*at\s+(\S+)')
TARGET_PACKAGES = ('com.groove', 'io.lettuce', 'org.springframework.data.redis')


def to_offset(ts_str, fault_start_ms):
	# 오프셋이 없으면 호스트(KST) 로그로 본다.
	normalized = ts_str.replace('T', ' ').replace('Z', '+00:00')
	dt = datetime.fromisoformat(normalized)
	if dt.tzinfo is None:
		dt = dt.replace(tzinfo=KST)
	epoch_ms = dt.timestamp() * 1000
	return round((epoch_ms - fault_start_ms) / 1000, 2)


def parse_fault_log(path):
	fault_start_ms = None
	markers = defaultdict(list)
	for line in path.read_text(encoding='utf-8', errors='replace').splitlines():
		match = FAULT_START_RE.match(line)
		if match:
			fault_start_ms = int(match.group('ms'))
			continue
		match = THREAD_DUMP_MARKER_RE.match(line)
		if match:
			markers[match.group('container')].append((int(match.group('offset')), int(match.group('ms'))))
	for container in markers:
		markers[container].sort(key=lambda pair: pair[1])
	return fault_start_ms, markers


def parse_boot_lines(raw_text, fault_start_ms):
	entries = []
	for line in raw_text.splitlines():
		match = BOOT_LOG_RE.match(line)
		if not match:
			continue
		try:
			offset = to_offset(match.group('ts'), fault_start_ms)
		except ValueError:
			continue
		entries.append((offset, match.group('msg')))
	return entries


def collect_connection_events(entries):
	return [(offset, msg) for offset, msg in entries if any(k in msg for k in CONN_EVENT_KEYWORDS)]


def collect_reserve_failures(entries):
	failures = []
	for offset, msg in entries:
		match = RESERVE_FAIL_RE.search(msg)
		if match:
			failures.append((offset, int(match.group(1))))
	return failures


def bucket_failures(failures):
	buckets = {'<=50ms': 0, '51~1500ms': 0, '>1500ms': 0}
	for _, elapsed in failures:
		if elapsed <= 50:
			buckets['<=50ms'] += 1
		elif elapsed <= 1500:
			buckets['51~1500ms'] += 1
		else:
			buckets['>1500ms'] += 1
	return buckets


def collect_circuit_events(entries):
	accum = []
	open_at = None
	closed_at = []
	for offset, msg in entries:
		match = CIRCUIT_ACCUM_RE.search(msg)
		if match:
			accum.append((offset, int(match.group(1)), int(match.group(2))))
			continue
		if CIRCUIT_OPEN_MARK in msg and open_at is None:
			open_at = offset
			continue
		if CIRCUIT_CLOSED_MARK in msg:
			closed_at.append(offset)
	return accum, open_at, closed_at


def split_thread_dump_blocks(raw_text):
	lines = raw_text.splitlines()
	starts = [i for i, line in enumerate(lines) if line.startswith('Full thread dump')]
	blocks = []
	for idx, start in enumerate(starts):
		end = starts[idx + 1] if idx + 1 < len(starts) else len(lines)
		blocks.append(lines[start:end])
	return blocks


def analyze_thread_block(block_lines):
	"""블록 안의 http-nio 스레드마다 상태·첫 락 대기 줄·그 위 애플리케이션 프레임을 뽑는다."""
	thread_starts = [i for i, line in enumerate(block_lines) if THREAD_HEADER_RE.match(line)]
	results = []
	for idx, start in enumerate(thread_starts):
		end = thread_starts[idx + 1] if idx + 1 < len(thread_starts) else len(block_lines)
		header_match = THREAD_HEADER_RE.match(block_lines[start])
		name = header_match.group(1) if header_match else ''
		if 'http-nio' not in name:
			continue
		thread_lines = block_lines[start:end]
		state = None
		lock_line_idx = None
		lock_target = None
		for i, line in enumerate(thread_lines):
			state_match = STATE_RE.search(line)
			if state_match and state is None:
				state = state_match.group(1)
			lock_match = LOCK_LINE_RE.search(line)
			if lock_match and lock_line_idx is None:
				lock_line_idx = i
				lock_target = '{} {}'.format(lock_match.group(1), lock_match.group(2).strip())
		# 스택 맨 위(가장 안쪽 호출)부터 내려가며 첫 앱·Redis 프레임을 잡는다. BLOCKED 는 그 프레임이 락 줄
		# 바로 위에, parking 은 JDK park 프레임 아래(호출자 쪽)에 있어 방향을 한쪽으로 고정하면 한 경우를 놓친다.
		frame = None
		for line in thread_lines:
			frame_match = FRAME_RE.match(line)
			if frame_match and frame_match.group(1).startswith(TARGET_PACKAGES):
				frame = frame_match.group(1)
				break
		results.append({'name': name, 'state': state, 'lockTarget': lock_target, 'frame': frame})
	return results


def print_thread_dump_section(raw_text, container_markers):
	if not container_markers:
		print('  (THREAD_DUMP_OFFSETS 마커 없음)')
		return
	blocks = split_thread_dump_blocks(raw_text)
	if not blocks:
		print('  ("Full thread dump" 블록을 찾지 못함)')
		return
	if len(blocks) != len(container_markers):
		print('  경고: 마커 {}개, 덤프 블록 {}개 - 등장 순서로 짝짓는다'.format(len(container_markers), len(blocks)))
	for (offset, ms), block in zip(container_markers, blocks):
		print('  offset={}s (ms={})'.format(offset, ms))
		threads = analyze_thread_block(block)
		http_threads = [t for t in threads if t['lockTarget'] is not None]
		counter = defaultdict(int)
		for thread in http_threads:
			key = (thread['frame'] or '(com.groove/io.lettuce/redis 프레임 없음)', thread['lockTarget'])
			counter[key] += 1
		if not counter:
			print('    http-nio 스레드 중 락 대기 없음 (전체 {}개)'.format(len(threads)))
			continue
		ranked = sorted(counter.items(), key=lambda item: item[1], reverse=True)[:5]
		for rank, ((frame, target), count) in enumerate(ranked, start=1):
			print('    {}. ({}, {}) -> {}스레드'.format(rank, frame, target, count))


def analyze_container(container, log_path, fault_start_ms, markers_by_container):
	print('=== container: {} ==='.format(container))
	raw_text = log_path.read_text(encoding='utf-8', errors='replace')
	entries = parse_boot_lines(raw_text, fault_start_ms)

	print('[연결 이벤트]')
	conn_events = collect_connection_events(entries)
	if not conn_events:
		print('  (없음)')
	for offset, msg in conn_events:
		print('  +{}s {}'.format(offset, msg))

	print('[한정반 Redis 선점 실패 elapsedMs 분포]')
	failures = collect_reserve_failures(entries)
	if not failures:
		print('  (없음)')
	else:
		buckets = bucket_failures(failures)
		for label, count in buckets.items():
			print('  {}: {}건'.format(label, count))
		print('  최초 10건:')
		for offset, elapsed in failures[:10]:
			print('    +{}s elapsedMs={}'.format(offset, elapsed))

	print('[서킷 실패 누적 / 상태 전이]')
	accum, open_at, closed_at = collect_circuit_events(entries)
	if not accum and open_at is None and not closed_at:
		print('  (없음)')
	else:
		for offset, current, threshold in accum:
			print('  +{}s 실패 누적 {}/{}'.format(offset, current, threshold))
		print('  OPEN: {}'.format('+{}s'.format(open_at) if open_at is not None else '(없음)'))
		if closed_at:
			print('  CLOSED 복귀: {}'.format(', '.join('+{}s'.format(o) for o in closed_at)))
		else:
			print('  CLOSED 복귀: (없음)')

	print('[스레드 덤프]')
	print_thread_dump_section(raw_text, markers_by_container.get(container, []))
	print()


def main():
	if len(sys.argv) != 2:
		print('사용법: analyze-circuit-lag.py <chaos 결과 디렉터리>', file=sys.stderr)
		return 2

	result_dir = Path(sys.argv[1])
	fault_log_path = result_dir / 'fault.log'
	if not fault_log_path.exists():
		print('fault.log 를 찾을 수 없다: {}'.format(fault_log_path), file=sys.stderr)
		return 2

	fault_start_ms, markers_by_container = parse_fault_log(fault_log_path)
	if fault_start_ms is None:
		print('fault.log 에서 FAULT_START 를 찾지 못했다', file=sys.stderr)
		return 2

	log_paths = sorted(result_dir.glob('backend-full-*.log'))
	if not log_paths:
		print('backend-full-*.log 를 찾을 수 없다: {}'.format(result_dir), file=sys.stderr)
		return 2

	for log_path in log_paths:
		container = log_path.stem[len('backend-full-'):]
		analyze_container(container, log_path, fault_start_ms, markers_by_container)

	return 0


if __name__ == '__main__':
	sys.exit(main())
