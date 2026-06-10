import { createContext, useContext } from 'react'

export const BranchContext = createContext(null)

export default function useBranch() {
  return useContext(BranchContext)
}
