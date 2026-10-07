import { defineConfig, minimal2023Preset } from '@vite-pwa/assets-generator/config'

// Gera os ícones PNG do PWA a partir de public/pwa-icon.svg.
// Rodar só quando o logo mudar: npm run generate-icons (os PNGs vão para o git).
const fullBleed = { padding: 0, resizeOptions: { background: '#18181b' } }

export default defineConfig({
  preset: {
    ...minimal2023Preset,
    // padding 0: o SVG já é um quadrado cheio, sem margem.
    transparent: { ...minimal2023Preset.transparent, padding: 0 },
    // Android recorta "maskable" em círculo/gota; o "B" central fica na área segura.
    maskable: { ...minimal2023Preset.maskable, ...fullBleed },
    // iOS: sem transparência (o iOS pintaria de preto) e ele mesmo arredonda os cantos.
    apple: { ...minimal2023Preset.apple, ...fullBleed },
  },
  images: ['public/pwa-icon.svg'],
})
