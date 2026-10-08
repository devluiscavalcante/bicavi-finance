import { useState } from 'react'
import { formatMoney } from '../../format'
import type { CategoryTotal } from '../../types'

// Quantas categorias aparecem antes do "Ver todas".
const CATEGORY_PAGE = 3

// Gastos por categoria, com uma barra proporcional ao total de despesas.
// Não precisa zerar o "Ver todas" ao trocar de mês: a tela recria o bloco a cada mês.
export function CategoryBreakdown({ categories, totalExpense }: {
  categories: CategoryTotal[] // já ordenadas pelo backend, da maior para a menor
  totalExpense: number
}) {
  const [showAll, setShowAll] = useState(false)

  if (categories.length === 0) {
    return null
  }

  const visible = showAll ? categories : categories.slice(0, CATEGORY_PAGE)

  return (
    <section className="card reveal" style={{ animationDelay: '80ms' }}>
      <h2>Gastos por categoria</h2>
      <ul className="categories">
        {visible.map(c => (
          // categoryId null ("Sem categoria") aparece no máximo uma vez.
          <li key={c.categoryId ?? 'none'}>
            <div className="category-head">
              <span>{c.categoryName ?? 'Sem categoria'}</span>
              <strong>{formatMoney(c.total)}</strong>
            </div>
            <div className="bar">
              {/* Proporção só para a largura da barra; nenhum valor exibido vem daqui. */}
              <span style={{ width: `${(c.total / totalExpense) * 100}%` }} />
            </div>
          </li>
        ))}
      </ul>
      {categories.length > CATEGORY_PAGE && (
        <button type="button" className="more-btn" onClick={() => setShowAll(v => !v)}>
          {showAll ? 'Ver menos' : `Ver todas (${categories.length})`}
        </button>
      )}
    </section>
  )
}
