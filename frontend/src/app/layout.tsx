import "./globals.css";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "FlagGuard OSS — Self-Healing Flagsmith Control Plane",
  description: "Real-time SSE telemetry and automated kill-switch for self-hosted Flagsmith",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="bg-slate-950 text-slate-100 antialiased">{children}</body>
    </html>
  );
}
