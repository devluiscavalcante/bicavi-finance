import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api, ApiError } from '../api'
import { ConfirmDialog } from '../ConfirmDialog'
import { sortByName } from '../lists'
import { Check, ChevronLeft, Close, Pencil, Tag, Trash } from '../icons'
import type { CardResponse, CategoryResponse, TransactionType } from '../types'
import { CardsTab } from './cards/CardsTab'

// O que a linha de uma categoria precisa: id + nome, para renomear e excluir.
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

const TAB_LABELS: Record<Tab, string> = {
  EXPENSE: 'Despesas',
  INCOME: 'Receitas',
  CARDS: 'Cartões',
}

// Textos das abas de categoria. A de cartões tem a tela própria (CardsTab).
const CATEGORY_TEXTS: Record<TransactionType, { newTitle: string; empty: string; example: string }> = {
  EXPENSE: {
    newTitle: 'Nova categoria de despesa',
    empty: 'Nenhuma categoria de despesa ainda.', example: 'Ex.: Mercado',
  },
  INCOME: {
    newTitle: 'Nova categoria de receita',
    empty: 'Nenhuma categoria de receita ainda.', example: 'Ex.: Salário',
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

  // Categorias da aba aberta (na aba de cartões, nenhuma).
  const items = (categories ?? []).filter(c => c.type === tab)

  // As operações atualizam a lista local com a RESPOSTA do backend, sem buscar
  // tudo de novo: o servidor já devolve o item criado/renomeado.
  async function createCategory(name: string) {
    const created = await api<CategoryResponse>('/api/categories', {
      method: 'POST',
      body: JSON.stringify({ name, type: tab }),
    })
    setCategories(list => sortByName([...(list ?? []), created]))
  }

  function handleRenamed(renamed: NamedItem) {
    setCategories(list => sortByName((list ?? []).map(c => (c.id === renamed.id ? { ...c, ...renamed } : c))))
  }

  async function confirmDelete() {
    const target = toDelete!
    await api<void>(`/api/categories/${target.id}`, { method: 'DELETE' })
    setCategories(list => (list ?? []).filter(c => c.id !== target.id))
    setToDelete(null)
  }

  // Cartão criado ou editado (o painel dos cartões já falou com a API):
  // troca o que tinha o mesmo id, ou acrescenta se é novo.
  function handleCardSaved(saved: CardResponse) {
    setCards(list => sortByName([...(list ?? []).filter(c => c.id !== saved.id), saved]))
  }

  function handleCardDeleted(id: number) {
    setCards(list => (list ?? []).filter(c => c.id !== id))
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
            {TAB_LABELS[t]}
          </button>
        ))}
      </div>

      {tab === 'CARDS' ? (
        <>
          {loadError && <p className="error">{loadError}</p>}
          {/* Carregando: um retângulo com o formato de um cartão. */}
          {!cards && !loadError && (
            <div className="wallet-area">
              <div className="skeleton paycard-skeleton" aria-busy="true" />
            </div>
          )}
          {cards && <CardsTab cards={cards} onSaved={handleCardSaved} onDeleted={handleCardDeleted} />}
        </>
      ) : (
        <>
          {/* key={tab}: trocar de aba recria o formulário, limpando nome e erro digitados. */}
          <CreateItemForm key={tab} title={CATEGORY_TEXTS[tab].newTitle} placeholder={CATEGORY_TEXTS[tab].example}
                          onCreate={createCategory} />

          {loadError && <p className="error">{loadError}</p>}
          {!categories && !loadError && <div className="skeleton" style={{ height: 200, marginTop: 16 }} />}
          {categories && (
            <section className="card reveal" style={{ animationDelay: '80ms' }}>
              <h2>{TAB_LABELS[tab]}</h2>
              {items.length === 0 ? (
                <p className="muted">{CATEGORY_TEXTS[tab].empty}</p>
              ) : (
                <ul className="category-list">
                  {items.map(item => (
                    <ItemRow key={item.id} item={item} onRenamed={handleRenamed} onDelete={setToDelete} />
                  ))}
                </ul>
              )}
            </section>
          )}
        </>
      )}

      {toDelete && (
        <ConfirmDialog
          title={`Excluir "${toDelete.name}"?`}
          message={"As transações desta categoria não serão apagadas: elas ficarão como 'Sem categoria'. "
            + 'Categoria usada em mês fechado não pode ser excluída.'}
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

function ItemRow({ item, onRenamed, onDelete }: {
  item: NamedItem
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
      // PUT só aceita o nome (o tipo da categoria não muda depois de criada).
      const renamed = await api<NamedItem>(`/api/categories/${item.id}`, {
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
