import { useParams } from 'react-router-dom'

export default function OrderDetail() {
  const { id } = useParams()

  return (
    <div className="order-detail-page">
      <h2>Detalle del pedido #{id}</h2>
      <p>
        Aquí irá el detalle completo del pedido. Funcionalidad completa en Sprint 5-F.
      </p>
    </div>
  )
}
