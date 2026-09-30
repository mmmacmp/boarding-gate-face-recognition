// Small inline SVG icons (no icon library). Every state in the UI pairs one of these with its colour.
const Svg = ({ children, size = 24, ...rest }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
       strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...rest}>
    {children}
  </svg>
)

export const CheckIcon = (p) => <Svg {...p}><circle cx="12" cy="12" r="10" /><path d="m7.5 12.5 3 3 6-6.5" /></Svg>
export const AlertIcon = (p) => <Svg {...p}><path d="M12 3 2.5 20h19Z" /><path d="M12 10v4" /><path d="M12 17.2v.1" /></Svg>
export const StepCloserIcon = (p) => (
  <Svg {...p}><circle cx="9" cy="5" r="2.2" /><path d="M9 8v7l-2.5 6M9 15l2.5 6M6 11h6" /><path d="M16 12h6m-2.5-2.5L22 12l-2.5 2.5" /></Svg>
)
export const PlaneIcon = (p) => <Svg {...p}><path d="M2 16l20-6-20-6 3 6-3 6Z" /><path d="M5 10h8" /></Svg>
export const SearchIcon = (p) => <Svg {...p}><circle cx="11" cy="11" r="6.5" /><path d="m20 20-4.2-4.2" /></Svg>
export const BanIcon = (p) => <Svg {...p}><circle cx="12" cy="12" r="9" /><path d="m5.6 5.6 12.8 12.8" /></Svg>
export const LockIcon = (p) => <Svg {...p}><rect x="4" y="10" width="16" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3" /></Svg>
export const CameraIcon = (p) => <Svg {...p}><path d="M4 8h3l2-3h6l2 3h3v11H4Z" /><circle cx="12" cy="13" r="3.5" /></Svg>
export const PlayIcon = (p) => <Svg {...p}><path d="M7 4.5v15l12-7.5Z" /></Svg>
export const PauseIcon = (p) => <Svg {...p}><path d="M8 5v14M16 5v14" /></Svg>
export const BoltIcon = (p) => <Svg {...p}><path d="M13 2 4 14h7l-1 8 9-12h-7Z" /></Svg>
export const ReplayIcon = (p) => <Svg {...p}><path d="M4 12a8 8 0 1 0 2.4-5.7" /><path d="M4 4v4h4" /></Svg>
export const CrossIcon = (p) => <Svg {...p}><circle cx="12" cy="12" r="10" /><path d="m9 9 6 6m0-6-6 6" /></Svg>
export const ResetIcon = (p) => <Svg {...p}><path d="M20 12a8 8 0 1 1-2.4-5.7" /><path d="M20 4v4h-4" /></Svg>
