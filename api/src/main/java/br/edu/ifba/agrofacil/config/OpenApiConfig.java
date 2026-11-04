// Metadados do OpenAPI (Swagger) e o esquema de segurança bearerAuth (doc 2 §1.16).
package br.edu.ifba.agrofacil.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String BEARER = "bearerAuth";

  @Bean
  OpenAPI agrofacilOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Agrofácil API")
                .version("v1")
                .description(
                    "Caderno de campo digital de agricultores orgânicos. "
                        + "O comportamento está no doc 1, Parte 3."))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }

  /**
   * O springdoc compõe cada subtipo de uma união com {@code allOf: [união]}, o que cria um ciclo
   * nos tipos gerados pelo openapi-typescript. Aqui cada subtipo vira um objeto simples, com o
   * {@code type} fixo no valor do discriminador.
   */
  @Bean
  OpenApiCustomizer discriminatedUnions() {
    return api -> {
      @SuppressWarnings("rawtypes")
      Map<String, Schema> schemas = api.getComponents().getSchemas();
      UNIONS.forEach(union -> flattenSubtypes(schemas, union));
    };
  }

  /** Uniões discriminadas por {@code type} (doc 1 §3.9.1). */
  private static final List<String> UNIONS = List.of("RecordWrite", "RecordView");

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void flattenSubtypes(Map<String, Schema> schemas, String unionName) {
    Schema union = schemas.get(unionName);
    if (union == null || union.getDiscriminator() == null) {
      return;
    }
    union
        .getDiscriminator()
        .getMapping()
        .forEach((value, ref) -> {
          String name = ref.substring(ref.lastIndexOf('/') + 1);
          Schema subtype = schemas.get(name);
          if (subtype == null) {
            return;
          }
          if (subtype.getAllOf() != null) {
            subtype =
                ((List<Schema>) subtype.getAllOf())
                    .stream().filter(s -> s.get$ref() == null).findFirst().orElse(subtype);
            schemas.put(name, subtype);
          }
          subtype.setType("object");
          var typeProperty = new StringSchema();
          typeProperty.setEnum(List.of(value));
          subtype.addProperty("type", typeProperty);
          var required =
              new ArrayList<String>(
                  subtype.getRequired() == null ? List.of() : subtype.getRequired());
          if (!required.contains("type")) {
            required.add("type");
          }
          subtype.setRequired(required);
        });
  }
}
