import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/Layout'
import ProtectedRoute from './components/ProtectedRoute'
import BranchProvider from './components/BranchProvider'
import Login from './pages/Login'
import BranchSelect from './pages/BranchSelect'
import Orders from './pages/Orders'
import OrderDetail from './pages/OrderDetail'
import Summary from './pages/Summary'
import './App.css'

function App() {
  return (
    <BrowserRouter>
      <BranchProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route
            path="/branch-select"
            element={
              <ProtectedRoute>
                <BranchSelect />
              </ProtectedRoute>
            }
          />
          <Route
            element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }
          >
            <Route path="/summary" element={<Summary />} />
            <Route path="/orders" element={<Orders />} />
            <Route path="/orders/:id" element={<OrderDetail />} />
          </Route>
        </Routes>
      </BranchProvider>
    </BrowserRouter>
  )
}

export default App
