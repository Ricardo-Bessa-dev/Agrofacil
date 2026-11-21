// Junta as fotos de um registro que já estão na API com as que ainda esperam envio no aparelho.
import type { AttachmentView } from "@agrofacil/api-types";
import type { PendingPhoto } from "./photoQueue";

/** Uma foto para exibir: vem da API (baixar pelo id) ou do próprio aparelho (blob local). */
export type RecordPhoto =
  | { attachmentId: string; source: "server" }
  | { attachmentId: string; source: "local"; blob: Blob }
  | { attachmentId: string; source: "missing" };

/**
 * Lista as fotos do registro, sem repetir. A cópia local tem prioridade: enquanto ela existir,
 * o arquivo pode ainda não ter chegado à API (uploaded=false).
 * @param attachments anexos do registro vindos da sincronização
 * @param pending fotos guardadas no aparelho para este registro
 */
export function mergeRecordPhotos(
  attachments: AttachmentView[],
  pending: PendingPhoto[],
): RecordPhoto[] {
  const local = new Map(pending.map((p) => [p.attachmentId, p]));
  const photos: RecordPhoto[] = [];
  for (const a of attachments) {
    if (!a.id || a.kind !== "PHOTO") continue;
    const localCopy = local.get(a.id);
    if (localCopy) {
      photos.push({ attachmentId: a.id, source: "local", blob: localCopy.blob });
      local.delete(a.id);
    } else {
      // uploaded=false sem cópia local: a foto foi tirada em outro aparelho e ainda não foi enviada.
      photos.push({ attachmentId: a.id, source: a.uploaded ? "server" : "missing" });
    }
  }
  // Fotos que nem foram registradas na API ainda (registro criado offline).
  for (const p of local.values()) {
    photos.push({ attachmentId: p.attachmentId, source: "local", blob: p.blob });
  }
  return photos;
}
