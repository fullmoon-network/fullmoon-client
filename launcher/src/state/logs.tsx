/* The game's console lines, kept out of the store on purpose: the stream is the only state that
   changes hundreds of times in a second, and anything reading the store would render with every
   batch. Only the launch overlay reads this. */
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { core } from "../core/client";
import type { LogLevel } from "../core/bindings";
import { appendCapped, createBatcher } from "../core/logBatch";

export interface LogEntry {
  id: number;
  /** the run that printed it, so a surface can read one session and not the tail of the last */
  session: string;
  level: LogLevel;
  line: string;
  ts: string;
}

interface Logs {
  logs: LogEntry[];
  clearLogs: () => void;
}

const Ctx = createContext<Logs | null>(null);
let logSeq = 0;

const now = () =>
  new Date().toLocaleTimeString("en-GB", { hour12: false }) +
  "." +
  String(new Date().getMilliseconds()).padStart(3, "0");

export function LogsProvider({ children }: { children: ReactNode }) {
  const [logs, setLogs] = useState<LogEntry[]>([]);

  useEffect(() => {
    let alive = true;
    const batch = createBatcher<LogEntry>((lines) => setLogs((l) => appendCapped(l, lines)));
    const off = core.on("game://log", ({ sessionId, level, line }) => {
      batch.push({ id: ++logSeq, session: sessionId, level, line, ts: now() });
    });
    /* a run already in flight has been talking without us — take its tail so
       the console shows the session rather than starting from the next line */
    core
      .game_status()
      .then((gs) => (gs.sessionId ? core.game_log() : []))
      .then((lines) => {
        if (!alive || lines.length === 0) return;
        setLogs(
          lines.map(({ sessionId, level, line }) => ({
            id: ++logSeq,
            session: sessionId,
            level,
            line,
            // stamping the whole backlog with the moment we asked for it
            // would date every line to the same millisecond
            ts: line.match(/^\[(\d{2}:\d{2}:\d{2})\]/)?.[1] ?? "",
          })),
        );
      })
      .catch(() => {});
    return () => {
      alive = false;
      off();
      batch.cancel();
    };
  }, []);

  const clearLogs = useCallback(() => setLogs([]), []);
  const value = useMemo(() => ({ logs, clearLogs }), [logs, clearLogs]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useLogs(): Logs {
  const l = useContext(Ctx);
  if (!l) throw new Error("useLogs outside provider");
  return l;
}
