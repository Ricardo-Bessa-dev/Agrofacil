// Mostra as fotos de um registro de manejo: as que já estão na API e as que ainda esperam envio no aparelho.
import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { AttachmentView } from "@agrofacil/api-types";
import {
  fetchAttachmentContent,
  listPendingPhotosForRecord,
  OFFLINE_QUEUE_EVENT,
} from "../api/client";
import { mergeRecordPhotos, type RecordPhoto } from "../api/recordPhotos";

/** Lado da miniatura, em px: três por linha cabem numa tela de 360 px. */
const THUMB_SIZE = 96;
/** O arquivo de um anexo não muda depois de enviado (o SHA-256 é fixo): pode ficar em cache a sessão toda. */
const FILE_STALE_TIME = Infinity;
/** Acima do modal de detalhes do registro (zIndex 200). */
const VIEWER_Z_INDEX = 300;

type RecordPhotosProps = {
  recordId: string;
  attachments: AttachmentView[];
};

export function RecordPhotos({ recordId, attachments }: RecordPhotosProps) {
  const queryClient = useQueryClient();
  const [expandedUrl, setExpandedUrl] = useState<string | null>(null);
  const pendingKey = ["pending-photos", recordId];
  const pendingQuery = useQuery({
    queryKey: pendingKey,
    queryFn: () => listPendingPhotosForRecord(recordId),
  });

  // Quando a fila muda (ex.: a foto acabou de ser enviada), relê as fotos guardadas no aparelho.
  useEffect(() => {
    const refresh = () => void queryClient.invalidateQueries({ queryKey: ["pending-photos"] });
    window.addEventListener(OFFLINE_QUEUE_EVENT, refresh);
    return () => window.removeEventListener(OFFLINE_QUEUE_EVENT, refresh);
  }, [queryClient]);

  if (pendingQuery.isPending) return null;
  const photos = mergeRecordPhotos(attachments, pendingQuery.data ?? []);
  if (photos.length === 0) return null;

  return (
    <div style={{ backgroundColor: "var(--color-bg)", padding: "12px", borderRadius: "8px" }}>
      <div style={{ fontSize: "11px", color: "var(--color-text-muted)", marginBottom: 8 }}>
        Fotos ({photos.length})
      </div>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
        {photos.map((photo) => (
          <PhotoThumb key={photo.attachmentId} photo={photo} onOpen={setExpandedUrl} />
        ))}
      </div>

      {expandedUrl && (
        <button
          type="button"
          aria-label="Fechar foto"
          onClick={() => setExpandedUrl(null)}
          style={{
            position: "fixed",
            inset: 0,
            zIndex: VIEWER_Z_INDEX,
            border: 0,
            padding: 16,
            background: "rgba(0, 0, 0, 0.9)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <img
            src={expandedUrl}
            alt="Foto do registro em tamanho real"
            style={{ maxWidth: "100%", maxHeight: "100%", objectFit: "contain" }}
          />
        </button>
      )}
    </div>
  );
}

function PhotoThumb({ photo, onOpen }: { photo: RecordPhoto; onOpen: (url: string) => void }) {
  // Data URL (e não URL.createObjectURL): fica no cache do React Query sem precisar liberar memória à mão.
  const fileQuery = useQuery({
    queryKey: ["files", photo.attachmentId, photo.source],
    queryFn: async () => {
      if (photo.source === "local") return blobToDataUrl(photo.blob);
      return blobToDataUrl(await fetchAttachmentContent(photo.attachmentId));
    },
    enabled: photo.source !== "missing",
    staleTime: FILE_STALE_TIME,
    retry: 1,
  });

  if (photo.source === "missing") return <Placeholder text="Foto ainda não enviada" />;
  if (fileQuery.isError) return <Placeholder text="Sem internet para carregar" />;
  const url = fileQuery.data;
  if (!url) return <Placeholder text="Carregando..." />;

  return (
    <button
      type="button"
      onClick={() => onOpen(url)}
      aria-label="Ver foto em tamanho real"
      style={{ position: "relative", border: 0, padding: 0, background: "none" }}
    >
      <img
        src={url}
        alt="Foto anexada ao registro"
        style={{
          width: THUMB_SIZE,
          height: THUMB_SIZE,
          objectFit: "cover",
          borderRadius: 8,
          display: "block",
        }}
      />
      {photo.source === "local" && (
        <span
          style={{
            position: "absolute",
            left: 4,
            right: 4,
            bottom: 4,
            fontSize: 10,
            fontWeight: 600,
            color: "#fff",
            background: "rgba(0, 0, 0, 0.6)",
            borderRadius: 4,
            padding: "2px 4px",
          }}
        >
          Aguardando envio
        </span>
      )}
    </button>
  );
}

function Placeholder({ text }: { text: string }) {
  return (
    <div
      style={{
        width: THUMB_SIZE,
        height: THUMB_SIZE,
        borderRadius: 8,
        border: "1px dashed var(--color-text-muted)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        textAlign: "center",
        fontSize: 11,
        color: "var(--color-text-secondary)",
        padding: 6,
      }}
    >
      {text}
    </div>
  );
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error ?? new Error("Falha ao ler a foto."));
    reader.readAsDataURL(blob);
  });
}
