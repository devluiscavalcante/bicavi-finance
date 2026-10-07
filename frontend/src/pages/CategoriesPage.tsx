import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { api, ApiError } from '../api'
import { ConfirmDialog } from '../ConfirmDialog'
import { Check, ChevronLeft, Close, Pencil, Tag, Trash } from '../icons'
import type { CategoryResponse, TransactionType } from '../types'

// Ordem alfabética respeitando acentos ("Água" junto do "A", não depois do "Z").
function sortByName(list: CategoryResponse[]) {
  return [...list].sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'))
}

function errorMessage(e: unknown) {
  return e instanceof ApiError ? e.message : 'Não foi possível conectar ao servidor'
}

const LABELS: Record<TransactionType, { tab: string; singular: string; example: string }> = {
  EXPENSE: { tab: 'Despesas', singular: 'despesa', example: 'Ex.: Mercado' },
  INCOME: { tab: 'Receitas', singular: 'receita', example: 'Ex.: Salário' },
}

export function CategoriesPage() {
  const [categories, setCategories] = useState<CategoryResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [toDelete, setToDelete] = useState<CategoryResponse | null>(null)
  // A aba escolhida filtra a lista E define o tipo da categoria criada.
  const [type, setType] = useState<TransactionType>('EXPENSE')

  useEffect(() => {
    api<CategoryResponse[]>('/api/categories')
      .then(list => setCategories(sortByName(list)))
      .catch(e => setLoadError(errorMessage(e)))
  }, [])

  // As três operações atualizam a lista local com a RESPOSTA do backend,
  // sem buscar tudo de novo: o servidor já devolve a categoria criada/renomeada.
  function handleCreated(created: CategoryResponse) {
    setCategories(list => sortByName([...(list ?? []), created]))
  }

  function handleRenamed(renamed: CategoryResponse) {
    setCategories(list => sortByName((list ?? []).map(c => (c.id === renamed.id ? renamed : c))))
  }

  async function confirmDelete() {
    const target = toDelete!
    await api<void>(`/api/categories/${target.id}`, { method: 'DELETE' })
    setCategories(list => (list ?? []).filter(c => c.id !== target.id))
    setToDelete(null)
  }

  const items = (categories ?? []).filter(c => c.type === type)

  return (
    <main>
      <header className="page-header">
        <Link to="/" className="icon-btn" aria-label="Voltar para o mês">
          <ChevronLeft />
        </Link>
        <h1>Categorias</h1>
      </header>

      <div className="segmented reveal" role="tablist" aria-label="Tipo de categoria">
        {(['EXPENSE', 'INCOME'] as const).map(t => (
          <button key={t} type="button" role="tab" aria-selected={type === t}
                  className={type === t ? 'active' : ''} onClick={() => setType(t)}>
            {LABELS[t].tab}
          </button>
        ))}
      </div>

      {/* key={type}: trocar de aba recria o formulário, limpando nome e erro digitados. */}
      <CreateCategoryForm key={type} type={type} onCreated={handleCreated} />

      {loadError && <p className="error">{loadError}</p>}
      {!categories && !loadError && <div className="skeleton" style={{ height: 200, marginTop: 16 }} />}
      {categories && (
        <section className="card reveal" style={{ animationDelay: '80ms' }}>
          <h2>{LABELS[type].tab}</h2>
          {items.length === 0 ? (
            <p className="muted">Nenhuma categoria de {LABELS[type].singular} ainda.</p>
          ) : (
            <ul className="category-list">
              {items.map(c => (
                <CategoryRow key={c.id} category={c} onRenamed={handleRenamed} onDelete={setToDelete} />
              ))}
            </ul>
          )}
        </section>
      )}

      {toDelete && (
        <ConfirmDialog
          title={`Excluir "${toDelete.name}"?`}
          message="As transações desta categoria não serão apagadas: elas ficarão como 'Sem categoria'."
          confirmLabel="Excluir"
          onConfirm={confirmDelete}
          onCancel={() => setToDelete(null)}
        />
      )}
    </main>
  )
}

function CreateCategoryForm({ type, onCreated }: {
  type: TransactionType
  onCreated: (c: CategoryResponse) => void
}) {
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (name.trim() === '') return
    setError(null)
    setSaving(true)
    try {
      const created = await api<CategoryResponse>('/api/categories', {
        method: 'POST',
        body: JSON.stringify({ name: name.trim(), type }),
      })
      onCreated(created)
      setName('')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="card reveal" style={{ animationDelay: '40ms' }}>
      <h2>Nova categoria de {LABELS[type].singular}</h2>
      <form onSubmit={handleSubmit}>
        <div className="inline-form">
          <input value={name} onChange={e => setName(e.target.value)} maxLength={50}
                 placeholder={LABELS[type].example} aria-label="Nome da categoria" />
          <button type="submit" className="btn-primary" disabled={saving || name.trim() === ''}>
            {saving ? '...' : 'Adicionar'}
          </button>
        </div>
        {error && <p className="error">{error}</p>}
      </form>
    </section>
  )
}

function CategoryRow({ category, onRenamed, onDelete }: {
  category: CategoryResponse
  onRenamed: (c: CategoryResponse) => void
  onDelete: (c: CategoryResponse) => void
}) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(category.name)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  function startEditing() {
    setName(category.name)
    setError(null)
    setEditing(true)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const trimmed = name.trim()
    if (trimmed === '' || trimmed === category.name) {
      setEditing(false)
      return
    }
    setSaving(true)
    setError(null)
    try {
      // PUT só aceita o nome: o tipo não muda depois de criado (regra do backend).
      const renamed = await api<CategoryResponse>(`/api/categories/${category.id}`, {
        method: 'PUT',
        body: JSON.stringify({ name: trimmed }),
      })
      onRenamed(renamed)
      setEditing(false)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  if (editing) {
    return (
      <li className="category-row">
        <form className="inline-form" onSubmit={handleSubmit}>
          <input value={name} onChange={e => setName(e.target.value)} maxLength={50} autoFocus
                 aria-label="Novo nome"
                 onKeyDown={e => {
                   if (e.key === 'Escape') setEditing(false)
                 }} />
          <button type="submit" className="icon-btn" aria-label="Salvar" disabled={saving}>
            <Check />
          </button>
          <button type="button" className="icon-btn" aria-label="Cancelar" onClick={() => setEditing(false)}>
            <Close />
          </button>
        </form>
        {error && <p className="field-error">{error}</p>}
      </li>
    )
  }

  return (
    <li className="category-row">
      <div className="category-name">
        <span className="tx-icon"><Tag /></span>
        <span>{category.name}</span>
      </div>
      <div className="row-actions">
        <button className="icon-btn ghost" aria-label={`Renomear ${category.name}`} onClick={startEditing}>
          <Pencil />
        </button>
        <button className="icon-btn ghost danger" aria-label={`Excluir ${category.name}`}
                onClick={() => onDelete(category)}>
          <Trash />
        </button>
      </div>
    </li>
  )
}
