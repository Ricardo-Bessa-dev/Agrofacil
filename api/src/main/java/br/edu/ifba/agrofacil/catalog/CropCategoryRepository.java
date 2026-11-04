// Acesso às categorias de cultura.
package br.edu.ifba.agrofacil.catalog;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropCategoryRepository extends JpaRepository<CropCategory, UUID> {}
