// Testa a renovação automática da sessão: em 401 o cliente renova o token e repete a chamada uma vez.
import { afterEach, describe, expect, it, vi } from "vitest";
import { api, setAccessToken, unwrap } from "./client";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

/** Mapeia cada chamada de fetch para uma resposta, guardando o Authorization usado. */
function mockFetch(responses: Response[]) {
  const authorizations: (string | null)[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const headers = input instanceof Request ? input.headers : new Headers(init?.headers);
    authorizations.push(headers.get("Authorization"));
    const next = responses.shift();
    if (!next) throw new Error("fetch chamado mais vezes que o esperado");
    return next;
  });
  vi.stubGlobal("fetch", fetchMock);
  return { fetchMock, authorizations };
}

afterEach(() => {
  vi.unstubAllGlobals();
  setAccessToken(null);
});

describe("renovação da sessão no painel", () => {
  it("renova o token expirado e repete a chamada com o token novo", async () => {
    setAccessToken("token-vencido");
    const { fetchMock, authorizations } = mockFetch([
      json(401, { code: "TOKEN_EXPIRED" }),
      json(200, { accessToken: "token-novo" }),
      json(200, { id: "u1", name: "Coordenação" }),
    ]);
    const me = await unwrap(api.GET("/me"));
    expect(me).toMatchObject({ id: "u1" });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(authorizations[0]).toBe("Bearer token-vencido");
    expect(authorizations[2]).toBe("Bearer token-novo");
  });

  it("não repete quando a renovação falha", async () => {
    setAccessToken("token-vencido");
    const { fetchMock } = mockFetch([json(401, { code: "TOKEN_EXPIRED" }), json(401, {})]);
    await expect(unwrap(api.GET("/me"))).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
