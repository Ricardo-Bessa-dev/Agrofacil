// Busca os municípios na API, com a lista local da Bahia (@agrofacil/domain) como reserva.
import type { MunicipalityView } from "@agrofacil/api-types";
import { BAHIA_MUNICIPALITIES } from "@agrofacil/domain";
import { api, unwrap } from "./client";



/**
 * Retorna a lista completa de municípios da UF selecionada.
 * Para a Bahia (BA), garante todos os 417 municípios mesmo se a instância atual da API
 * ainda estiver com o limite antigo de 50 registros antes de reiniciar.
 */
export async function fetchMunicipalities(uf?: string): Promise<MunicipalityView[]> {
  const targetUf = (uf || "BA").toUpperCase();
  try {
    const list = await unwrap(
      api.GET("/municipalities", {
        params: { query: { uf: targetUf } },
      }),
    );
    if (targetUf === "BA" && list.length < BAHIA_MUNICIPALITIES.length) {
      return [...BAHIA_MUNICIPALITIES];
    }
    return list;
  } catch {
    return targetUf === "BA" ? [...BAHIA_MUNICIPALITIES] : [];
  }
}
