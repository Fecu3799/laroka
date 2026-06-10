import { useContext } from 'react'
import { BranchContext } from '../components/BranchProvider'

export default function useBranch() {
  return useContext(BranchContext)
}
