// Mostra os anexos (fotos, áudios e documentos) de um registro, baixando cada binário com o token do coordenador.
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import type { AttachmentView } from "@agrofacil/api-types";
import { downloadFile, fetchFileBlob } from "../api/client";

/** Lado da miniatura da foto, em px. */
const THUMB_SIZE = 160;
/** Os anexos não mudam depois de enviados (o hash SHA-256 é fixo), então podem ficar em cache a sessão toda. */
const BLOB_STALE_TIME = Infinity;

type AttachmentGalleryProps = {
  attachments: AttachmentView[];
};

export function AttachmentGallery({ attachments }: AttachmentGalleryProps) {
  if (attachments.length === 0) {
    return (
      <div className="af-empty-state" style={{ padding: "18px 10px" }}>
        Nenhum anexo enviado pelo agricultor.
      </div>
    );
  }
  return (
    <div style={{ display: "flex", flexWrap: "wrap", gap: 12 }}>
      {attachments.map((a) => (
        <AttachmentItem key={a.id} attachment={a} />
      ))}
    </div>
  );
}

function AttachmentItem({ attachment }: { attachment: AttachmentView }) {
  // O app registra o anexo antes de mandar o arquivo; enquanto uploaded=false, o binário ainda está no aparelho.
  if (!attachment.uploaded || !attachment.id) {
    return <Placeholder text="Aguardando envio do aparelho" />;
  }
  if (attachment.kind === "PHOTO" || attachment.kind === "AUDIO") {
    return <MediaPreview attachment={attachment} />;
  }
  return (
    <button
      type="button"
      className="af-btn af-btn--outline af-btn--sm"
      onClick={() => void downloadFile(contentPath(attachment.id!), `anexo-${attachment.id}`)}
    >
      Baixar documento
    </button>
  );
}

function MediaPreview({ attachment }: { attachment: AttachmentView }) {
  const [expanded, setExpanded] = useState(false);
  // Data URL (e não URL.createObjectURL): fica no cache do React Query sem precisar liberar memória à mão.
  const fileQuery = useQuery({
    queryKey: ["files", attachment.id],
    queryFn: async () => blobToDataUrl(await fetchFileBlob(contentPath(attachment.id!))),
    staleTime: BLOB_STALE_TIME,
  });
  const url = fileQuery.data;

  if (fileQuery.isError) return <Placeholder text="Não foi possível carregar o anexo" />;
  if (!url) return <Placeholder text="Carregando..." />;

  if (attachment.kind === "AUDIO") {
    return <audio controls src={url} style={{ maxWidth: 320 }} />;
  }
  return (
    <>
      <button
        type="button"
        onClick={() => setExpanded(true)}
        title="Ver a foto em tamanho real"
        style={{ border: 0, padding: 0, background: "none", cursor: "zoom-in" }}
      >
        <img
          src={url}
          alt="Foto anexada ao registro"
          style={{
            width: THUMB_SIZE,
            height: THUMB_SIZE,
            objectFit: "cover",
            borderRadius: "var(--af-radius-md)",
            border: "1px solid var(--af-border)",
          }}
        />
      </button>
      {expanded && (
        <div
          className="af-modal-backdrop"
          onClick={() => setExpanded(false)}
          style={{ cursor: "zoom-out" }}
        >
          <img
            src={url}
            alt="Foto anexada ao registro em tamanho real"
            style={{ maxWidth: "92vw", maxHeight: "92vh", borderRadius: "var(--af-radius-md)" }}
          />
        </div>
      )}
    </>
  );
}

function Placeholder({ text }: { text: string }) {
  return (
    <div
      style={{
        width: THUMB_SIZE,
        height: THUMB_SIZE,
        display: "grid",
        placeItems: "center",
        textAlign: "center",
        padding: 8,
        fontSize: "0.8rem",
        color: "var(--af-text-muted)",
        borderRadius: "var(--af-radius-md)",
        border: "1px dashed var(--af-border)",
      }}
    >
      {text}
    </div>
  );
}

function contentPath(id: string): string {
  return `/files/${id}/content`;
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}
