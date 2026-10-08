import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api, ApiError } from '../api'
import { ConfirmDialog } from '../ConfirmDialog'
import { sortByName } from '../lists'
import { Check, ChevronLeft, Close, CreditCard, Pencil, Tag, Trash } from '../icons'
import type { CardResponse, CategoryResponse, TransactionType } from '../types'

// Categoria e cartão têm o mesmo formato na tela: id + nome, renomear e excluir.
interface NamedItem {
  id: number
  name: string
}

function errorMessage(e: unknown) {
  return e instanceof ApiError ? e.message : 'Não foi possível conectar ao servidor'
}

// Abas: os dois tipos de categoria e os cartões.
type Tab = TransactionType | 'CARDS'

const TABS: Tab[] = ['EXPENSE', 'INCOME', 'CARDS']

const LABELS: Record<Tab, { tab: string; newTitle: string; empty: string; example: string }> = {
  EXPENSE: {
    tab: 'Despesas', newTitle: 'Nova categoria de despesa',
    empty: 'Nenhuma categoria de despesa ainda.', example: 'Ex.: Mercado',
  },
  INCOME: {
    tab: 'Receitas', newTitle: 'Nova categoria de receita',
    empty: 'Nenhuma categoria de receita ainda.', example: 'Ex.: Salário',
  },
  CARDS: {
    tab: 'Cartões', newTitle: 'Novo cartão de crédito',
    empty: 'Nenhum cartão cadastrado ainda.', example: 'Ex.: Banco do Brasil',
  },
}

export function CategoriesPage() {
  // A aba fica na URL (/categorias?aba=cartoes): o formulário de transação
  // consegue mandar a pessoa direto para os cartões.
  const [searchParams, setSearchParams] = useSearchParams()
  const tab: Tab = searchParams.get('aba') === 'cartoes' ? 'CARDS'
    : searchParams.get('aba') === 'receitas' ? 'INCOME' : 'EXPENSE'

  const [categories, setCategories] = useState<CategoryResponse[] | null>(null)
  const [cards, setCards] = useState<CardResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [toDelete, setToDelete] = useState<NamedItem | null>(null)

  useEffect(() => {
    Promise.all([api<CategoryResponse[]>('/api/categories'), api<CardResponse[]>('/api/cards')])
      .then(([categoryList, cardList]) => {
        setCategories(sortByName(categoryList))
        setCards(sortByName(cardList))
      })
      .catch(e => setLoadError(errorMessage(e)))
  }, [])

  function changeTab(next: Tab) {
    const aba = next === 'CARDS' ? 'cartoes' : next === 'INCOME' ? 'receitas' : null
    // replace: trocar de aba não empilha entradas no "voltar" do navegador.
    setSearchParams(aba ? { aba } : {}, { replace: true })
  }

  const isCards = tab === 'CARDS'
  const path = isCards ? '/api/cards' : '/api/categories'
  const loaded = isCards ? cards : categories
  const items: NamedItem[] = isCards ? (cards ?? []) : (categories ?? []).filter(c => c.type === tab)

  // As operações atualizam a lista local com a RESPOSTA do backend, sem buscar
  // tudo de novo: o servidor já devolve o item criado/renomeado.
  async function create(name: string) {
    if (isCards) {
      const created = await api<CardResponse>('/api/cards', { method: 'POST', body: JSON.stringify({ name }) })
      setCards(list => sortByName([...(list ?? []), created]))
    } else {
      const created = await api<CategoryResponse>('/api/categories', {
        method: 'POST',
        body: JSON.stringify({ name, type: tab }),
      })
      setCategories(list => sortByName([...(list ?? []), created]))
    }
  }

  function handleRenamed(renamed: NamedItem) {
    if (isCards) {
      setCards(list => sortByName((list ?? []).map(c => (c.id === renamed.id ? { ...c, ...renamed } : c))))
    } else {
      setCategories(list => sortByName((list ?? []).map(c => (c.id === renamed.id ? { ...c, ...renamed } : c))))
    }
  }

  async function confirmDelete() {
    const target = toDelete!
    await api<void>(`${path}/${target.id}`, { method: 'DELETE' })
    if (isCards) {
      setCards(list => (list ?? []).filter(c => c.id !== target.id))
    } else {
      setCategories(list => (list ?? []).filter(c => c.id !== target.id))
    }
    setToDelete(null)
  }

  return (
    <main>
      <header className="page-header">
        <Link to="/" className="icon-btn" aria-label="Voltar para o mês">
          <ChevronLeft />
        </Link>
        <h1>Categorias e cartões</h1>
      </header>

      <div className="segmented reveal" role="tablist" aria-label="O que gerenciar">
        {TABS.map(t => (
          <button key={t} type="button" role="tab" aria-selected={tab === t}
                  className={tab === t ? 'active' : ''} onClick={() => changeTab(t)}>
            {LABELS[t].tab}
          </button>
        ))}
      </div>

      {/* key={tab}: trocar de aba recria o formulário, limpando nome e erro digitados. */}
      <CreateItemForm key={tab} title={LABELS[tab].newTitle} placeholder={LABELS[tab].example}
                      onCreate={create} />

      {loadError && <p className="error">{loadError}</p>}
      {!loaded && !loadError && <div className="skeleton" style={{ height: 200, marginTop: 16 }} />}
      {loaded && (
        <section className="card reveal" style={{ animationDelay: '80ms' }}>
          <h2>{LABELS[tab].tab}</h2>
          {isCards && (
            <p className="muted">
              O cartão é escolhido nas compras no crédito. A categoria continua dizendo o tipo do gasto.
            </p>
          )}
          {items.length === 0 ? (
            <p className="muted">{LABELS[tab].empty}</p>
          ) : (
            <ul className="category-list">
              {items.map(item => (
                <ItemRow key={item.id} item={item} path={path} icon={isCards ? <CreditCard /> : <Tag />}
                         onRenamed={handleRenamed} onDelete={setToDelete} />
              ))}
            </ul>
          )}
        </section>
      )}

      {toDelete && (
        <ConfirmDialog
          title={`Excluir "${toDelete.name}"?`}
          message={isCards
            ? 'As compras deste cartão não serão apagadas: elas ficarão sem cartão.'
            : "As transações desta categoria não serão apagadas: elas ficarão como 'Sem categoria'."}
          confirmLabel="Excluir"
          onConfirm={confirmDelete}
          onCancel={() => setToDelete(null)}
        />
      )}
    </main>
  )
}

function CreateItemForm({ title, placeholder, onCreate }: {
  title: string
  placeholder: string
  onCreate: (name: string) => Promise<void>
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
      await onCreate(name.trim())
      setName('')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="card reveal" style={{ animationDelay: '40ms' }}>
      <h2>{title}</h2>
      <form onSubmit={handleSubmit}>
        <div className="inline-form">
          <input value={name} onChange={e => setName(e.target.value)} maxLength={50}
                 placeholder={placeholder} aria-label="Nome" />
          <button type="submit" className="btn-primary" disabled={saving || name.trim() === ''}>
            {saving ? '...' : 'Adicionar'}
          </button>
        </div>
        {error && <p className="error">{error}</p>}
      </form>
    </section>
  )
}

function ItemRow({ item, path, icon, onRenamed, onDelete }: {
  item: NamedItem
  path: string // '/api/categories' ou '/api/cards'
  icon: ReactNode
  onRenamed: (item: NamedItem) => void
  onDelete: (item: NamedItem) => void
}) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(item.name)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  function startEditing() {
    setName(item.name)
    setError(null)
    setEditing(true)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const trimmed = name.trim()
    if (trimmed === '' || trimmed === item.name) {
      setEditing(false)
      return
    }
    setSaving(true)
    setError(null)
    try {
      // PUT só aceita o nome (de uma categoria, o tipo não muda depois de criado).
      const renamed = await api<NamedItem>(`${path}/${item.id}`, {
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
        <span className="tx-icon">{icon}</span>
        <span>{item.name}</span>
      </div>
      <div className="row-actions">
        <button className="icon-btn ghost" aria-label={`Renomear ${item.name}`} onClick={startEditing}>
          <Pencil />
        </button>
        <button className="icon-btn ghost danger" aria-label={`Excluir ${item.name}`}
                onClick={() => onDelete(item)}>
          <Trash />
        </button>
      </div>
    </li>
  )
}
