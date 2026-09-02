# GROOVE Frontend

1. `npm install` — 의존성 설치
2. `cp .env.example .env` 후 `VITE_API_BASE_URL`, `VITE_TOSS_CLIENT_KEY` 값 채우기
3. `npm run dev` — 개발 서버 실행 (`http://localhost:5173`, `/api`는 `http://localhost:8080`으로 프록시)
4. `npm run build` — 프로덕션 빌드 (`dist/`)
5. `npm run lint` / `npm run format` — ESLint 검사 / Prettier 포맷팅
