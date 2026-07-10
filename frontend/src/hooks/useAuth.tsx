// Access token lives in memory only (never localStorage) — persistence across
// reloads comes from the httpOnly REFRESH_TOKEN cookie via silent refresh on
// boot. This is the standard SPA-with-refresh-cookie pattern.
import {
  createContext,
  use,
  useCallback,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import * as api from "@/lib/api";
import type { User } from "@/lib/api";

interface AuthState {
  user: User | null;
  accessToken: string | null;
  booting: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (input: {
    username: string;
    nickname: string;
    password: string;
    email: string;
    emailCode: string;
  }) => Promise<void>;
  logout: () => Promise<void>;
  refreshMe: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [booting, setBooting] = useState(true);

  useEffect(() => {
    api
      .refreshToken()
      .then(({ user, accessToken }) => {
        setUser(user);
        setAccessToken(accessToken);
      })
      .catch(() => {
        // No valid refresh cookie — the visitor isn't signed in; not an error.
      })
      .finally(() => setBooting(false));
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const { user, accessToken } = await api.login(username, password);
    setUser(user);
    setAccessToken(accessToken);
  }, []);

  const register = useCallback(
    async (input: {
      username: string;
      nickname: string;
      password: string;
      email: string;
      emailCode: string;
    }) => {
      const { user, accessToken } = await api.register(input);
      setUser(user);
      setAccessToken(accessToken);
    },
    [],
  );

  const logout = useCallback(async () => {
    await api.logout();
    setUser(null);
    setAccessToken(null);
  }, []);

  const refreshMe = useCallback(async () => {
    if (!accessToken) return;
    const { user } = await api.getMe(accessToken);
    setUser(user);
  }, [accessToken]);

  return (
    <AuthContext
      value={{ user, accessToken, booting, login, register, logout, refreshMe }}
    >
      {children}
    </AuthContext>
  );
}

export function useAuth(): AuthState {
  const ctx = use(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
