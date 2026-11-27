"use client";

import { useEffect, useState } from "react";

interface FlagTelemetry {
  flagName: string;
  enabled: boolean;
  totalRequests: number;
  failedRequests: number;
  errorRate: number;
  avgLatencyMs: number;
  maxErrorThreshold: number;
  maxLatencyThresholdMs: number;
  status: string;
}

interface RollbackIncident {
  id: string;
  flagName: string;
  reason: string;
  observedErrorRate: number;
  observedAvgLatencyMs: number;
  actionTaken: string;
  timestamp: string;
}

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

export default function ControlPlane() {
  const [flags, setFlags] = useState<FlagTelemetry[]>([]);
  const [incidents, setIncidents] = useState<RollbackIncident[]>([]);
  const [chaosEnabled, setChaosEnabled] = useState(false);
  const [connected, setConnected] = useState(false);
  const [lastLog, setLastLog] = useState<string>("Ready to simulate traffic...");

  const fetchSnapshot = async () => {
    try {
      const res = await fetch(`${API_BASE}/state`);
      const data = await res.json();
      setFlags(data.flags || []);
      setIncidents(data.incidents || []);
      setChaosEnabled(Boolean(data.chaosEnabled));
    } catch {
      setLastLog("Waiting for Spring Boot backend on port 8080...");
    }
  };

  useEffect(() => {
    fetchSnapshot();
    const es = new EventSource(`${API_BASE}/events/flags`);
    es.onopen = () => setConnected(true);
    es.addEventListener("flag-update", (event) => {
      const parsed = JSON.parse((event as MessageEvent).data);
      if (parsed.flags) setFlags(parsed.flags);
      if (parsed.incidents) setIncidents(parsed.incidents);
    });
    es.onerror = () => setConnected(false);
    return () => es.close();
  }, []);

  const toggleChaos = async () => {
    const next = !chaosEnabled;
    await fetch(`${API_BASE}/chaos?enabled=${next}`, { method: "POST" });
    setChaosEnabled(next);
    setLastLog(next ? "CHAOS INJECTED: checkout-v2 will throw 502 errors!" : "Chaos disabled.");
  };

  const sendTrafficBurst = async () => {
    setLastLog("Sending 6 checkout requests through @FlagGuarded...");
    for (let i = 0; i < 6; i++) {
      const res = await fetch(`${API_BASE}/checkout?amount=79.00`, { method: "POST" });
      const body = await res.json();
      setLastLog(`Request #${i + 1}: [${body.engine}] -> ${body.status}`);
    }
    await fetchSnapshot();
  };

  const reEnableFlag = async (flagName: string) => {
    await fetch(`${API_BASE}/flags/${flagName}/re-enable`, { method: "POST" });
    setChaosEnabled(false);
    setLastLog(`Re-enabled '${flagName}' via Flagsmith Management API & reset telemetry window.`);
    await fetchSnapshot();
  };

  return (
    <main className="min-h-screen max-w-6xl mx-auto p-6 space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white">
            FlagGuard <span className="text-emerald-400">OSS</span>
          </h1>
          <p className="text-sm text-slate-400">
            Self-Healing Auto-Rollback &amp; Real-Time SSE Release Controller for Self-Hosted Flagsmith
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className={`px-3 py-1 rounded-full text-xs font-medium ${connected ? "bg-emerald-500/20 text-emerald-300" : "bg-amber-500/20 text-amber-300"}`}>
            {connected ? "● SSE Stream Live" : "○ Polling Fallback"}
          </span>
          <a
            href="http://localhost:8000"
            target="_blank"
            rel="noreferrer"
            className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-200 border border-slate-700"
          >
            Open Flagsmith UI ↗
          </a>
        </div>
      </header>

      <section className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <button
          onClick={toggleChaos}
          className={`p-4 rounded-xl border text-left transition ${
            chaosEnabled
              ? "bg-rose-950/60 border-rose-500 text-rose-200"
              : "bg-slate-900 border-slate-800 hover:border-slate-700"
          }`}
        >
          <div className="text-xs uppercase tracking-wider font-semibold text-slate-400">Step 1 · Fault Injection</div>
          <div className="text-lg font-bold mt-1">{chaosEnabled ? "Chaos Mode: ON (Failing 502s)" : "Chaos Mode: OFF (Healthy)"}</div>
          <div className="text-xs text-slate-400 mt-1">Click to toggle simulated payment gateway failures</div>
        </button>

        <button
          onClick={sendTrafficBurst}
          className="p-4 rounded-xl bg-indigo-600/20 border border-indigo-500/50 hover:bg-indigo-600/30 text-left transition"
        >
          <div className="text-xs uppercase tracking-wider font-semibold text-indigo-300">Step 2 · Traffic Generator</div>
          <div className="text-lg font-bold text-white mt-1">Send 6 Checkout Requests →</div>
          <div className="text-xs text-indigo-200/80 mt-1">Executes @FlagGuarded(&quot;checkout-v2&quot;) endpoint</div>
        </button>

        <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 flex flex-col justify-between">
          <div className="text-xs uppercase tracking-wider font-semibold text-slate-400">Live Execution Feed</div>
          <div className="text-sm font-mono text-emerald-300 mt-2 break-all">{lastLog}</div>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold text-white">Guarded Feature Flags</h2>
        <div className="grid grid-cols-1 gap-4">
          {flags.map((flag) => (
            <div key={flag.flagName} className="p-5 rounded-xl bg-slate-900 border border-slate-800 flex flex-wrap items-center justify-between gap-4">
              <div className="space-y-1">
                <div className="flex items-center gap-3">
                  <span className="text-lg font-mono font-bold text-white">{flag.flagName}</span>
                  <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                    flag.enabled ? "bg-emerald-500/20 text-emerald-300" : "bg-rose-500/20 text-rose-300"
                  }`}>
                    {flag.enabled ? "ENABLED IN FLAGSMITH" : "AUTO-KILLED IN FLAGSMITH"}
                  </span>
                  <span className="px-2.5 py-0.5 rounded-full text-xs bg-slate-800 text-slate-300">
                    {flag.status}
                  </span>
                </div>
                <p className="text-xs text-slate-400">
                  SLA Guardrails: Max Error Rate {(flag.maxErrorThreshold * 100).toFixed(0)}% · Max Latency {flag.maxLatencyThresholdMs}ms
                </p>
              </div>

              <div className="flex items-center gap-6">
                <div className="text-right">
                  <div className="text-xs text-slate-400">Requests / Failed</div>
                  <div className="text-base font-mono font-bold">{flag.totalRequests} / <span className="text-rose-400">{flag.failedRequests}</span></div>
                </div>
                <div className="text-right">
                  <div className="text-xs text-slate-400">Error Rate</div>
                  <div className={`text-base font-mono font-bold ${flag.errorRate > flag.maxErrorThreshold ? "text-rose-400" : "text-emerald-400"}`}>
                    {(flag.errorRate * 100).toFixed(1)}%
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-xs text-slate-400">Avg Latency</div>
                  <div className="text-base font-mono font-bold">{flag.avgLatencyMs.toFixed(0)} ms</div>
                </div>
                {!flag.enabled && (
                  <button
                    onClick={() => reEnableFlag(flag.flagName)}
                    className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold"
                  >
                    Re-Enable Flag
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold text-white">Automated Rollback Audit Log</h2>
        {incidents.length === 0 ? (
          <div className="p-6 rounded-xl bg-slate-900/50 border border-slate-800 text-sm text-slate-400">
            No rollback incidents recorded yet. Enable Chaos Mode and send traffic to trigger an automated Flagsmith kill-switch!
          </div>
        ) : (
          <div className="space-y-2">
            {incidents.map((inc) => (
              <div key={inc.id} className="p-4 rounded-xl bg-rose-950/30 border border-rose-500/40 flex flex-wrap items-center justify-between gap-2">
                <div>
                  <span className="font-mono text-xs text-rose-300">[{inc.id}]</span>{" "}
                  <span className="font-semibold text-white">{inc.flagName}</span> —{" "}
                  <span className="text-rose-200 text-sm">{inc.reason}</span>
                </div>
                <div className="text-xs font-mono text-slate-400">
                  {inc.actionTaken} · {new Date(inc.timestamp).toLocaleTimeString()}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
