// Acesso às unidades de medida.
package br.edu.ifba.agrofacil.catalog;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MeasurementUnitRepository extends JpaRepository<MeasurementUnit, String> {

  List<MeasurementUnit> findAllByOrderBySortOrder();

  @Query("select u from MeasurementUnit u where u.syncSeq > :cursor order by u.syncSeq")
  List<MeasurementUnit> findChanges(long cursor, Limit limit);
}
