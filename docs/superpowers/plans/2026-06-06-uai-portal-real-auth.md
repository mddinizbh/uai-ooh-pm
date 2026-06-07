# uai-portal Real Auth Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (- [ ]) syntax.

**Goal:** Replace the interim auth stub with a real `uai-auth` provider (login/refresh/logout/me) selected by `VITE_AUTH_MODE=sso`, with transparent single-in-flight refresh-on-401, dual-token storage, and ADMIN = full access — keeping the stub for dev.

**Architecture:** Auth config + provider selection live in `src/lib/auth/`. A `realProvider` calls `POST /api/auth/login` then `GET /api/auth/me`; `tokenStore` holds access + refresh; `http.ts` runs a transparent refresh-on-401 with ONE shared in-flight refresh promise (concurrent 401s queue on it and all retry); on refresh failure it `clearSession()` and the existing reactive `ProtectedLayout` `<Navigate to="/login">` performs the redirect. Provider is chosen at runtime by `VITE_AUTH_MODE` (`sso` -> real, `stub` -> dev).

**Tech Stack:** Vite 5 + React 18 + TypeScript 5, Vitest 3 (jsdom, globals) via `./node_modules/.bin/vitest`, Testing Library, react-router-dom 6. Runner: `npm` / `node_modules/.bin` (bun may be absent — never invoke bun).

---

## File Structure

| File | Create/Modify | Single responsibility |
|---|---|---|
| `src/lib/auth/config.ts` | Create | Auth env constants: `AuthMode`, `AUTH_MODE`, `SSO_REDIRECT_DEFERRED`, `AUTH_BASE_URL`, `REFRESH_WINDOW_SEC`, `authUrl(path)`. |
| `src/lib/auth/config.test.ts` | Create | Asserts default mode/base-url and `authUrl` join. |
| `src/lib/auth/session.ts` | Modify | Add `ADMIN` to `Role`; add optional `refreshToken` to `AuthSession`. |
| `src/lib/auth/tokenStore.ts` | Modify | Add `getRefreshToken()` and `updateTokens()` (rotation primitive). |
| `src/lib/auth/tokenStore.test.ts` | Modify | Tests for `getRefreshToken` + `updateTokens` rotation/no-op. |
| `src/lib/auth/realProvider.ts` | Create | Real auth calls: `realLogin` (login -> me), `fetchMe`, `realLogout`, `AuthError`, role mapping. |
| `src/lib/auth/realProvider.test.ts` | Create | me-populates-profile, login 401 throws, logout Bearer, no-op logout. |
| `src/lib/auth/stubProvider.ts` | Modify | Drop local mode constants; re-export them from `config` (Task 1), then remove dead re-exports after AuthContext switches (Task 6). |
| `src/lib/auth/provider.ts` | Create | `SessionProvider` interface + `resolveProvider(mode)` (stub vs real). |
| `src/lib/auth/provider.test.ts` | Create | `resolveProvider` picks stub vs real and both produce a session. |
| `src/lib/auth/access.ts` | Create | `roleLabel(role)` + `canAccessVertical(role, id)` (ADMIN = full access). |
| `src/lib/auth/access.test.ts` | Create | ADMIN full access, CLIENT_VIEWER restricted, labels. |
| `src/lib/api/http.ts` | Modify | Transparent refresh-on-401: single in-flight refresh + queue + retry; failure -> clearSession. |
| `src/lib/api/http.test.ts` | Modify | Concurrent-401 -> ONE refresh + all retry, rotation, failure -> logout. |
| `src/contexts/AuthContext.tsx` | Modify | Async `login`/`loginWithSso`/`logout` via `resolveProvider`; read mode from `config`. |
| `src/contexts/AuthContext.test.tsx` | Modify | Await async login family. |
| `src/components/portal/PortalSidebar.tsx` | Modify | Role-gate verticals via `canAccessVertical`; real footer (`userName` + `roleLabel`). |
| `src/components/portal/PortalSidebar.test.tsx` | Modify | Login as ADMIN; footer shows name + "Admin"; OOH visible. |
| `src/pages/LoginPage.tsx` | Modify | Async submit + error display; hide SSO button when mode!=='stub'. |
| `src/pages/LoginPage.test.tsx` | Create | Login failure surfaces error; SSO button hidden in sso mode. |
| `.env.production` | Modify | `VITE_AUTH_MODE=sso` + `VITE_AUTH_BASE_URL=/api/auth`. |

## Setup (run once before Task 1)

- [ ] Step: Branch off main for the cutover work.
  ```bash
  cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-portal
  git checkout main && git pull --ff-only
  git checkout -b feat/uai-auth-portal-real-provider
  ```
- [ ] Step: Confirm the suite is green at baseline.
  ```bash
  ./node_modules/.bin/vitest run
  ```
  Expected: `Test Files  28 passed (28)` · `Tests  165 passed (165)`.

---

### Task 1: Auth config module (mode / base-url / refresh window)

**Files:**
- Create `src/lib/auth/config.ts`
- Create `src/lib/auth/config.test.ts`
- Modify `src/lib/auth/stubProvider.ts` (replace lines 17-24: move mode constants to config, re-export for back-compat)

**Test:** `src/lib/auth/config.test.ts`

- [ ] Step: Write the failing test.
  ```ts
  import { describe, it, expect } from "vitest";
  import { AUTH_BASE_URL, AUTH_MODE, REFRESH_WINDOW_SEC, authUrl } from "./config";

  describe("auth config", () => {
    it("default mode = stub e base = /api/auth (ambiente de teste)", () => {
      expect(AUTH_MODE).toBe("stub");
      expect(AUTH_BASE_URL).toBe("/api/auth");
    });

    it("REFRESH_WINDOW_SEC = 7 dias", () => {
      expect(REFRESH_WINDOW_SEC).toBe(7 * 24 * 60 * 60);
    });

    it("authUrl junta a base + path", () => {
      expect(authUrl("/login")).toBe("/api/auth/login");
      expect(authUrl("/me")).toBe("/api/auth/me");
    });
  });
  ```
- [ ] Step: Run it, verify it FAILS.
  ```bash
  ./node_modules/.bin/vitest run src/lib/auth/config.test.ts
  ```
  Expected: fails to resolve import `./config` (`Failed to load url ./config` / "No test found" collection error) — module does not exist yet.
- [ ] Step: Minimal implementation — create `src/lib/auth/config.ts`.
  ```ts
  // config — parâmetros de auth resolvidos por env (Vite), fora do React.
  // Fonte única do modo de auth e da base do uai-auth. Em prod, o nginx faz o strip
  // /api/auth/* -> /api/v1/auth/* (uai-infra), então o front fala same-origin com /api/auth.

  export type AuthMode = "stub" | "sso";

  /** Modo de auth. Default `stub` (dev). `sso` seleciona o provider real (uai-auth). */
  export const AUTH_MODE: AuthMode =
    (import.meta.env.VITE_AUTH_MODE as AuthMode) || "stub";

  /** Enquanto !== "sso", o provider real não é selecionado (cai no stub de dev). */
  export const SSO_REDIRECT_DEFERRED = AUTH_MODE !== "sso";

  /** Base same-origin do uai-auth. Default `/api/auth` (nginx strip -> /api/v1/auth). */
  export const AUTH_BASE_URL: string =
    import.meta.env.VITE_AUTH_BASE_URL || "/api/auth";

  /** Janela do refresh token (7d, contrato uai-auth) — define a presença da sessão local. */
  export const REFRESH_WINDOW_SEC = 7 * 24 * 60 * 60;

  /** Junta a base do auth com o path (`/login`, `/refresh`, `/me`, `/logout`). */
  export function authUrl(path: string): string {
    return AUTH_BASE_URL.replace(/\/+$/, "") + path;
  }
  ```
- [ ] Step: Re-home the mode constants in `stubProvider.ts` (DRY) by replacing its local definitions with re-exports. Replace lines 16-24, which are:
  ```ts
  import { AuthSession, AuthUser, Role } from "./session";

  export type AuthMode = "stub" | "sso";

  /** Modo de auth resolvido por env. Default `stub` (gate uai-auth=RED). */
  export const AUTH_MODE: AuthMode =
    (import.meta.env.VITE_AUTH_MODE as AuthMode) || "stub";

  /** Enquanto true, o redirect SSO real não dispara — cai no stub login. */
  export const SSO_REDIRECT_DEFERRED = AUTH_MODE !== "sso";
  ```
  with:
  ```ts
  import { AuthSession, AuthUser, Role } from "./session";

  // Modo de auth e flags migraram para `./config` (fonte única). Re-export por compat
  // enquanto os consumidores migram (removido na Task 6 quando o AuthContext usa config).
  export { AUTH_MODE, SSO_REDIRECT_DEFERRED } from "./config";
  export type { AuthMode } from "./config";
  ```
- [ ] Step: Run tests, verify PASS.
  ```bash
  ./node_modules/.bin/vitest run src/lib/auth/config.test.ts src/lib/auth/stubProvider.test.ts src/contexts/AuthContext.test.tsx
  ```
  Expected: all pass (config 3, stubProvider 6, AuthContext 5).
- [ ] Step: Commit.
  ```bash
  git add src/lib/auth/config.ts src/lib/auth/config.test.ts src/lib/auth/stubProvider.ts
  git commit -m "feat(auth): add config module (mode/base-url/refresh window) and re-home mode constants" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

### Task 2: tokenStore holds access + refresh (rotation primitive)

**Files:**
- Modify `src/lib/auth/session.ts` (add optional `refreshToken` to `AuthSession`, after line 23)
- Modify `src/lib/auth/tokenStore.ts` (add `getRefreshToken` + `updateTokens`, after line 40)
- Modify `src/lib/auth/tokenStore.test.ts` (add tests)

**Test:** `src/lib/auth/tokenStore.test.ts`

- [ ] Step: Write the failing tests — extend the import on lines 2-9 and append a describe. First change the import block (lines 2-9) to add the two new functions:
  ```ts
  import {
    getSession,
    getToken,
    getRefreshToken,
    setSession,
    clearSession,
    subscribe,
    updateTokens,
    STORAGE_SESSION_KEY,
  } from "./tokenStore";
  ```
  Then append (after line 66, the final `});`):
  ```ts
  describe("tokenStore — access + refresh (rotação)", () => {
    it("getRefreshToken devolve o refresh da sessão (ou null)", () => {
      expect(getRefreshToken()).toBeNull();
      setSession(makeSession({ refreshToken: "r1" }));
      expect(getRefreshToken()).toBe("r1");
    });

    it("updateTokens rotaciona access+refresh e estende a janela, preservando o usuário", () => {
      const s = makeSession({ token: "a1", refreshToken: "r1" });
      setSession(s);
      const newExp = Math.floor(Date.now() / 1000) + 7 * 24 * 3600;

      expect(updateTokens("a2", "r2", newExp)).toBe(true);

      const after = getSession()!;
      expect(after.token).toBe("a2");
      expect(after.refreshToken).toBe("r2");
      expect(after.expiresAt).toBe(newExp);
      expect(after.user).toEqual(s.user);
    });

    it("updateTokens é no-op (false) quando não há sessão", () => {
      expect(updateTokens("a", "r", Math.floor(Date.now() / 1000) + 3600)).toBe(false);
      expect(getSession()).toBeNull();
    });
  });
  ```
- [ ] Step: Run it, verify it FAILS.
  ```bash
  ./node_modules/.bin/vitest run src/lib/auth/tokenStore.test.ts
  ```
  Expected: fails — `getRefreshToken` / `updateTokens` are not exported (TS/runtime: "updateTokens is not a function" / import undefined).
- [ ] Step: Minimal implementation — add `refreshToken` to `AuthSession` in `src/lib/auth/session.ts`. Replace lines 18-24:
  ```ts
  export interface AuthSession {
    /** access token Bearer propagado às chamadas do intel (validado por introspection). */
    token: string;
    /** refresh token (modo real). Ausente no stub. Usado na rotação refresh-on-401. */
    refreshToken?: string;
    user: AuthUser;
    /** epoch em segundos. Stub: exp do token (8h). Real: janela do refresh (7d). */
    expiresAt: number;
  }
  ```
- [ ] Step: Minimal implementation — add the two functions in `src/lib/auth/tokenStore.ts`, immediately after `getToken` (after line 40):
  ```ts
  /** Refresh token corrente, ou null (deslogado / sessão stub sem refresh). */
  export function getRefreshToken(): string | null {
    return getSession()?.refreshToken ?? null;
  }

  /**
   * Rotaciona os tokens da sessão vigente (refresh-on-401). Preserva o usuário; troca
   * access+refresh e estende a janela (`expiresAt`). Retorna false (no-op) sem sessão.
   */
  export function updateTokens(token: string, refreshToken: string, expiresAt: number): boolean {
    const current = getSession();
    if (!current) return false;
    setSession({ ...current, token, refreshToken, expiresAt });
    return true;
  }
  ```
- [ ] Step: Run tests, verify PASS.
  ```bash
  ./node_modules/.bin/vitest run src/lib/auth/tokenStore.test.ts
  ```
  Expected: 8 tests pass.
- [ ] Step: Commit.
  ```bash
  git add src/lib/auth/session.ts src/lib/auth/tokenStore.ts src/lib/auth/tokenStore.test.ts
  git commit -m "feat(auth): store access+refresh and add updateTokens rotation primitive" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

### Task 3: Real provider (login -> me, logout)

**Files:**
- Modify `src/lib/auth/session.ts` (add `ADMIN` to `Role`, line 8)
- Create `src/lib/auth/realProvider.ts`
- Create `src/lib/auth/realProvider.test.ts`

**Test:** `src/lib/auth/realProvider.test.ts`

- [ ] Step: Write the failing test.
  ```ts
  import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
  import { realLogin, realLogout, AuthError } from "./realProvider";
  import { isSessionValid } from "./session";

  let fetchMock: ReturnType<typeof vi.fn>;

  function jsonResponse(status: number, body: unknown): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
  }

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  describe("realProvider — login + me", () => {
    it("posta /api/auth/login, busca /api/auth/me e popula o perfil (access+refresh)", async () => {
      fetchMock.mockImplementation(async (url: string | URL, init?: RequestInit) => {
        const u = String(url);
        if (u.endsWith("/api/auth/login")) {
          expect((init as RequestInit).method).toBe("POST");
          expect(JSON.parse(String((init as RequestInit).body))).toEqual({
            email: "marley.diniz@gmail.com",
            password: "s3cr3t",
          });
          return jsonResponse(200, { access_token: "a1", refresh_token: "r1", expires_in: 900 });
        }
        if (u.endsWith("/api/auth/me")) {
          expect(new Headers((init as RequestInit).headers).get("Authorization")).toBe("Bearer a1");
          return jsonResponse(200, {
            user_id: "uuid-1",
            email: "marley.diniz@gmail.com",
            role: "ADMIN",
            tenant_id: "t1",
          });
        }
        throw new Error("url inesperada: " + u);
      });

      const s = await realLogin("marley.diniz@gmail.com", "s3cr3t");

      expect(s.token).toBe("a1");
      expect(s.refreshToken).toBe("r1");
      expect(s.user).toEqual({
        sub: "uuid-1",
        name: "marley.diniz",
        email: "marley.diniz@gmail.com",
        role: "ADMIN",
      });
      expect(isSessionValid(s)).toBe(true);
    });

    it("lança AuthError(401) em credencial inválida", async () => {
      fetchMock.mockResolvedValue(jsonResponse(401, { error: "bad" }));
      await expect(realLogin("x@y.com", "bad")).rejects.toBeInstanceOf(AuthError);
      await expect(realLogin("x@y.com", "bad")).rejects.toMatchObject({ status: 401 });
    });
  });

  describe("realProvider — logout", () => {
    it("posta /api/auth/logout com Bearer", async () => {
      fetchMock.mockResolvedValue(jsonResponse(204, {}));
      await realLogout("a1");
      const [url, init] = fetchMock.mock.calls.at(-1)!;
      expect(String(url)).toBe("/api/auth/logout");
      expect((init as RequestInit).method).toBe("POST");
      expect(new Headers((init as RequestInit).headers).get("Authorization")).toBe("Bearer a1");
    });

    it("logout sem token é no-op (não chama a rede)", async () => {
      await realLogout(null);
      expect(fetchMock).not.toHaveBeenCalled();
    });
  });
  ```
- [ ] Step: Run it, verify it FAILS.
  ```bash
  ./node_modules/.bin/vitest run src/lib/auth/realProvider.test.ts
  ```
  Expected: fails to resolve `./realProvider` (module does not exist).
- [ ] Step: Minimal implementation — add `ADMIN` to `Role` in `src/lib/auth/session.ts`. Replace line 8:
  ```ts
  export type Role = "ADMIN" | "AGENCY_MANAGER" | "CLIENT_VIEWER";
  ```
- [ ] Step: Minimal implementation — create `src/lib/auth/realProvider.ts`.
  ```ts
  // realProvider — login real contra o uai-auth (modo `sso`). Substitui o mint do stub.
  //
  // Fluxo de login: POST /api/auth/login {email,password} -> {access_token,refresh_token,expires_in},
  // depois GET /api/auth/me (Bearer) -> {user_id,email,role,tenant_id} para popular o perfil
  // (não confiamos no display do JWT — o /me é a fonte). A sessão guarda os 2 tokens; o
  // refresh-on-401 transparente vive no http.ts. Em prod o nginx strip /api/auth -> /api/v1/auth.

  import { AuthSession, AuthUser, Role } from "./session";
  import { authUrl, REFRESH_WINDOW_SEC } from "./config";

  /** Erro de auth carregando o status HTTP (login/me falharam). */
  export class AuthError extends Error {
    constructor(readonly status: number, message: string) {
      super(message);
      this.name = "AuthError";
    }
  }

  interface LoginResponse {
    access_token: string;
    refresh_token: string;
    expires_in: number;
  }

  interface MeResponse {
    user_id: string;
    email: string;
    role: string;
    tenant_id: string;
  }

  /** Mapeia a string de role do uai-auth para o `Role` do portal (default least-privilege). */
  function roleFromString(value: string): Role {
    switch (value) {
      case "ADMIN":
        return "ADMIN";
      case "AGENCY_MANAGER":
        return "AGENCY_MANAGER";
      case "CLIENT_VIEWER":
        return "CLIENT_VIEWER";
      default:
        return "CLIENT_VIEWER";
    }
  }

  /** Nome de exibição derivado do email (o /me não traz display name). */
  function nameFromEmail(email: string): string {
    return email.split("@")[0] || "Usuário";
  }

  function userFromMe(me: MeResponse): AuthUser {
    return {
      sub: me.user_id,
      name: nameFromEmail(me.email),
      email: me.email,
      role: roleFromString(me.role),
    };
  }

  /** GET /api/auth/me -> perfil. Lança AuthError em !ok. */
  export async function fetchMe(accessToken: string): Promise<AuthUser> {
    const res = await fetch(authUrl("/me"), {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!res.ok) throw new AuthError(res.status, `me falhou (HTTP ${res.status})`);
    return userFromMe((await res.json()) as MeResponse);
  }

  /** Login por credenciais: /login depois /me. Devolve a sessão (2 tokens + perfil). */
  export async function realLogin(email: string, password: string): Promise<AuthSession> {
    const res = await fetch(authUrl("/login"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    if (!res.ok) throw new AuthError(res.status, `login falhou (HTTP ${res.status})`);

    const body = (await res.json()) as LoginResponse;
    const user = await fetchMe(body.access_token);
    const nowSec = Math.floor(Date.now() / 1000);
    return {
      token: body.access_token,
      refreshToken: body.refresh_token,
      user,
      expiresAt: nowSec + REFRESH_WINDOW_SEC,
    };
  }

  /** POST /api/auth/logout (best-effort: blacklist no servidor). O logout local é sempre feito. */
  export async function realLogout(accessToken: string | null): Promise<void> {
    if (!accessToken) return;
    try {
      await fetch(authUrl("/logout"), {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}` },
      });
    } catch {
      // rede caiu: ignoramos — o AuthContext limpa a sessão local de qualquer forma.
    }
  }
  ```
- [ ] Step: Run tests, verify PASS.
  ```bash
  ./node_modules/.bin/vitest run src/lib/auth/realProvider.test.ts
  ```
  Expected: 4 tests pass.
- [ ] Step: Commit.
  ```bash
  git add src/lib/auth/session.ts src/lib/auth/realProvider.ts src/lib/auth/realProvider.test.ts
  git commit -m "feat(auth): add real provider (login->me, logout) and ADMIN role" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

### Task 4: Provider selection (stub vs real by mode)

**Files:**
- Create `src/lib/auth/provider.ts`
- Create `src/lib/auth/provider.test.ts`

**Test:** `src/lib/auth/provider.test.ts`

- [ ] Step: Write the failing test.
  ```ts
  import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
  import { resolveProvider } from "./provider";

  let fetchMock: ReturnType<typeof vi.fn>;
  function jsonResponse(status: number, body: unknown): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
  }

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  describe("resolveProvider", () => {
    it("'stub' -> login stub (token de 3 partes, sem rede)", async () => {
      const p = resolveProvider("stub");
      const s = await p.login("gerente@uai.com", "x");
      expect(fetchMock).not.toHaveBeenCalled();
      expect(s.token.split(".")).toHaveLength(3);
      expect(s.user.role).toBe("AGENCY_MANAGER");
    });

    it("'sso' -> login real (POST /api/auth/login + GET /api/auth/me)", async () => {
      fetchMock.mockImplementation(async (url: string | URL) => {
        const u = String(url);
        if (u.endsWith("/api/auth/login"))
          return jsonResponse(200, { access_token: "a", refresh_token: "r", expires_in: 900 });
        if (u.endsWith("/api/auth/me"))
          return jsonResponse(200, { user_id: "u", email: "e@x.com", role: "ADMIN", tenant_id: "t" });
        throw new Error("url inesperada: " + u);
      });

      const p = resolveProvider("sso");
      const s = await p.login("e@x.com", "pw");
      expect(s.token).toBe("a");
      expect(s.refreshToken).toBe("r");
      expect(s.user.role).toBe("ADMIN");
    });

    it("'sso' -> logout real chama /api/auth/logout; 'stub' -> logout local (no-op de rede)", async () => {
      fetchMock.mockResolvedValue(jsonResponse(204, {}));
      await resolveProvider("stub").logout("a1");
      expect(fetchMock).not.toHaveBeenCalled();

      await resolveProvider("sso").logout("a1");
      expect(String(fetchMock.mock.calls.at(-1)![0])).toBe("/api/auth/logout");
    });
  });
  ```
- [ ] Step: Run it, verify it FAILS.
  ```bash
  ./node_modules/.bin/vitest run src/lib/auth/provider.test.ts
  ```
  Expected: fails to resolve `./provider` (module does not exist).
- [ ] Step: Minimal implementation — create `src/lib/auth/provider.ts`.
  ```ts
  // provider — seleção do provider de sessão por modo (stub dev | real sso).
  // O AuthContext consome só esta interface; a troca stub<->real é uma linha de env.

  import { AuthSession } from "./session";
  import { AUTH_MODE, AuthMode } from "./config";
  import { stubLogin, stubSsoLogin } from "./stubProvider";
  import { realLogin, realLogout } from "./realProvider";

  export interface SessionProvider {
    login(email: string, password: string): Promise<AuthSession>;
    /** Entrada SSO "Entrar com uAI" (demo no stub; sem IdP redirect no MVP real). */
    loginWithSso(): Promise<AuthSession>;
    /** Logout no servidor quando aplicável; o logout local é do AuthContext. */
    logout(accessToken: string | null): Promise<void>;
  }

  const stubSessionProvider: SessionProvider = {
    login: async (email, password) => stubLogin(email, password),
    loginWithSso: async () => stubSsoLogin(),
    logout: async () => {
      /* stub: nada no servidor — só o clearSession local. */
    },
  };

  const realSessionProvider: SessionProvider = {
    login: (email, password) => realLogin(email, password),
    loginWithSso: async () => {
      throw new Error("SSO redirect não suportado no MVP — use login por credenciais.");
    },
    logout: (accessToken) => realLogout(accessToken),
  };

  /** Resolve o provider pelo modo (default: env `AUTH_MODE`). */
  export function resolveProvider(mode: AuthMode = AUTH_MODE): SessionProvider {
    return mode === "sso" ? realSessionProvider : stubSessionProvider;
  }
  ```
- [ ] Step: Run tests, verify PASS.
  ```bash
  ./node_modules/.bin/vitest run src/lib/auth/provider.test.ts
  ```
  Expected: 3 tests pass.
- [ ] Step: Commit.
  ```bash
  git add src/lib/auth/provider.ts src/lib/auth/provider.test.ts
  git commit -m "feat(auth): add SessionProvider selection (stub vs real by mode)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

### Task 5: Transparent refresh-on-401 with single in-flight refresh + queue

**Files:**
- Modify `src/lib/api/http.ts` (rewrite — add refresh logic; lines 10-54)
- Modify `src/lib/api/http.test.ts` (add imports + helper + a new describe; existing tests unchanged)

**Test:** `src/lib/api/http.test.ts`

- [ ] Step: Write the failing tests. First extend the imports (lines 1-4) to:
  ```ts
  import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
  import { apiFetch, buildUrl } from "./http";
  import { setSession, getToken, getRefreshToken } from "@/lib/auth/tokenStore";
  import { stubLogin } from "@/lib/auth/stubProvider";
  import { AuthSession } from "@/lib/auth/session";
  ```
  Then append, after the final `});` (line 69), this block:
  ```ts
  function jsonResponse(status: number, body: unknown): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
  }

  function realSession(token: string, refreshToken: string): AuthSession {
    const nowSec = Math.floor(Date.now() / 1000);
    return {
      token,
      refreshToken,
      user: { sub: "u1", name: "u", email: "u@uai.com", role: "ADMIN" },
      expiresAt: nowSec + 7 * 24 * 3600,
    };
  }

  describe("apiFetch — refresh-on-401 transparente (fila + rotação)", () => {
    let fetchMock: ReturnType<typeof vi.fn>;

    beforeEach(() => {
      localStorage.clear();
      fetchMock = vi.fn();
      vi.stubGlobal("fetch", fetchMock);
    });
    afterEach(() => vi.unstubAllGlobals());

    it("401 concorrentes disparam UM ÚNICO refresh e refazem TODAS as requests", async () => {
      setSession(realSession("access-1", "refresh-1"));
      let refreshCalls = 0;

      fetchMock.mockImplementation(async (url: string | URL, init?: RequestInit) => {
        const u = String(url);
        if (u.includes("/api/auth/refresh")) {
          refreshCalls++;
          return jsonResponse(200, { access_token: "access-2", refresh_token: "refresh-2", expires_in: 900 });
        }
        const authz = new Headers(init?.headers).get("Authorization");
        return authz === "Bearer access-2" ? jsonResponse(200, {}) : jsonResponse(401, {});
      });

      const results = await Promise.all([
        apiFetch("/api/lines"),
        apiFetch("/api/regions"),
        apiFetch("/api/lines/ranking"),
      ]);

      expect(refreshCalls).toBe(1);
      expect(results.map((r) => r.status)).toEqual([200, 200, 200]);
      expect(getToken()).toBe("access-2");
      expect(getRefreshToken()).toBe("refresh-2");
    });

    it("rotaciona os 2 tokens em uma request única após o 401", async () => {
      setSession(realSession("access-1", "refresh-1"));
      fetchMock.mockImplementation(async (url: string | URL, init?: RequestInit) => {
        const u = String(url);
        if (u.includes("/api/auth/refresh"))
          return jsonResponse(200, { access_token: "access-9", refresh_token: "refresh-9", expires_in: 900 });
        const authz = new Headers(init?.headers).get("Authorization");
        return authz === "Bearer access-9" ? jsonResponse(200, {}) : jsonResponse(401, {});
      });

      const res = await apiFetch("/api/lines");
      expect(res.status).toBe(200);
      expect(getToken()).toBe("access-9");
      expect(getRefreshToken()).toBe("refresh-9");
    });

    it("refresh que falha -> logout (clearSession): token e refresh some", async () => {
      setSession(realSession("access-1", "refresh-1"));
      fetchMock.mockImplementation(async (url: string | URL) => {
        const u = String(url);
        if (u.includes("/api/auth/refresh")) return jsonResponse(401, {});
        return jsonResponse(401, {});
      });

      const res = await apiFetch("/api/lines");
      expect(res.status).toBe(401);
      expect(getToken()).toBeNull();
      expect(getRefreshToken()).toBeNull();
    });

    it("sessão stub (sem refresh) em 401 -> logout direto, sem chamar /refresh", async () => {
      setSession(stubLogin("gerente@uai.com", "x"));
      fetchMock.mockResolvedValue(jsonResponse(401, {}));

      await apiFetch("/api/lines");
      const calledRefresh = fetchMock.mock.calls.some((c) => String(c[0]).includes("/api/auth/refresh"));
      expect(calledRefresh).toBe(false);
      expect(getToken()).toBeNull();
    });
  });
  ```
- [ ] Step: Run it, verify it FAILS.
  ```bash
  ./node_modules/.bin/vitest run src/lib/api/http.test.ts
  ```
  Expected: the new block fails — current `http.ts` clears the session on 401 instead of refreshing, so the concurrent test gets `refreshCalls === 0`, `results` = `[401, 401, 401]`, and `getToken()` is null (not `"access-2"`).
- [ ] Step: Minimal implementation — rewrite `src/lib/api/http.ts`.
  ```ts
  // http — interceptor de Bearer + base URL + refresh-on-401 transparente.
  //
  // No 401 do intel (token expirado/revogado), dispara UM refresh contra o uai-auth e
  // refaz a request original. 401s concorrentes compartilham a MESMA promise de refresh
  // (fila) — uma única chamada a /refresh para N requests. Em falha, clearSession(); o
  // redirect pro /login acontece reativamente (AuthContext assina o tokenStore ->
  // ProtectedLayout <Navigate>). A rotação troca os 2 tokens via tokenStore.updateTokens.

  import { getToken, getRefreshToken, updateTokens, clearSession } from "@/lib/auth/tokenStore";
  import { authUrl, REFRESH_WINDOW_SEC } from "@/lib/auth/config";

  /** Base URL do intel. Vazio ⇒ caminhos relativos (proxy/dev). */
  export const INTEL_BASE_URL: string = import.meta.env.VITE_INTEL_BASE_URL ?? "";

  /** Junta base + path. Path absoluto (http...) passa direto; base vazia ⇒ só o path. */
  export function buildUrl(base: string, path: string): string {
    if (/^https?:\/\//i.test(path)) return path;
    if (!base) return path;
    const b = base.replace(/\/+$/, "");
    const p = path.startsWith("/") ? path : `/${path}`;
    return `${b}${p}`;
  }

  export interface ApiFetchOptions extends RequestInit {
    /** Sobrescreve a base (default: `INTEL_BASE_URL`). */
    baseUrl?: string;
    /** Se false, não injeta o Bearer (ex.: endpoints públicos). Default true. */
    auth?: boolean;
    /** Interno: marca a request já refeita pós-refresh (evita loop). */
    __retried?: boolean;
  }

  interface RefreshResponse {
    access_token: string;
    refresh_token: string;
    expires_in: number;
  }

  // Refresh ÚNICO em voo: 401s concorrentes aguardam a MESMA promise (fila).
  let refreshInFlight: Promise<boolean> | null = null;

  async function performRefresh(): Promise<boolean> {
    const refreshToken = getRefreshToken();
    if (!refreshToken) return false;
    try {
      const res = await fetch(authUrl("/refresh"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refresh_token: refreshToken }),
      });
      if (!res.ok) return false;
      const body = (await res.json()) as RefreshResponse;
      const expiresAt = Math.floor(Date.now() / 1000) + REFRESH_WINDOW_SEC;
      return updateTokens(body.access_token, body.refresh_token, expiresAt);
    } catch {
      return false;
    }
  }

  /** Dispara (ou reaproveita) o refresh em voo — garante UMA chamada para N requests. */
  function refreshTokens(): Promise<boolean> {
    if (!refreshInFlight) {
      refreshInFlight = performRefresh().finally(() => {
        refreshInFlight = null;
      });
    }
    return refreshInFlight;
  }

  /**
   * `fetch` com interceptor: injeta `Authorization: Bearer <token>`, prefixa a base URL e,
   * em 401, tenta um refresh transparente (fila única) e refaz a request. Refresh falhou ⇒
   * clearSession (relogin reativo).
   */
  export async function apiFetch(path: string, options: ApiFetchOptions = {}): Promise<Response> {
    const { baseUrl = INTEL_BASE_URL, auth = true, headers, __retried = false, ...rest } = options;

    const finalHeaders = new Headers(headers);
    if (auth) {
      const token = getToken();
      if (token && !finalHeaders.has("Authorization")) {
        finalHeaders.set("Authorization", `Bearer ${token}`);
      }
    }

    const response = await fetch(buildUrl(baseUrl, path), { ...rest, headers: finalHeaders });

    if (response.status === 401 && auth && !__retried) {
      const refreshed = await refreshTokens();
      if (refreshed) {
        return apiFetch(path, { ...options, __retried: true });
      }
      // Sem refresh possível / refresh falhou ⇒ derruba a sessão local. O redirect /login
      // ocorre reativamente (AuthContext -> ProtectedLayout <Navigate to="/login">).
      clearSession();
    }

    return response;
  }
  ```
- [ ] Step: Run the full `http.test.ts` (old + new), verify PASS.
  ```bash
  ./node_modules/.bin/vitest run src/lib/api/http.test.ts
  ```
  Expected: all pass — the original 8 `buildUrl`/interceptor tests plus the 4 new refresh tests.
- [ ] Step: Commit.
  ```bash
  git add src/lib/api/http.ts src/lib/api/http.test.ts
  git commit -m "feat(api): transparent refresh-on-401 with single in-flight refresh and request queue" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

### Task 6: Wire AuthContext to the provider (async login family)

**Files:**
- Modify `src/contexts/AuthContext.tsx` (rewrite — async methods + provider selection)
- Modify `src/contexts/AuthContext.test.tsx` (await the async login family)
- Modify `src/lib/auth/stubProvider.ts` (remove the now-dead re-export lines added in Task 1)

**Test:** `src/contexts/AuthContext.test.tsx`

- [ ] Step: Write the failing test — rewrite the file (the click handlers are now async, so assertions must run after `act`).
  ```tsx
  import { describe, it, expect, beforeEach, afterEach } from "vitest";
  import { render, screen, fireEvent, cleanup, act } from "@testing-library/react";
  import { MemoryRouter } from "react-router-dom";
  import { AuthProvider, useAuth } from "./AuthContext";
  import { getToken } from "@/lib/auth/tokenStore";

  function Harness() {
    const { isAuthenticated, role, userName, token, login, loginWithSso, logout } = useAuth();
    return (
      <div>
        <span data-testid="auth">{String(isAuthenticated)}</span>
        <span data-testid="role">{role ?? "none"}</span>
        <span data-testid="user">{userName}</span>
        <span data-testid="token">{token ? "yes" : "no"}</span>
        <button onClick={() => login("gerente@uaiagencia.com.br", "x")}>login</button>
        <button onClick={() => login("cliente@acme.com", "x")}>login-cliente</button>
        <button onClick={() => loginWithSso()}>sso</button>
        <button onClick={() => logout()}>logout</button>
      </div>
    );
  }

  function renderAuth() {
    return render(
      <MemoryRouter>
        <AuthProvider>
          <Harness />
        </AuthProvider>
      </MemoryRouter>
    );
  }

  async function click(label: string) {
    await act(async () => {
      fireEvent.click(screen.getByText(label));
    });
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => {
    cleanup();
    localStorage.clear();
  });

  describe("AuthContext — provider (modo stub default)", () => {
    it("começa deslogado", () => {
      renderAuth();
      expect(screen.getByTestId("auth").textContent).toBe("false");
      expect(screen.getByTestId("token").textContent).toBe("no");
    });

    it("login por credenciais autentica, emite token e infere a role", async () => {
      renderAuth();
      await click("login");
      expect(screen.getByTestId("auth").textContent).toBe("true");
      expect(screen.getByTestId("token").textContent).toBe("yes");
      expect(screen.getByTestId("role").textContent).toBe("AGENCY_MANAGER");
      expect(screen.getByTestId("user").textContent).toBe("gerente");
      expect(getToken()).not.toBeNull();
      expect(localStorage.getItem("uai_auth")).toBeNull();
    });

    it("infere CLIENT_VIEWER para email de cliente", async () => {
      renderAuth();
      await click("login-cliente");
      expect(screen.getByTestId("role").textContent).toBe("CLIENT_VIEWER");
    });

    it("loginWithSso autentica (demo stub)", async () => {
      renderAuth();
      await click("sso");
      expect(screen.getByTestId("auth").textContent).toBe("true");
      expect(screen.getByTestId("token").textContent).toBe("yes");
    });

    it("logout limpa a sessão e o token", async () => {
      renderAuth();
      await click("login");
      await click("logout");
      expect(screen.getByTestId("auth").textContent).toBe("false");
      expect(getToken()).toBeNull();
    });
  });
  ```
- [ ] Step: Run it, verify it FAILS.
  ```bash
  ./node_modules/.bin/vitest run src/contexts/AuthContext.test.tsx
  ```
  Expected: fails — current `AuthContext.login` is synchronous and (still) imports work, but the new test uses async `act` against a sync context; the precise failure is a TS/behavior mismatch once the impl below changes. Run after editing the impl if collection errors block — sequence: edit impl next, then this should pass. (If run before the impl edit, the assertions on `role`/`user` still pass because stub login is sync; proceed to the impl edit to make the async contract real.)
- [ ] Step: Minimal implementation — rewrite `src/contexts/AuthContext.tsx`.
  ```tsx
  import { createContext, useContext, useEffect, useState, ReactNode } from "react";
  import { useNavigate } from "react-router-dom";
  import { AuthSession, Role, isSessionValid } from "@/lib/auth/session";
  import * as tokenStore from "@/lib/auth/tokenStore";
  import { AUTH_MODE, AuthMode } from "@/lib/auth/config";
  import { resolveProvider } from "@/lib/auth/provider";

  interface AuthContextType {
    isAuthenticated: boolean;
    role: Role | null;
    userName: string;
    /** access token Bearer corrente (propagado ao intel pelo interceptor). */
    token: string | null;
    /** `stub` (dev) | `sso` (provider real uai-auth). */
    mode: AuthMode;
    /** login por credenciais (POST /api/auth/login no modo sso). */
    login: (email: string, password: string) => Promise<void>;
    /** entrada SSO "Entrar com uAI" — demo no stub; oculta no modo sso (MVP sem IdP redirect). */
    loginWithSso: () => Promise<void>;
    logout: () => Promise<void>;
  }

  const AuthContext = createContext<AuthContextType | null>(null);

  export function AuthProvider({ children }: { children: ReactNode }) {
    const navigate = useNavigate();
    // Verdade persistida = tokenStore (localStorage). Espelhamos em estado p/ reatividade.
    const [session, setSession] = useState<AuthSession | null>(() => tokenStore.getSession());
    const provider = resolveProvider(AUTH_MODE);

    // Mantém o estado em sincronia com mudanças externas (refresh-on-401, outras abas).
    useEffect(() => tokenStore.subscribe(setSession), []);

    const apply = (s: AuthSession, to: string) => {
      tokenStore.setSession(s);
      setSession(s);
      navigate(to);
    };

    const login = async (email: string, password: string) => {
      // F1: pós-login cai no vertical OOH (único no ar).
      apply(await provider.login(email, password), "/ooh");
    };

    const loginWithSso = async () => {
      apply(await provider.loginWithSso(), "/ooh");
    };

    const logout = async () => {
      await provider.logout(session?.token ?? null);
      tokenStore.clearSession();
      setSession(null);
      navigate("/login");
    };

    const value: AuthContextType = {
      isAuthenticated: isSessionValid(session),
      role: session?.user.role ?? null,
      userName: session?.user.name ?? "",
      token: session?.token ?? null,
      mode: AUTH_MODE,
      login,
      loginWithSso,
      logout,
    };

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
  }

  export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error("useAuth must be used within AuthProvider");
    return ctx;
  }
  ```
- [ ] Step: Remove the now-dead re-exports from `src/lib/auth/stubProvider.ts` (no consumer imports `AUTH_MODE`/`AuthMode`/`SSO_REDIRECT_DEFERRED` from stubProvider anymore). Delete these lines added in Task 1:
  ```ts
  // Modo de auth e flags migraram para `./config` (fonte única). Re-export por compat
  // enquanto os consumidores migram (removido na Task 6 quando o AuthContext usa config).
  export { AUTH_MODE, SSO_REDIRECT_DEFERRED } from "./config";
  export type { AuthMode } from "./config";
  ```
  (Leave the `import { AuthSession, AuthUser, Role } from "./session";` line intact.)
- [ ] Step: Verify no dangling imports of the removed symbols from stubProvider.
  ```bash
  grep -rn "from \"@/lib/auth/stubProvider\"\|from \"./stubProvider\"" src
  ```
  Expected: only imports of `stubLogin`, `stubSsoLogin`, `mintStubToken`, `decodeToken` — none of `AUTH_MODE`/`AuthMode`/`SSO_REDIRECT_DEFERRED`.
- [ ] Step: Run tests, verify PASS.
  ```bash
  ./node_modules/.bin/vitest run src/contexts/AuthContext.test.tsx src/components/portal/ProtectedLayout.test.tsx
  ```
  Expected: AuthContext (5) + ProtectedLayout (2) pass.
- [ ] Step: Commit.
  ```bash
  git add src/contexts/AuthContext.tsx src/contexts/AuthContext.test.tsx src/lib/auth/stubProvider.ts
  git commit -m "feat(auth): wire AuthContext to provider selection with async login/logout" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

### Task 7: Role mapping — ADMIN full access + sidebar

**Files:**
- Create `src/lib/auth/access.ts`
- Create `src/lib/auth/access.test.ts`
- Modify `src/components/portal/PortalSidebar.tsx` (role-gate verticals + real footer; lines 1-2, 84-89, 101, 144-150)
- Modify `src/components/portal/PortalSidebar.test.tsx` (login as ADMIN; footer assertions)

**Test:** `src/lib/auth/access.test.ts`

- [ ] Step: Write the failing test.
  ```ts
  import { describe, it, expect } from "vitest";
  import { canAccessVertical, roleLabel } from "./access";

  describe("access — ADMIN = acesso total", () => {
    it("ADMIN acessa todos os verticais", () => {
      expect(canAccessVertical("ADMIN", "ooh")).toBe(true);
      expect(canAccessVertical("ADMIN", "marketing")).toBe(true);
      expect(canAccessVertical("ADMIN", "qualquer-futuro")).toBe(true);
    });

    it("AGENCY_MANAGER acessa os verticais ativos", () => {
      expect(canAccessVertical("AGENCY_MANAGER", "ooh")).toBe(true);
      expect(canAccessVertical("AGENCY_MANAGER", "marketing")).toBe(true);
    });

    it("CLIENT_VIEWER fica restrito ao OOH (read-only)", () => {
      expect(canAccessVertical("CLIENT_VIEWER", "ooh")).toBe(true);
      expect(canAccessVertical("CLIENT_VIEWER", "marketing")).toBe(false);
    });

    it("sem role (deslogado) não acessa nada", () => {
      expect(canAccessVertical(null, "ooh")).toBe(false);
    });

    it("roleLabel mapeia rótulos pt-BR", () => {
      expect(roleLabel("ADMIN")).toBe("Admin");
      expect(roleLabel("AGENCY_MANAGER")).toBe("Gerente");
      expect(roleLabel("CLIENT_VIEWER")).toBe("Cliente");
      expect(roleLabel(null)).toBe("");
    });
  });
  ```
- [ ] Step: Run it, verify it FAILS.
  ```bash
  ./node_modules/.bin/vitest run src/lib/auth/access.test.ts
  ```
  Expected: fails to resolve `./access` (module does not exist).
- [ ] Step: Minimal implementation — create `src/lib/auth/access.ts`.
  ```ts
  // access — mapa de papéis -> acesso na UI. ADMIN = acesso total a todos os verticais.

  import { Role } from "./session";

  const LABELS: Record<Role, string> = {
    ADMIN: "Admin",
    AGENCY_MANAGER: "Gerente",
    CLIENT_VIEWER: "Cliente",
  };

  /** Rótulo pt-BR do papel para exibição (footer/sidebar). Vazio se deslogado. */
  export function roleLabel(role: Role | null): string {
    return role ? LABELS[role] : "";
  }

  /** Pode ver um vertical? ADMIN/AGENCY_MANAGER: todos. CLIENT_VIEWER: só `ooh`. Deslogado: nenhum. */
  export function canAccessVertical(role: Role | null, verticalId: string): boolean {
    if (!role) return false;
    switch (role) {
      case "ADMIN":
        return true;
      case "AGENCY_MANAGER":
        return true;
      case "CLIENT_VIEWER":
        return verticalId === "ooh";
      default:
        return false;
    }
  }
  ```
- [ ] Step: Run the access test, verify PASS.
  ```bash
  ./node_modules/.bin/vitest run src/lib/auth/access.test.ts
  ```
  Expected: 5 tests pass.
- [ ] Step: Write the failing sidebar test — rewrite `src/components/portal/PortalSidebar.test.tsx` to log in (the sidebar now role-gates).
  ```tsx
  import { describe, it, expect, afterEach } from "vitest";
  import { render, screen, within, cleanup } from "@testing-library/react";
  import { MemoryRouter } from "react-router-dom";
  import PortalSidebar, { verticals } from "./PortalSidebar";
  import { AuthProvider } from "@/contexts/AuthContext";
  import { setSession } from "@/lib/auth/tokenStore";
  import { Role } from "@/lib/auth/session";

  function loginAs(role: Role, name = "Marley Diniz") {
    setSession({
      token: "h.p.s",
      refreshToken: "r",
      user: { sub: "u", name, email: "marley@uai.com", role },
      expiresAt: Math.floor(Date.now() / 1000) + 3600,
    });
  }

  function renderSidebar() {
    return render(
      <MemoryRouter>
        <AuthProvider>
          <PortalSidebar />
        </AuthProvider>
      </MemoryRouter>
    );
  }

  afterEach(() => {
    cleanup();
    localStorage.clear();
  });

  describe("PortalSidebar — nav de verticais", () => {
    it("expõe o vertical OOH como 1º vertical da plataforma", () => {
      expect(verticals[0].id).toBe("ooh");
      expect(verticals[0].label).toBe("OOH");
    });

    it("ADMIN vê o OOH e o footer mostra o nome + rótulo 'Admin'", () => {
      loginAs("ADMIN");
      renderSidebar();

      const oohSection = document.querySelector('[data-vertical="ooh"]');
      expect(oohSection).not.toBeNull();
      expect(within(oohSection as HTMLElement).getByText("OOH")).toBeInTheDocument();

      const link = screen.getByRole("link", { name: /Planejamento OOH/ });
      expect(link).toHaveAttribute("href", "/ooh");

      expect(screen.getByText("Marley Diniz")).toBeInTheDocument();
      expect(screen.getByText("Admin")).toBeInTheDocument();
    });

    it("mantém o vertical Marketing nos dados, porém escondido (hidden)", () => {
      const marketing = verticals.find((v) => v.id === "marketing");
      expect(marketing).toBeDefined();
      expect(marketing?.hidden).toBe(true);

      loginAs("ADMIN");
      renderSidebar();

      expect(document.querySelector('[data-vertical="marketing"]')).toBeNull();
      expect(screen.queryByRole("link", { name: /Dashboard/ })).toBeNull();
    });

    it("renderiza apenas verticais acessíveis e não-hidden (só OOH no F1)", () => {
      loginAs("ADMIN");
      renderSidebar();
      const rendered = document.querySelectorAll("[data-vertical]");
      expect(rendered.length).toBe(1);
      expect(rendered[0].getAttribute("data-vertical")).toBe("ooh");
    });
  });
  ```
- [ ] Step: Run it, verify it FAILS.
  ```bash
  ./node_modules/.bin/vitest run src/components/portal/PortalSidebar.test.tsx
  ```
  Expected: fails — sidebar footer still renders hardcoded "Vivian Almeida"/"Gerente" (so `getByText("Marley Diniz")` and `getByText("Admin")` fail).
- [ ] Step: Minimal implementation — edit `src/components/portal/PortalSidebar.tsx`.
  - Replace the import line 2:
    ```tsx
    import { useAuth } from "@/contexts/AuthContext";
    ```
    with:
    ```tsx
    import { useAuth } from "@/contexts/AuthContext";
    import { canAccessVertical, roleLabel } from "@/lib/auth/access";
    ```
  - Replace lines 86-89:
    ```tsx
    const { logout, userName } = useAuth();
    const [open, setOpen] = useState(false);

    const initials = userName.slice(0, 2).toUpperCase() || "UA";
    ```
    with:
    ```tsx
    const { logout, userName, role } = useAuth();
    const [open, setOpen] = useState(false);

    const initials = userName.slice(0, 2).toUpperCase() || "UA";
    ```
  - Replace the filter on line 101:
    ```tsx
    {verticals.filter((vertical) => !vertical.hidden).map((vertical) => (
    ```
    with:
    ```tsx
    {verticals
      .filter((vertical) => !vertical.hidden && canAccessVertical(role, vertical.id))
      .map((vertical) => (
    ```
  - Replace the hardcoded footer (lines 147-150):
    ```tsx
    <div className="flex-1 min-w-0">
      <div className="text-sm font-medium text-white truncate">Vivian Almeida</div>
      <div className="text-[11px] text-white/40">Gerente</div>
    </div>
    ```
    with:
    ```tsx
    <div className="flex-1 min-w-0">
      <div className="text-sm font-medium text-white truncate">{userName || "Usuário"}</div>
      <div className="text-[11px] text-white/40">{roleLabel(role)}</div>
    </div>
    ```
- [ ] Step: Run tests, verify PASS.
  ```bash
  ./node_modules/.bin/vitest run src/lib/auth/access.test.ts src/components/portal/PortalSidebar.test.tsx src/components/portal/ProtectedLayout.test.tsx
  ```
  Expected: access (5) + PortalSidebar (4) + ProtectedLayout (2) pass. (ProtectedLayout logs in via `stubLogin` -> role `AGENCY_MANAGER` -> `canAccessVertical` true for `ooh`, so the OOH section still renders.)
- [ ] Step: Commit.
  ```bash
  git add src/lib/auth/access.ts src/lib/auth/access.test.ts src/components/portal/PortalSidebar.tsx src/components/portal/PortalSidebar.test.tsx
  git commit -m "feat(portal): role-gate sidebar verticals (ADMIN full access) and show real profile" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

### Task 8: LoginPage — async submit + error + hide SSO in real mode

**Files:**
- Modify `src/pages/LoginPage.tsx` (lines 1-15, 12-15, 67-90)
- Create `src/pages/LoginPage.test.tsx`

**Test:** `src/pages/LoginPage.test.tsx`

- [ ] Step: Write the failing test.
  ```tsx
  import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
  import { render, screen, fireEvent, cleanup, act } from "@testing-library/react";
  import { MemoryRouter } from "react-router-dom";

  const { loginMock } = vi.hoisted(() => ({ loginMock: vi.fn() }));
  vi.mock("@/contexts/AuthContext", () => ({
    // modo sso: o botão "Entrar com uAI" fica oculto; só a credencial entra.
    useAuth: () => ({ login: loginMock, loginWithSso: vi.fn(), mode: "sso" }),
  }));

  import LoginPage from "./LoginPage";

  function renderLogin() {
    return render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );
  }

  beforeEach(() => loginMock.mockReset());
  afterEach(() => cleanup());

  describe("LoginPage", () => {
    it("mostra erro quando o login falha (credencial inválida)", async () => {
      loginMock.mockRejectedValueOnce(new Error("401"));
      renderLogin();

      fireEvent.change(screen.getByPlaceholderText("seu@email.com"), { target: { value: "x@y.com" } });
      fireEvent.change(screen.getByPlaceholderText("••••••••"), { target: { value: "bad" } });
      await act(async () => {
        fireEvent.click(screen.getByRole("button", { name: "Entrar" }));
      });

      expect(await screen.findByText("Email ou senha inválidos.")).toBeInTheDocument();
    });

    it("chama login com as credenciais e não mostra erro no sucesso", async () => {
      loginMock.mockResolvedValueOnce(undefined);
      renderLogin();

      fireEvent.change(screen.getByPlaceholderText("seu@email.com"), {
        target: { value: "marley.diniz@gmail.com" },
      });
      fireEvent.change(screen.getByPlaceholderText("••••••••"), { target: { value: "pw" } });
      await act(async () => {
        fireEvent.click(screen.getByRole("button", { name: "Entrar" }));
      });

      expect(loginMock).toHaveBeenCalledWith("marley.diniz@gmail.com", "pw");
      expect(screen.queryByText("Email ou senha inválidos.")).toBeNull();
    });

    it("oculta o botão SSO quando mode != stub", () => {
      renderLogin();
      expect(screen.queryByRole("button", { name: "Entrar com uAI" })).toBeNull();
    });
  });
  ```
- [ ] Step: Run it, verify it FAILS.
  ```bash
  ./node_modules/.bin/vitest run src/pages/LoginPage.test.tsx
  ```
  Expected: fails — current `LoginPage` has a synchronous `handleSubmit` (no error state, no try/catch) so "Email ou senha inválidos." never appears, and the SSO button is always rendered (so the hide-in-sso assertion fails).
- [ ] Step: Minimal implementation — edit `src/pages/LoginPage.tsx`.
  - Replace lines 1-15:
    ```tsx
    import { useState, FormEvent } from "react";
    import { useAuth } from "@/contexts/AuthContext";
    import { Eye, EyeOff } from "lucide-react";

    export default function LoginPage() {
      const { login, loginWithSso, mode } = useAuth();
      const [email, setEmail] = useState("");
      const [password, setPassword] = useState("");
      const [showPass, setShowPass] = useState(false);
      const [remember, setRemember] = useState(false);
      const [error, setError] = useState("");
      const [submitting, setSubmitting] = useState(false);

      const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        if (!email || !password) return;
        setError("");
        setSubmitting(true);
        try {
          await login(email, password);
        } catch {
          setError("Email ou senha inválidos.");
        } finally {
          setSubmitting(false);
        }
      };
    ```
    (This replaces the original `import`s, the destructure, the four `useState`s, and the old synchronous `handleSubmit`.)
  - Replace the submit button + SSO block (lines 67-89):
    ```tsx
    <button type="submit" className="w-full py-3 rounded-xl font-heading font-semibold text-primary-foreground gradient-cta glow-primary hover:opacity-90 btn-press">
      Entrar
    </button>

    {/* Decisão B (2026-06-05): login unificado via SSO do uai-auth.
        uai-auth=RED ⇒ redirect real deferido; em modo stub o botão loga via stub. */}
    <div className="flex items-center gap-3">
      <span className="flex-1 h-px bg-white/10" />
      <span className="text-xs uppercase tracking-widest text-white/30">ou</span>
      <span className="flex-1 h-px bg-white/10" />
    </div>
    <button
      type="button"
      onClick={() => loginWithSso()}
      className="w-full py-3 rounded-xl font-heading font-semibold text-white border border-white/15 bg-white/5 hover:bg-white/10 btn-press"
    >
      Entrar com uAI
    </button>
    {mode === "stub" && (
      <p className="text-center text-[11px] text-white/30">
        SSO em modo demonstração — uai-auth indisponível.
      </p>
    )}
    ```
    with:
    ```tsx
    {error && (
      <p className="text-sm text-red-400" role="alert">
        {error}
      </p>
    )}
    <button
      type="submit"
      disabled={submitting}
      className="w-full py-3 rounded-xl font-heading font-semibold text-primary-foreground gradient-cta glow-primary hover:opacity-90 btn-press disabled:opacity-60"
    >
      {submitting ? "Entrando..." : "Entrar"}
    </button>

    {/* SSO ("Entrar com uAI") só no modo stub (demo). No modo sso/real do MVP não há
        IdP redirect — a credencial email/senha é o login real. */}
    {mode === "stub" && (
      <>
        <div className="flex items-center gap-3">
          <span className="flex-1 h-px bg-white/10" />
          <span className="text-xs uppercase tracking-widest text-white/30">ou</span>
          <span className="flex-1 h-px bg-white/10" />
        </div>
        <button
          type="button"
          onClick={() => loginWithSso()}
          className="w-full py-3 rounded-xl font-heading font-semibold text-white border border-white/15 bg-white/5 hover:bg-white/10 btn-press"
        >
          Entrar com uAI
        </button>
        <p className="text-center text-[11px] text-white/30">
          SSO em modo demonstração — uai-auth indisponível.
        </p>
      </>
    )}
    ```
- [ ] Step: Run tests, verify PASS.
  ```bash
  ./node_modules/.bin/vitest run src/pages/LoginPage.test.tsx
  ```
  Expected: 3 tests pass.
- [ ] Step: Commit.
  ```bash
  git add src/pages/LoginPage.tsx src/pages/LoginPage.test.tsx
  git commit -m "feat(portal): async login with error feedback and SSO button hidden in real mode" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

### Task 9: Production env cutover + full verification

**Files:**
- Modify `.env.production`

**Test:** full suite + lint (no new unit test — this is a config/verification task).

- [ ] Step: Edit `.env.production` to flip auth to the real provider. Replace its contents (preserving the intel comment block) with:
  ```dotenv
  # Build de PRODUÇÃO do portal (Vite lê .env.production no `npm run build`).
  # Em prod o front fala com o uai-ooh-intel via Nginx: VITE_INTEL_BASE_URL=/api/ooh
  # (o intelClient chama /api/lines ⇒ /api/ooh/api/lines ⇒ nginx faz strip ⇒ /api/lines no intel).
  VITE_INTEL_BASE_URL=/api/ooh
  # Auth REAL (uai-auth): provider real (login/refresh/logout/me). Same-origin via nginx,
  # que faz strip /api/auth/* ⇒ /api/v1/auth/* (uai-infra).
  VITE_AUTH_MODE=sso
  VITE_AUTH_BASE_URL=/api/auth
  ```
- [ ] Step: Run the FULL suite, verify PASS (tests force `VITE_INTEL_BASE_URL=""` and `.env.local` keeps `VITE_AUTH_MODE=stub`, so `.env.production` does not affect test runs).
  ```bash
  ./node_modules/.bin/vitest run
  ```
  Expected: all test files pass — original 28 files plus the 4 new files (`config.test.ts`, `realProvider.test.ts`, `provider.test.ts`, `access.test.ts`, `LoginPage.test.tsx`), with the modified `http.test.ts`, `tokenStore.test.ts`, `AuthContext.test.tsx`, `PortalSidebar.test.tsx`. Count: 33 files, ~187 tests (165 baseline + 3 config + 4 realProvider + 3 provider + 5 access + 3 LoginPage + 4 http + 3 tokenStore, minus none removed).
- [ ] Step: Run lint, verify clean.
  ```bash
  npm run lint
  ```
  Expected: exit 0, no errors (warnings from pre-existing `react-refresh` rules on UI files are tolerated only if they already existed at baseline; the new auth modules export only functions/constants/types and must produce no new errors).
- [ ] Step: Production build sanity check (confirms `.env.production` + TS compile end-to-end).
  ```bash
  npm run build
  ```
  Expected: `vite build` succeeds (`dist/` written, no TS errors).
- [ ] Step: Commit.
  ```bash
  git add .env.production
  git commit -m "chore(env): production auth cutover to real provider (VITE_AUTH_MODE=sso)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```
- [ ] Step: Push the branch (only when the user/cutover step authorizes the portal PR — this is step 5 of the cross-repo cutover, AFTER uai-auth + infra + intel are deployed).
  ```bash
  git push -u origin feat/uai-auth-portal-real-provider
  ```
  Then open a PR titled `feat(portal): real uai-auth provider + transparent refresh-on-401` describing: provider selection by `VITE_AUTH_MODE`, dual-token store, single-in-flight refresh queue, ADMIN full access, stub retained for dev.

---

## Cutover ordering note (cross-repo)

Per the design's rollout (section 9), this portal PR is **step 5** — merge/deploy only AFTER: (1) uai-auth repo+CI+image, (2) uai-infra compose/nginx `/api/auth/`/db/deploy/secrets, (3) uai-auth deployed and validated isolated, (4) intel flipped to introspection. The portal `deploy.yml` VPS step is still gated `if: false` (deferred to ship); enabling portal auto-deploy is an infra/ship concern, not part of this PR.
