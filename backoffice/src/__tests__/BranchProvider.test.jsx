import { render, screen } from '@testing-library/react'
import BranchProvider from '../components/BranchProvider'
import useBranch from '../hooks/useBranch'
import useAuth from '../hooks/useAuth'

vi.mock('../hooks/useAuth', () => ({ default: vi.fn() }))

function Probe() {
  const { activeBranchId } = useBranch()
  return <div data-testid="active-branch">{String(activeBranchId)}</div>
}

function renderWithAuth(authValue) {
  useAuth.mockReturnValue(authValue)
  render(
    <BranchProvider>
      <Probe />
    </BranchProvider>
  )
}

beforeEach(() => {
  sessionStorage.clear()
  vi.clearAllMocks()
})

describe('BranchProvider · auto-resolución de activeBranchId desde el token', () => {
  test('MANAGER con branchId en el token auto-setea activeBranchId al montar', () => {
    renderWithAuth({ role: 'MANAGER', branchId: 1, branchName: 'Puerto Madryn' })
    expect(screen.getByTestId('active-branch')).toHaveTextContent('1')
  })

  test('STAFF con branchId en el token auto-setea activeBranchId al montar', () => {
    renderWithAuth({ role: 'STAFF', branchId: 2, branchName: 'Playa Unión' })
    expect(screen.getByTestId('active-branch')).toHaveTextContent('2')
  })

  test('ADMIN no auto-setea activeBranchId (debe elegir sucursal)', () => {
    renderWithAuth({ role: 'ADMIN', branchId: null, branchName: null })
    expect(screen.getByTestId('active-branch')).toHaveTextContent('null')
  })
})
