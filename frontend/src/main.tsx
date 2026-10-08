import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// Fonte Inter servida pelo próprio app (antes vinha do Google Fonts): nenhum
// pedido a terceiros e a Content-Security-Policy só precisa liberar 'self'.
// O CSS declara um arquivo por alfabeto (unicode-range); o navegador só baixa
// o que a página usa, que em português é o "latin".
import '@fontsource-variable/inter'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
