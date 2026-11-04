// Acesso aos municípios, com busca que ignora acentos.
package br.edu.ifba.agrofacil.catalog;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MunicipalityRepository extends JpaRepository<Municipality, Integer> {

  @Query(
      "select m from Municipality m where (:uf is null or m.uf = :uf)"
          + " and cast(function('f_unaccent', lower(m.name)) as String)"
          + " like cast(function('f_unaccent', lower(:pattern)) as String)"
          + " order by m.name")
  List<Municipality> search(String uf, String pattern, Limit limit);
}
