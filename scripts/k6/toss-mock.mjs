// 토스페이먼츠 목 서버. 배포 드레인 측정(deploy-drain.js)과 결제 대사(PaymentReconcileScheduler) 셧다운
// 동작 실측에서 실제 결제사 지연·재기동을 흉내내기 위해 쓴다.
// Node 기본 모듈만 쓴다(node:http). 실행: node scripts/k6/toss-mock.mjs
// 환경변수:
//   PORT(기본 18080)
//   CONFIRM_DELAY_MS(기본 0) — POST confirm 응답 전 지연
//   LOOKUP_DELAY_MS(기본 0) — GET 조회 응답 전 지연. 대사 루프가 셧다운 신호로 다음 건에 안 넘어가는지
//     (진행 중인 조회 하나만 마저 끝내고 멈추는지) 확인할 때 이 지연을 크게 잡는다.
//   LOOKUP_UNKNOWN_AS_DONE(기본 0) — "1"이면 메모리에 없는 orderId 조회도 404 대신 DONE 으로 답한다.
//     대사는 DB 에 UNKNOWN 으로 남은 결제를 조회하는데, 목 서버가 재기동돼 메모리(승인 이력)를 잃으면
//     실제 토스라면 있었을 결제가 404 로 보여 원래 시나리오(UNKNOWN → 대사 성공)를 재현할 수 없다.
//
// 응답 필드는 backend/src/main/java/com/groove/payment/client/dto/TossPaymentResponse.java 와
// TossPaymentClient(NOT_FOUND_ERROR_CODES) 가 읽는 값에 맞춘다.

import http from 'node:http';

const PORT = Number(process.env.PORT || 18080);
const CONFIRM_DELAY_MS = Number(process.env.CONFIRM_DELAY_MS || 0);
const LOOKUP_DELAY_MS = Number(process.env.LOOKUP_DELAY_MS || 0);
const LOOKUP_UNKNOWN_AS_DONE = process.env.LOOKUP_UNKNOWN_AS_DONE === '1';

// orderId(=order.orderNumber) -> 마지막 승인/취소 응답. 조회(lookup)와 취소가 이 상태를 읽는다.
const payments = new Map();

function readBody(req) {
	return new Promise((resolve, reject) => {
		let body = '';
		req.on('data', (chunk) => {
			body += chunk;
		});
		req.on('end', () => resolve(body));
		req.on('error', reject);
	});
}

function sendJson(res, status, payload) {
	const body = JSON.stringify(payload);
	res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
	res.end(body);
}

function logRequest(method, path, startedAt) {
	const elapsedMs = Date.now() - startedAt;
	console.log(`${new Date().toISOString()} ${method} ${path} (${elapsedMs}ms)`);
}

function sleep(ms) {
	return new Promise((resolve) => setTimeout(resolve, ms));
}

function tossPaymentResponse(paymentKey, orderId, amount, status, cancels) {
	return {
		paymentKey,
		orderId,
		status,
		method: '카드',
		totalAmount: amount,
		approvedAt: new Date().toISOString(),
		cancels,
	};
}

async function handleConfirm(req, res) {
	const body = JSON.parse(await readBody(req));
	const { paymentKey, orderId, amount } = body;

	if (CONFIRM_DELAY_MS > 0) {
		await sleep(CONFIRM_DELAY_MS);
	}

	const response = tossPaymentResponse(paymentKey, orderId, amount, 'DONE', []);
	payments.set(orderId, response);
	sendJson(res, 200, response);
}

async function handleLookup(res, orderId) {
	if (LOOKUP_DELAY_MS > 0) {
		await sleep(LOOKUP_DELAY_MS);
	}

	const found = payments.get(orderId);
	if (found) {
		sendJson(res, 200, found);
		return;
	}

	if (LOOKUP_UNKNOWN_AS_DONE) {
		sendJson(res, 200, tossPaymentResponse(`unknown-${orderId}`, orderId, 0, 'DONE', []));
		return;
	}

	sendJson(res, 404, { code: 'NOT_FOUND_PAYMENT', message: '결제 정보를 찾을 수 없습니다.' });
}

async function handleCancel(req, res, paymentKey) {
	const body = JSON.parse(await readBody(req));
	const orderId = [...payments.entries()].find(([, p]) => p.paymentKey === paymentKey)?.[0];
	const previous = orderId ? payments.get(orderId) : null;
	const amount = previous ? previous.totalAmount : 0;
	const canceledAt = new Date().toISOString();

	const response = tossPaymentResponse(paymentKey, orderId ?? previous?.orderId, amount, 'CANCELED', [
		{ cancelReason: body.cancelReason, canceledAt },
	]);
	if (orderId) {
		payments.set(orderId, response);
	}
	sendJson(res, 200, response);
}

const server = http.createServer((req, res) => {
	const startedAt = Date.now();
	const url = new URL(req.url, `http://localhost:${PORT}`);
	res.on('finish', () => logRequest(req.method, url.pathname, startedAt));

	if (req.method === 'POST' && url.pathname === '/v1/payments/confirm') {
		handleConfirm(req, res).catch((err) => sendJson(res, 500, { code: 'MOCK_ERROR', message: err.message }));
		return;
	}

	const lookupMatch = url.pathname.match(/^\/v1\/payments\/orders\/(.+)$/);
	if (req.method === 'GET' && lookupMatch) {
		handleLookup(res, decodeURIComponent(lookupMatch[1]))
			.catch((err) => sendJson(res, 500, { code: 'MOCK_ERROR', message: err.message }));
		return;
	}

	const cancelMatch = url.pathname.match(/^\/v1\/payments\/(.+)\/cancel$/);
	if (req.method === 'POST' && cancelMatch) {
		handleCancel(req, res, decodeURIComponent(cancelMatch[1]))
			.catch((err) => sendJson(res, 500, { code: 'MOCK_ERROR', message: err.message }));
		return;
	}

	sendJson(res, 404, { code: 'NOT_FOUND', message: `등록되지 않은 경로: ${req.method} ${url.pathname}` });
});

server.listen(PORT, () => {
	console.log(`토스 목 서버 기동: http://localhost:${PORT} `
		+ `(CONFIRM_DELAY_MS=${CONFIRM_DELAY_MS}, LOOKUP_DELAY_MS=${LOOKUP_DELAY_MS}, `
		+ `LOOKUP_UNKNOWN_AS_DONE=${LOOKUP_UNKNOWN_AS_DONE})`);
});
