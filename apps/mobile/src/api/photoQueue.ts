// Guarda no IndexedDB as fotos que ainda não chegaram à API, para enviá-las quando houver internet.
// (O localStorage não serve: só guarda texto e tem cota pequena.)

const DB_NAME = "agrofacil-mobile";
const DB_VERSION = 1;
const STORE = "pending-photos";

/** Foto esperando envio. Os ids são gerados uma vez, para que reenviar não crie anexos repetidos. */
export interface PendingPhoto {
  attachmentId: string;
  /** mutationId fixo: a API ignora a mesma mutação enviada duas vezes. */
  mutationId: string;
  recordId: string;
  blob: Blob;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  /** ISO 8601 */
  createdAt: string;
  /** true depois que a API aceitou o anexo; falta só o arquivo. */
  registered: boolean;
}

let dbPromise: Promise<IDBDatabase> | null = null;

function openDb(): Promise<IDBDatabase> {
  dbPromise ??= new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      request.result.createObjectStore(STORE, { keyPath: "attachmentId" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => {
      dbPromise = null;
      reject(request.error);
    };
  });
  return dbPromise;
}

async function run<T>(
  mode: IDBTransactionMode,
  action: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, mode);
    const request = action(tx.objectStore(STORE));
    tx.oncomplete = () => resolve(request.result);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

export function savePendingPhoto(photo: PendingPhoto): Promise<IDBValidKey> {
  return run("readwrite", (store) => store.put(photo));
}

export function listPendingPhotos(): Promise<PendingPhoto[]> {
  if (typeof indexedDB === "undefined") return Promise.resolve([]);
  return run("readonly", (store) => store.getAll() as IDBRequest<PendingPhoto[]>);
}

export function deletePendingPhoto(attachmentId: string): Promise<undefined> {
  return run("readwrite", (store) => store.delete(attachmentId));
}

export async function countPendingPhotos(): Promise<number> {
  if (typeof indexedDB === "undefined") return 0;
  return run("readonly", (store) => store.count());
}
