import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  addMonths, currentMonth, formatDay, formatDayHeading, formatMoney, installmentPreview, isValidMonth, monthOf,
  parseAmount, todayIso, transactionTitle,
} from './format'

// O Intl separa "R$" do número com um espaço INQUEBRÁVEL (\u00a0). Trocamos
// por espaço comum para os textos esperados ficarem legíveis.
const plain = (text: string) => text.replace(/\u00a0/g, ' ')

afterEach(() => {
  vi.useRealTimers()
})

describe('parseAmount', () => {
  it('aceita vírgula ou ponto como separador decimal', () => {
    expect(parseAmount('30')).toBe(30)
    expect(parseAmount('30,5')).toBe(30.5)
    expect(parseAmount('30.50')).toBe(30.5)
    expect(parseAmount('  12,34  ')).toBe(12.34)
  })

  it('aceita até 10 dígitos inteiros, o mesmo @Digits(integer = 10) do backend', () => {
    expect(parseAmount('1234567890,99')).toBe(1234567890.99)
    expect(parseAmount('12345678901')).toBeNull()
  })

  it.each([
    ['', 'vazio'],
    ['abc', 'texto'],
    ['1.234,56', 'separador de milhar'],
    ['10,555', 'mais de 2 casas'],
    ['0', 'zero'],
    ['0,00', 'zero com casas'],
    ['-5', 'negativo'],
    ['5,', 'vírgula sem casas'],
  ])('recusa "%s" (%s)', text => {
    expect(parseAmount(text)).toBeNull()
  })
})

// Precisa bater com Installments.split do backend: todas as parcelas recebem o
// valor truncado e o que sobra da divisão vai para a 1ª.
describe('installmentPreview', () => {
  it('mostra a 1ª parcela maior quando a divisão não é exata', () => {
    expect(plain(installmentPreview(100, 3))).toBe('3x de R$ 33,33 (1ª de R$ 33,34)')
    expect(plain(installmentPreview(10.01, 2))).toBe('2x de R$ 5,00 (1ª de R$ 5,01)')
  })

  it('mostra um valor só quando a divisão é exata', () => {
    expect(plain(installmentPreview(90, 3))).toBe('3x de R$ 30,00')
  })

  it('não erra centavos com valores que o double não representa exatamente', () => {
    // 0.29 * 100 = 28.999999999999996 no JavaScript: sem arredondar, sumiria 1 centavo.
    expect(plain(installmentPreview(0.29, 2))).toBe('2x de R$ 0,14 (1ª de R$ 0,15)')
  })
})

describe('meses', () => {
  it('addMonths atravessa a virada do ano nos dois sentidos', () => {
    expect(addMonths('2026-01', -1)).toBe('2025-12')
    expect(addMonths('2026-12', 1)).toBe('2027-01')
    expect(addMonths('2026-10', -13)).toBe('2025-09')
  })

  it('isValidMonth só aceita "AAAA-MM" com mês de 01 a 12', () => {
    expect(isValidMonth('2026-10')).toBe(true)
    expect(isValidMonth('2026-13')).toBe(false)
    expect(isValidMonth('2026-1')).toBe(false)
    expect(isValidMonth(null)).toBe(false)
  })

  it('monthOf pega o mês de uma data', () => {
    expect(monthOf('2026-10-07')).toBe('2026-10')
  })

  // O bug que o comentário de currentMonth evita: toISOString() usa UTC, e às
  // 22h do dia 31/10 em São Paulo (UTC-3) já seria 01/11 em UTC.
  it('currentMonth e todayIso usam a data LOCAL, não UTC', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 9, 31, 22, 0)) // 31/10/2026 22:00, horário local

    expect(currentMonth()).toBe('2026-10')
    expect(todayIso()).toBe('2026-10-31')
  })
})

describe('datas na tela', () => {
  it('formatDay mostra dia/mês sem passar por Date (que trocaria o dia pelo fuso)', () => {
    expect(formatDay('2026-10-07')).toBe('07/10')
  })

  it('formatDayHeading usa Hoje, Ontem e Amanhã perto de hoje', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 9, 8, 12, 0)) // quinta, 08/10/2026

    expect(formatDayHeading('2026-10-08')).toBe('Hoje')
    expect(formatDayHeading('2026-10-07')).toBe('Ontem')
    expect(formatDayHeading('2026-10-09')).toBe('Amanhã')
    expect(formatDayHeading('2026-10-10')).toBe('sábado, 10 de outubro')
  })

  it('formatMoney usa o formato brasileiro', () => {
    expect(plain(formatMoney(1234.5))).toBe('R$ 1.234,50')
  })
})

describe('transactionTitle', () => {
  const base = {
    description: null, categoryName: null, type: 'EXPENSE' as const,
    installmentNumber: null, installmentCount: null,
  }

  it('usa a descrição; sem ela, a categoria; sem as duas, o tipo', () => {
    expect(transactionTitle({ ...base, description: 'Biscoito', categoryName: 'Alimentação' })).toBe('Biscoito')
    expect(transactionTitle({ ...base, categoryName: 'Alimentação' })).toBe('Alimentação')
    expect(transactionTitle(base)).toBe('Despesa')
    expect(transactionTitle({ ...base, type: 'INCOME' })).toBe('Receita')
  })

  it('acrescenta "(n/total)" nas parcelas', () => {
    expect(transactionTitle({ ...base, description: 'PS5', installmentNumber: 2, installmentCount: 3 }))
      .toBe('PS5 (2/3)')
    expect(transactionTitle({ ...base, installmentNumber: 1, installmentCount: 12 })).toBe('Parcela (1/12)')
  })
})
