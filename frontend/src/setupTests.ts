import '@testing-library/jest-dom/vitest';

import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// globals 를 껐으므로 RTL 의 자동 cleanup 이 걸리지 않는다.
afterEach(cleanup);
