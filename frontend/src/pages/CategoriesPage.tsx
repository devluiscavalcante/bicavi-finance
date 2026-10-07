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

export function CategoriesPage() {
  const [categories, setCategories] = useState<CategoryResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [toDelete, setToDelete] = useState<CategoryResponse | null>(null)

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

  return (
    <main>
      <header className="page-header">
        <Link to="/" className="icon-btn" aria-label="Voltar para o mês">
          <ChevronLeft />
        </Link>
        <h1>Categorias</h1>
      </header>

      <CreateCategoryForm onCreated={handleCreated} />

      {loadError && <p className="error">{loadError}</p>}
      {!categories && !loadError && <div className="skeleton" style={{ height: 200, marginTop: 16 }} />}
      {categories && (
        <>
          <CategoryGroup title="Despesas" type="EXPENSE" categories={categories} delay={80}
                         onRenamed={handleRenamed} onDelete={setToDelete} />
          <CategoryGroup title="Receitas" type="INCOME" categories={categories} delay={160}
                         onRenamed={handleRenamed} onDelete={setToDelete} />
        </>
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

function CreateCategoryForm({ onCreated }: { onCreated: (c: CategoryResponse) => void }) {
  const [name, setName] = useState('')
  const [type, setType] = useState<TransactionType>('EXPENSE')
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
      setName('') // mantém o tipo: facilita criar várias do mesmo tipo seguidas
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="card reveal">
      <h2>Nova categoria</h2>
      <form onSubmit={handleSubmit}>
        <div className="segmented" role="radiogroup" aria-label="Tipo">
          {(['EXPENSE', 'INCOME'] as const).map(t => (
            <button key={t} type="button" role="radio" aria-checked={type === t}
                    className={type === t ? 'active' : ''} onClick={() => setType(t)}>
              {t === 'EXPENSE' ? 'Despesa' : 'Receita'}
            </button>
          ))}
        </div>
        <div className="inline-form">
          <input value={name} onChange={e => setName(e.target.value)} maxLength={50}
                 placeholder={type === 'EXPENSE' ? 'Ex.: Mercado' : 'Ex.: Salário'}
                 aria-label="Nome da categoria" />
          <button type="submit" className="btn-primary" disabled={saving || name.trim() === ''}>
            {saving ? '...' : 'Adicionar'}
          </button>
        </div>
        {error && <p className="error">{error}</p>}
      </form>
    </section>
  )
}

function CategoryGroup({ title, type, categories, delay, onRenamed, onDelete }: {
  title: string
  type: TransactionType
  categories: CategoryResponse[]
  delay: number
  onRenamed: (c: CategoryResponse) => void
  onDelete: (c: CategoryResponse) => void
}) {
  const items = categories.filter(c => c.type === type)
  return (
    <section className="card reveal" style={{ animationDelay: `${delay}ms` }}>
      <h2>{title}</h2>
      {items.length === 0 ? (
        <p className="muted">Nenhuma categoria de {title.toLowerCase()} ainda.</p>
      ) : (
        <ul className="category-list">
          {items.map(c => (
            <CategoryRow key={c.id} category={c} onRenamed={onRenamed} onDelete={onDelete} />
          ))}
        </ul>
      )}
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
