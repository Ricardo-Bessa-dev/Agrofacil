// Testa a junção das fotos da API com as fotos que ainda estão só no aparelho.
import { describe, expect, it } from "vitest";
import type { AttachmentView } from "@agrofacil/api-types";
import type { PendingPhoto } from "./photoQueue";
import { mergeRecordPhotos } from "./recordPhotos";

const attachment = (id: string, uploaded: boolean, kind: AttachmentView["kind"] = "PHOTO") =>
  ({ id, kind, uploaded, ownerType: "RECORD", ownerId: "r1" }) satisfies AttachmentView;

const pending = (attachmentId: string): PendingPhoto => ({
  attachmentId,
  mutationId: `m-${attachmentId}`,
  recordId: "r1",
  blob: new Blob(["x"], { type: "image/jpeg" }),
  contentType: "image/jpeg",
  sizeBytes: 1,
  sha256: "abc",
  createdAt: "2026-11-20T10:00:00Z",
  registered: false,
});

describe("mergeRecordPhotos", () => {
  it("usa a API para as fotos já enviadas", () => {
    expect(mergeRecordPhotos([attachment("a1", true)], [])).toEqual([
      { attachmentId: "a1", source: "server" },
    ]);
  });

  it("prefere a cópia local e não repete a foto já registrada na API", () => {
    const photos = mergeRecordPhotos([attachment("a1", false)], [pending("a1"), pending("a2")]);
    expect(photos.map((p) => [p.attachmentId, p.source])).toEqual([
      ["a1", "local"],
      ["a2", "local"],
    ]);
  });

  it("marca como ausente a foto registrada sem arquivo e sem cópia neste aparelho", () => {
    expect(mergeRecordPhotos([attachment("a1", false)], [])).toEqual([
      { attachmentId: "a1", source: "missing" },
    ]);
  });

  it("ignora áudios e documentos", () => {
    expect(mergeRecordPhotos([attachment("a1", true, "AUDIO")], [])).toEqual([]);
  });
});
