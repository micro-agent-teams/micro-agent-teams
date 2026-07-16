import { cn } from "@/lib/utils";

// Rounded-square avatars, WeChat style. No avatar images available yet, so we
// render a deterministic colour + an initial derived from the seed.
const COLORS = [
  "#4e6ef2",
  "#12b76a",
  "#f79009",
  "#ef4444",
  "#7c3aed",
  "#06aed4",
  "#e64980",
  "#2dd4bf",
];

function colorFor(seed: number | string): string {
  const n = typeof seed === "number" ? seed : hash(seed);
  return COLORS[Math.abs(n) % COLORS.length];
}

function hash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return h;
}

export function Avatar({
  seed,
  label,
  className,
}: {
  seed: number | string;
  /** Text whose first char becomes the initial; falls back to the seed. */
  label?: string;
  className?: string;
}) {
  const initial = (label ?? String(seed)).trim().charAt(0).toUpperCase() || "#";
  return (
    <div
      className={cn(
        "flex size-10 shrink-0 items-center justify-center rounded-lg text-sm font-semibold text-white select-none",
        className,
      )}
      style={{ backgroundColor: colorFor(seed) }}
      aria-hidden
    >
      {initial}
    </div>
  );
}
