import { vi } from 'vitest'
vi.stubEnv('VITE_API_URL', 'http://localhost:8080')

import '@testing-library/jest-dom'
