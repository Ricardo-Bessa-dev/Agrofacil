// Detalhe do grupo: os dados do grupo e a lista dos agricultores (doc 1 §3.7.3).
package br.edu.ifba.agrofacil.group;

import br.edu.ifba.agrofacil.user.FarmerListItem;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

public record GroupDetail(@JsonUnwrapped GroupView group, List<FarmerListItem> farmers) {}
