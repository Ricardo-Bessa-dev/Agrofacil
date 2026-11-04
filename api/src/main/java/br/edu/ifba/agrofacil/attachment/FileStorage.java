// Guarda os binários dos anexos fora de qualquer pasta pública (doc 2 §1.13).
package br.edu.ifba.agrofacil.attachment;

import java.io.InputStream;

/** Um {@code S3FileStorage} pode trocar a implementação sem mexer no resto. */
public interface FileStorage {

  /** Grava (ou substitui) o arquivo da chave. */
  void save(String key, byte[] content);

  /** Abre o arquivo para leitura; o chamador fecha o stream. */
  InputStream open(String key);

  void delete(String key);
}
