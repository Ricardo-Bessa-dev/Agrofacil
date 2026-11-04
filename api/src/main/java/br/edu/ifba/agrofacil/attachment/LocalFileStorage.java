// Armazenamento dos anexos no disco local, na pasta app.storage.dir.
package br.edu.ifba.agrofacil.attachment;

import br.edu.ifba.agrofacil.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {

  private final Path root;

  public LocalFileStorage(AppProperties props) {
    this.root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
  }

  @Override
  public void save(String key, byte[] content) {
    Path target = resolve(key);
    try {
      Files.createDirectories(target.getParent());
      // Grava num temporário e move: um arquivo pela metade nunca fica com o nome final.
      Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
      Files.write(tmp, content);
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public InputStream open(String key) {
    try {
      return Files.newInputStream(resolve(key));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void delete(String key) {
    try {
      Files.deleteIfExists(resolve(key));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A chave é gerada pelo servidor, mas confere que não escapa da pasta raiz. */
  private Path resolve(String key) {
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root)) {
      throw new IllegalArgumentException("Chave de arquivo inválida");
    }
    return path;
  }
}
