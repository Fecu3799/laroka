import './ToggleSwitch.css'

export default function ToggleSwitch({ checked = false, onChange, disabled = false, label }) {
  return (
    <label className={`toggle-switch${disabled ? ' toggle-switch--disabled' : ''}`}>
      {label && <span className="toggle-label">{label}</span>}
      <input
        type="checkbox"
        className="toggle-input"
        checked={checked}
        onChange={onChange}
        disabled={disabled}
      />
      <span className="toggle-track">
        <span className="toggle-thumb" />
      </span>
    </label>
  )
}
