import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { StoreProvider } from "./state/store";
import { I18nProvider, type Language } from "./i18n";
import BRAND from "./brand";

// design/tokens.css is linked from index.html so it is in place before the first paint
import "./styles/base.css";
import "./styles/palace.css";
import "./styles/ui.css";
import "./styles/shell.css";
import "./styles/screens.css";

document.title = BRAND.name;

/* peek the persisted language so there's no flash of the wrong locale */
function initialLang(): Language {
  try {
    const raw = localStorage.getItem("pinion.v1.state");
    if (raw) {
      const lang = (JSON.parse(raw) as { settings?: { language?: Language } }).settings?.language;
      if (lang === "ko" || lang === "en") return lang;
    }
  } catch {
    /* first run */
  }
  return "ko";
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <I18nProvider initial={initialLang()}>
      <StoreProvider>
        <App />
      </StoreProvider>
    </I18nProvider>
  </React.StrictMode>,
);
