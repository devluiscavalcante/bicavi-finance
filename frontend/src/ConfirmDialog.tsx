import { useEffect, useRef, useState } from 'react'
import { ApiError } from './api'

// Caixa de confirmação para ações destrutivas (excluir).
// Mesmo padrão do TransactionSheet: <dialog> nativo com showModal(), e o pai
// só renderiza enquanto ela está aberta.
export function ConfirmDialog({ title, message, confirmLabel, onConfirm, onCancel }: {
  title: string
  message: string
  confirmLabel: string
  // Se a ação falhar, a caixa continua aberta mostrando o erro.
  onConfirm: () => Promise<void>
  onCancel: () => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) {
      dialog.showModal()
    }
  }, [])

  async function handleConfirm() {
    setBusy(true)
    setError(null)
    try {
      await onConfirm()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Não foi possível conectar ao servidor')
      setBusy(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className="confirm"
      aria-labelledby="confirm-title"
      aria-describedby="confirm-message"
      onClose={onCancel}
      onClick={e => {
        if (e.target === e.currentTarget && !busy) dialogRef.current?.close()
      }}
    >
      <div className="confirm-body">
        <h2 id="confirm-title">{title}</h2>
        <p id="confirm-message">{message}</p>
        {error && <p className="error">{error}</p>}
        <div className="confirm-actions">
          {/* autoFocus no "Cancelar": Enter por engano não apaga nada. */}
          <button type="button" className="btn-secondary" autoFocus disabled={busy}
                  onClick={() => dialogRef.current?.close()}>
            Cancelar
          </button>
          <button type="button" className="btn-danger" disabled={busy} onClick={handleConfirm}>
            {busy ? 'Aguarde...' : confirmLabel}
          </button>
        </div>
      </div>
    </dialog>
  )
}
