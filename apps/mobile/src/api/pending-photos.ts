export interface PendingPhoto {
  owner: string;
  file: File;
  recordId: string;
  id: string;
  mutationId: string;
  registered: boolean;
  error?: string;
}

function openPhotos(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open("agrofacil-mobile", 1);
    request.onupgradeneeded = () => {
      request.result.createObjectStore("photos", { keyPath: ["owner", "id"] });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function usePhotos<T>(
  mode: IDBTransactionMode,
  action: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  const database = await openPhotos();
  try {
    return await new Promise((resolve, reject) => {
      const transaction = database.transaction("photos", mode);
      const request = action(transaction.objectStore("photos"));
      transaction.oncomplete = () => resolve(request.result);
      transaction.onabort = () => reject(transaction.error ?? request.error);
    });
  } finally {
    database.close();
  }
}

export function pendingPhotos(owner: string): Promise<PendingPhoto[]> {
  return usePhotos("readonly", (store) => store.getAll(IDBKeyRange.bound([owner], [owner, []])));
}

export function savePhoto(photo: PendingPhoto) {
  return usePhotos("readwrite", (store) => store.put(photo));
}

export function removePhoto(photo: PendingPhoto) {
  return usePhotos("readwrite", (store) => store.delete([photo.owner, photo.id]));
}
