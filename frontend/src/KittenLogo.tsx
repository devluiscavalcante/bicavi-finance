// Logo do tema rosa nas telas de login e cadastro: um gatinho que entra
// quicando, pisca, mexe a orelha e solta coraçõezinhos.
//
// Toda a animação está no CSS (.kitten-*); aqui só o desenho. Cada parte que
// se mexe é um <g> separado, com transform-origin próprio no CSS (a orelha gira
// pela base, o olho fecha pelo meio).
export function KittenLogo() {
  return (
    <span className="kitten" aria-hidden="true">
      <svg viewBox="0 0 64 64" className="kitten-svg">
        <g className="kitten-ear kitten-ear-left">
          <path d="M10 30 8 6l18 11Z" className="kitten-fur" />
          <path d="m13 24-1-12 9 6Z" className="kitten-inner" />
        </g>
        <g className="kitten-ear kitten-ear-right">
          <path d="m54 30 2-24-18 11Z" className="kitten-fur" />
          <path d="m51 24 1-12-9 6Z" className="kitten-inner" />
        </g>
        <ellipse cx="32" cy="36" rx="24" ry="21" className="kitten-fur" />
        <g className="kitten-eyes">
          <ellipse cx="23" cy="34" rx="3" ry="4" className="kitten-eye" />
          <ellipse cx="41" cy="34" rx="3" ry="4" className="kitten-eye" />
          <circle cx="24" cy="32.5" r="1" className="kitten-shine" />
          <circle cx="42" cy="32.5" r="1" className="kitten-shine" />
        </g>
        <ellipse cx="16" cy="42" rx="4" ry="2.5" className="kitten-blush" />
        <ellipse cx="48" cy="42" rx="4" ry="2.5" className="kitten-blush" />
        <path d="M29.5 40h5L32 43Z" className="kitten-nose" />
        <path d="M32 43c0 2.5-2 3.5-4 2.5M32 43c0 2.5 2 3.5 4 2.5" className="kitten-line" />
        <path d="M10 39 2 37M10 43l-7 2M54 39l8-2M54 43l7 2" className="kitten-line kitten-whiskers" />
      </svg>
      <span className="kitten-heart" />
      <span className="kitten-heart" />
      <span className="kitten-heart" />
    </span>
  )
}
