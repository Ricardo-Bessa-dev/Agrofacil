# Agrofácil: guia da API

Este guia leva do projeto vazio até a API completa do contrato ([doc 1, Parte 3](1-arquitetura-e-contrato.md#parte-3-contrato-api-front)) e depois até ela rodando, com HTTPS e backup. Ele assume que a equipe conhece Java e o básico de Spring. O que é específico do Agrofácil vem com código.

> **Antes de começar:** leia o modelo e as RNs ([doc 1, Parte 2](1-arquitetura-e-contrato.md#parte-2-modelo-de-domínio-e-regras-de-negócio)), o contrato ([doc 1, Parte 3](1-arquitetura-e-contrato.md#parte-3-contrato-api-front)), a sincronização ([doc 1, Parte 4](1-arquitetura-e-contrato.md#parte-4-sincronização-offline)) e a segurança e LGPD ([doc 1, Parte 5](1-arquitetura-e-contrato.md#parte-5-segurança-e-lgpd)). Este guia mostra *como* implementar; o *quê* está lá.

**Os três documentos:** [doc 1: arquitetura, domínio e contrato](1-arquitetura-e-contrato.md) · **doc 2: guia da API (este)** · [doc 3: guia do front-end](3-guia-front-end.md)

## Sumário

- **[Parte 1. Construção da API (Spring Boot)](#parte-1-construção-da-api-spring-boot)**: [1.1 Pré-requisitos](#11-pré-requisitos) · [1.2 Criando o projeto](#12-criando-o-projeto) · [1.3 Configuração](#13-configuração) · [1.4 Estrutura de pacotes](#14-estrutura-de-pacotes) · [1.5 Banco e migrações (Flyway)](#15-banco-e-migrações-flyway) · [1.6 Entidades JPA](#16-entidades-jpa) · [1.7 Segurança e autenticação](#17-segurança-e-autenticação) · [1.8 Validação e erros](#18-validação-e-erros) · [1.9 Padrão de um recurso REST (exemplo: agricultores)](#19-padrão-de-um-recurso-rest-exemplo-agricultores) · [1.10 Registros de manejo](#110-registros-de-manejo) · [1.11 Sincronização](#111-sincronização) · [1.12 Rotinas, notificações, push e e-mail](#112-rotinas-notificações-push-e-e-mail) · [1.13 Arquivos](#113-arquivos) · [1.14 Relatórios](#114-relatórios) · [1.15 Visitas e documentos do SPG](#115-visitas-e-documentos-do-spg) · [1.16 OpenAPI e geração de tipos](#116-openapi-e-geração-de-tipos) · [1.17 Testes](#117-testes) · [1.18 Ordem de implementação sugerida](#118-ordem-de-implementação-sugerida) · [1.19 Checklist antes de cada PR](#119-checklist-antes-de-cada-pr)
- **[Parte 2. DDL de referência (migração V1)](#parte-2-ddl-de-referência-migração-v1)**
- **[Parte 3. Ambiente e implantação](#parte-3-ambiente-e-implantação)**: [3.1 Visão dos ambientes](#31-visão-dos-ambientes) · [3.2 Desenvolvimento local](#32-desenvolvimento-local) · [3.3 Demonstração: Docker Compose completo](#33-demonstração-docker-compose-completo) · [3.4 nginx: painel + API na mesma origem](#34-nginx-painel--api-na-mesma-origem) · [3.5 HTTPS para os celulares no campo: túnel](#35-https-para-os-celulares-no-campo-túnel) · [3.6 Backup e restauração](#36-backup-e-restauração) · [3.7 Distribuição do app (APK)](#37-distribuição-do-app-apk) · [3.8 Caminho para produção real](#38-caminho-para-produção-real) · [3.9 Variáveis de ambiente (`infra/.env.example`)](#39-variáveis-de-ambiente-infraenvexample)

## Parte 1. Construção da API (Spring Boot)

### 1.1 Pré-requisitos

| Ferramenta | Versão |
|---|---|
| JDK | 21 (Temurin) |
| Maven | O wrapper do projeto (`./mvnw`) |
| Docker + Docker Compose | Para o Postgres, o Mailpit e os testes (Testcontainers) |
| IDE | IntelliJ IDEA ou VS Code com o Extension Pack for Java |

### 1.2 Criando o projeto

Em [start.spring.io](https://start.spring.io), escolha:
- **Project:** Maven. **Language:** Java. **Spring Boot:** a versão 4.x estável mais recente. **Java:** 21. **Packaging:** Jar.
- **Group:** `br.edu.ifba`. **Artifact:** `agrofacil-api`. **Package name:** `br.edu.ifba.agrofacil`.
- **Dependências:** Spring Web, Spring Data JPA, PostgreSQL Driver, Flyway Migration, Validation, Spring Security, OAuth2 Resource Server, Java Mail Sender, Thymeleaf, Spring Boot Actuator, Testcontainers, Docker Compose Support.

Gere o projeto e mova-o para `api/` no monorepo. Depois acrescente ao `pom.xml`:

> **Já feito no esqueleto do repositório** (Spring Boot 4.1.2). O start.spring.io atual chama o Spring Web de `spring-boot-starter-webmvc` e cria um `*-test` para cada starter. As versões abaixo estão fixadas no `api/pom.xml`.

```xml
<!-- Histórico de alterações (ADR-10) -->
<dependency>
  <groupId>org.hibernate.orm</groupId>
  <artifactId>hibernate-envers</artifactId>
</dependency>
<!-- Swagger / OpenAPI: use a linha do springdoc compatível com o seu Boot (3.x para Boot 4, 2.x para Boot 3) -->
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>${springdoc.version}</version>
</dependency>
<!-- Argon2id para senhas e códigos -->
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcprov-jdk18on</artifactId>
  <version>${bouncycastle.version}</version>
</dependency>
<!-- Limite de tentativas -->
<dependency>
  <groupId>com.bucket4j</groupId>
  <artifactId>bucket4j_jdk17-core</artifactId>
  <version>${bucket4j.version}</version>
</dependency>
<!-- PDF a partir de HTML (fork mantido) -->
<dependency>
  <groupId>io.github.openhtmltopdf</groupId>
  <artifactId>openhtmltopdf-pdfbox</artifactId>
  <version>${openhtmltopdf.version}</version>
</dependency>
<!-- Detecção do tipo real de arquivo enviado -->
<dependency>
  <groupId>org.apache.tika</groupId>
  <artifactId>tika-core</artifactId>
  <version>${tika.version}</version>
</dependency>
```

Fixe cada versão em `<properties>`, usando a última estável. Rode `./mvnw dependency:tree` uma vez para conferir se não há conflito.

**Formatação (padrão do projeto):** use o plugin **Spotless** com o google-java-format e ligue o `spotless:check` na fase `verify`.

```xml
<plugin>
  <groupId>com.diffplug.spotless</groupId>
  <artifactId>spotless-maven-plugin</artifactId>
  <version>${spotless.version}</version>
  <configuration>
    <java>
      <googleJavaFormat/>
      <removeUnusedImports/>
    </java>
  </configuration>
  <executions>
    <execution><goals><goal>check</goal></goals></execution>
  </executions>
</plugin>
```

`./mvnw spotless:apply` formata tudo. O CI falha se alguém esquecer.

### 1.3 Configuração

`src/main/resources/application.yml`:

```yaml
# Configuração base da API; os valores sensíveis vêm de variáveis de ambiente.
spring:
  application.name: agrofacil-api
  # Lê o api/.env, se existir, para quem roda com ./mvnw spring-boot:run (§3.2).
  config.import: optional:file:.env[.properties]
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/agrofacil}
    username: ${DB_USER:agrofacil}
    password: ${DB_PASSWORD:agrofacil}
  jpa:
    open-in-view: false            # evita consultas escondidas na serialização
    hibernate.ddl-auto: validate   # o esquema é só do Flyway
    properties:
      hibernate.jdbc.time_zone: UTC
      org.hibernate.envers.audit_table_suffix: _aud
      org.hibernate.envers.store_data_at_delete: true
  flyway.locations: classpath:db/migration
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:1025}
    username: ${SMTP_USER:}
    password: ${SMTP_PASSWORD:}
  servlet.multipart:
    max-file-size: 10MB
    max-request-size: 11MB
  threads.virtual.enabled: true
  docker.compose:
    file: ../infra/docker-compose.dev.yml   # relativo à pasta api/
    lifecycle-management: start-only       # não derruba o banco quando a API para

server:
  servlet.context-path: /api/v1
  forward-headers-strategy: framework   # atrás do nginx/túnel

management:
  endpoints.web.exposure.include: health
  endpoint.health.probes.enabled: true
  health.mail.enabled: false   # SMTP fora do ar não deve marcar a API como caída; e-mail falho só vai para o log

app:
  timezone: America/Bahia
  public-url: ${APP_PUBLIC_URL:http://localhost:5173}   # usado nos links dos e-mails
  min-mobile-version: ${APP_MIN_MOBILE_VERSION:1.0.0}
  terms-version: "2026-10"
  jwt:
    secret: ${JWT_SECRET:}               # 32+ bytes em base64; nunca no git. Vazio = a API não sobe (@NotBlank)
    access-ttl: PT15M
  farmer-session:
    refresh-idle-ttl: P180D
  coordinator-session:
    refresh-idle-ttl: PT12H
    refresh-max-ttl: P7D
  storage.dir: ${STORAGE_DIR:./data/files}
  mail.from: ${MAIL_FROM:Agrofácil <nao-responda@agrofacil.app>}
  push.expo-access-token: ${EXPO_ACCESS_TOKEN:}
  bootstrap.coordinator-email: ${BOOTSTRAP_COORDINATOR_EMAIL:}

springdoc:
  api-docs.path: /openapi.json
  swagger-ui.path: /docs
```

Leia o bloco `app.*` com uma classe `@ConfigurationProperties(prefix = "app")` (record Java), nunca com `@Value` espalhado. Assim toda configuração fica num lugar só.

**Perfis:**
- `dev`: Swagger ligado e logs SQL opcionais.
- `prod`: Swagger desligado (`springdoc.api-docs.enabled: false`), cookies `Secure` e `JWT_SECRET` obrigatório.

**CORS:** **não é necessário.** No desenvolvimento, o Vite repassa `/api` para a API ([doc 3](3-guia-front-end.md)), e em produção o nginx serve o painel e a API na mesma origem. O app mobile não é navegador. Deixe o CORS desligado: é uma superfície de ataque a menos.

### 1.4 Estrutura de pacotes

Organize **por funcionalidade**, não por camada. Cada pacote tem seu controller, serviço, repositório, entidades e DTOs.

```text
br.edu.ifba.agrofacil
├── AgrofacilApplication.java
├── config/            # AppProperties, SecurityConfig, OpenApiConfig, ClockConfig, AsyncConfig
├── common/
│   ├── error/         # ApiException, ErrorCode, GlobalExceptionHandler
│   ├── persistence/   # BaseEntity, SyncableEntity, AuditRevision, AuditRevisionListener
│   ├── security/      # CurrentUser, CurrentUserProvider, AccessScope, RateLimiter, AppVersionFilter
│   ├── domain/        # Area, Quantity, GeoPoint, Address (embeddables), UnitConversion, Cpf
│   └── web/           # PageResponse, TraceIdFilter
├── auth/              # AuthController, FarmerActivationService, CoordinatorLoginService, TokenService…
├── association/
├── user/              # AppUser, Farmer, coordenadores, aparelhos
├── group/
├── property/          # Property, Plot, Worker
├── certification/     # + CertificationAlertJob
├── record/            # ManagementRecord e subtipos, RecordService, CompletenessRules
├── correction/
├── catalog/           # Crop, FarmInput, MeasurementUnit, Municipality
├── attachment/        # Attachment, FileStorage, FilesController
├── announcement/
├── notification/      # Notification, NotificationService, ExpoPushSender, EmailSender
├── sync/              # SyncController, SyncPushService, SyncPullService
├── dashboard/
├── report/
├── spg/
│   ├── visit/         # Visit, VisitService, VisitCycleCalculator, VisitorEligibility, VisitorScoring,
│   │                  # VisitorSuggestionService, VisitBriefBuilder, VisitMutationHandler, VisitAlertJob
│   └── people/        # Collaborator, CommitteeMember, MemberConflict
└── document/          # GeneratedDocument, DocumentService, DocumentDataAssembler, DocumentRenderer
```

Regras de dependência:
- `controller` → `service` → `repository`.
- O controller **nunca** acessa um repositório direto e **nunca** devolve uma entidade: só DTOs (records Java).
- Regras de negócio ficam em métodos que dá para testar sem Spring (`CompletenessRules`, `UnitConversion`, `CertificationStatusCalculator`, `VisitCycleCalculator`, `VisitorEligibility`, `VisitorScoring`).
- O serviço de registros é **um só**, usado pelo REST do painel **e** pelo push da sincronização. Assim não existem duas versões das regras.

Todo arquivo começa com um comentário de uma frase dizendo o que faz. Métodos públicos de serviço têm Javadoc.

### 1.5 Banco e migrações (Flyway)

```text
src/main/resources/db/migration/
├── V1__schema.sql                 # DDL da Parte 2
├── V2__audit_tables.sql           # tabelas do Envers
├── V3__seed_reference_data.sql    # unidades, categorias, culturas, insumos, roteiro da visita IN19-v1
├── V4__seed_municipalities_ba.sql # 417 municípios da Bahia
└── V5__seed_association.sql       # associação única (nome provisório)
```

- **Nunca edite uma migração já aplicada** em algum ambiente compartilhado. Crie uma `V6__...` nova.
- **Envers (V2):** para não escrever as tabelas `_aud` à mão, gere o DDL uma vez:
  1. num perfil temporário, ligue `spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create` e `...scripts.create-target=build/schema.sql`;
  2. suba a aplicação;
  3. copie só as tabelas `*_aud` e a `audit_revision` para a `V2`.
- **Municípios (V4):** gere com um script (fora do app) a partir da API do IBGE: `https://servicodados.ibge.gov.br/api/v1/localidades/estados/29/municipios`. Salve o SQL gerado no repositório, com um comentário dizendo de onde veio e em que data.
- **Insumos (V3):** comece pelos mais usados na região, com `regulatory_reference` apontando para o Anexo V (fertilizantes e corretivos) ou VII (controle de pragas) da Portaria 52/2021. A associação completa depois pelo painel.
- **Associação (V5):** use um UUID fixo e conhecido (ex.: `00000000-0000-0000-0000-000000000001`) para facilitar os testes.

### 1.6 Entidades JPA

#### 1.6.1 Classes base

```java
// Campos comuns de toda entidade sincronizável: id do cliente, versão, exclusão lógica e sync_seq.
@MappedSuperclass
public abstract class SyncableEntity {

  @Id private UUID id;

  // Long (e não long) para o Spring Data saber que a entidade é nova quando version == null,
  // mesmo com o id vindo do cliente. Assim o save() faz persist, e não merge.
  @Version private Long version;

  @Column(nullable = false, updatable = false) private Instant createdAt;
  @Column(nullable = false) private Instant updatedAt;
  private Instant deletedAt;

  // Preenchido pelo trigger do banco (ADR-09). Somente leitura para o JPA.
  @Column(insertable = false, updatable = false) private Long syncSeq;

  @PrePersist
  void onCreate() {
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void markDeleted(Instant when) {
    this.deletedAt = when;
  }
  // getters…
}
```

As medidas viram `@Embeddable`. Cada uso dá nomes às colunas com `@AttributeOverride`:

```java
// Área informada pelo usuário mais o valor normalizado em m² (RN14).
@Embeddable
public record AreaValue(BigDecimal value, String unit, BigDecimal m2) {}

// Uso no Planting:
@Embedded
@AttributeOverride(name = "value", column = @Column(name = "planted_area"))
@AttributeOverride(name = "unit", column = @Column(name = "planted_area_unit"))
@AttributeOverride(name = "m2", column = @Column(name = "planted_area_m2"))
private AreaValue plantedArea;
```

#### 1.6.2 Herança dos registros

```java
// Superclasse dos registros de manejo (Manejo no diagrama original), mapeada com herança JOINED.
@Entity
@Table(name = "management_record")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "record_type")
@Audited
public abstract class ManagementRecord extends SyncableEntity {

  @Column(insertable = false, updatable = false) private Long recordNumber;
  @Column(nullable = false) private UUID associationId;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) private AppUser farmer;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) private Property property;
  @ManyToOne(fetch = FetchType.LAZY) private Plot plot;
  @Column(nullable = false) private LocalDate performedOn;
  private String notes;
  @Embedded private GeoPointValue location;
  private UUID deviceId;
  @Enumerated(EnumType.STRING) private RecordSource source;
  // createdBy, updatedBy, clientCreatedAt…

  @Enumerated(EnumType.STRING) private Completeness completeness;
  @JdbcTypeCode(SqlTypes.ARRAY) private List<String> missingFields;

  /** Nomes (do contrato) dos campos recomendados ainda vazios. Ver doc 1 §2.5. */
  public abstract List<String> missingRecommendedFields();

  /** Recalcula a completude. É chamado pelo serviço antes de salvar (RN09). */
  public void refreshCompleteness() {
    missingFields = missingRecommendedFields();
    completeness = missingFields.isEmpty() ? Completeness.COMPLETE : Completeness.INCOMPLETE;
  }
}

// Plantio: ciclo de uma cultura numa área; aplicações e colheitas apontam para ele.
@Entity
@Table(name = "planting")
@PrimaryKeyJoinColumn(name = "record_id")
@DiscriminatorValue("PLANTING")
@Audited
public class Planting extends ManagementRecord {
  // campos do doc 1 §2.3.8…

  @Override
  public List<String> missingRecommendedFields() {
    var missing = new ArrayList<String>();
    if (plantedArea == null) missing.add("plantedArea");
    if (seedOrigin == null) missing.add("seedOrigin");
    if (expectedHarvestOn == null) missing.add("expectedHarvestOn");
    return missing;
  }
}
```

> **Atenção:** ao `@Audited` nas entidades com `@ManyToOne` para catálogos (`Crop`, `FarmInput`), acrescente `@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)` no relacionamento. Os catálogos não são auditados.

#### 1.6.3 Revisão de auditoria com "quem fez"

```java
// Revisão do Envers enriquecida com o usuário, o aparelho e a origem da alteração.
@Entity
@Table(name = "audit_revision")
@RevisionEntity(AuditRevisionListener.class)
public class AuditRevision {
  @Id @GeneratedValue @RevisionNumber private Long id;
  @RevisionTimestamp private Instant revisionAt;
  private UUID userId;
  private UUID deviceId;
  private String source; // MOBILE | WEB | SYSTEM
}

// Preenche a revisão a partir do usuário autenticado na requisição atual.
public class AuditRevisionListener implements RevisionListener {
  @Override
  public void newRevision(Object revision) {
    var rev = (AuditRevision) revision;
    CurrentUserProvider.currentOrNull().ifPresentOrElse(u -> {
      rev.setUserId(u.id());
      rev.setDeviceId(u.deviceId());
      rev.setSource(u.deviceId() != null ? "MOBILE" : "WEB");
    }, () -> rev.setSource("SYSTEM"));
  }
}
```

O endpoint `GET /records/{id}/history` usa o `AuditReader` (`AuditReaderFactory.get(entityManager)`) para listar as revisões, compara campo a campo com a anterior e monta o `changes[]`.

### 1.7 Segurança e autenticação

#### 1.7.1 Configuração do Spring Security

```java
// Regras de acesso HTTP e configuração do JWT (HS256) usado como token de acesso.
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain api(HttpSecurity http, AppVersionFilter appVersionFilter) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())   // sem sessão por cookie, exceto /auth/refresh (ver §1.7.4)
        .cors(cors -> cors.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/**", "/actuator/health/**", "/docs/**", "/openapi.json/**").permitAll()
            .requestMatchers("/sync/**", "/me/device").hasRole("FARMER")
            .requestMatchers("/me/**", "/files/**", "/catalog/units", "/municipalities", "/reports/field-notebook")
                .authenticated()
            .anyRequest().hasRole("COORDINATOR"))
        .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())))
        .addFilterBefore(appVersionFilter, BearerTokenAuthenticationFilter.class)
        .headers(h -> h
            .contentTypeOptions(Customizer.withDefaults())
            .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
        .build();
  }

  @Bean
  JwtDecoder jwtDecoder(AppProperties props) {
    return NimbusJwtDecoder.withSecretKey(props.jwt().secretKey()).build();
  }

  @Bean
  JwtEncoder jwtEncoder(AppProperties props) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(props.jwt().secretKey()));
  }

  private JwtAuthenticationConverter jwtAuthConverter() {
    var authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("role");
    authorities.setAuthorityPrefix("ROLE_");
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }
}
```

Personalize as respostas 401 e 403 (`authenticationEntryPoint` e `accessDeniedHandler`) para devolverem o Problem Details do contrato. Use `TOKEN_EXPIRED` quando o JWT expirou e `UNAUTHENTICATED` nos outros casos.

#### 1.7.2 Usuário atual e escopo

```java
// Dados do usuário autenticado extraídos do JWT.
public record CurrentUser(UUID id, Role role, UUID associationId, UUID deviceId) {
  public boolean isFarmer() {
    return role == Role.FARMER;
  }
}
```

- O `CurrentUserProvider` lê o `Jwt` do `SecurityContextHolder`.
- Os serviços recebem o `CurrentUser` como parâmetro: isso deixa explícito quem está agindo e facilita os testes.

**Toda busca por id passa pelo escopo.** Nunca use `findById` puro num controller ou serviço que atende usuários:

```java
// Busca registros respeitando o escopo do usuário: o agricultor só os próprios, o coordenador só os da associação.
public ManagementRecord findVisibleRecord(CurrentUser user, UUID id) {
  Optional<ManagementRecord> found = user.isFarmer()
      ? recordRepository.findByIdAndFarmerIdAndDeletedAtIsNull(id, user.id())
      : recordRepository.findByIdAndAssociationIdAndDeletedAtIsNull(id, user.associationId());
  return found.orElseThrow(() -> ApiException.notFound()); // 404 também para recurso alheio
}
```

#### 1.7.3 Ativação do agricultor

```java
// Troca CPF + código de acesso por uma sessão vinculada ao aparelho (ADR-03, RN03).
@Transactional(noRollbackFor = ApiException.class) // o contador de tentativas precisa ser gravado mesmo no erro
public SessionTokens activate(ActivateRequest req, String clientIp) {
  rateLimiter.check("activate:cpf:" + req.cpf(), 5, Duration.ofHours(1));
  rateLimiter.check("activate:ip:" + clientIp, 20, Duration.ofHours(1));

  var farmer = userRepository.findActiveFarmerByCpf(Cpf.normalize(req.cpf()))
      .orElseThrow(ApiException::invalidAccessCode);   // mesma resposta para CPF inexistente
  var code = accessCodeRepository.findLatestUsable(farmer.getId(), clock.instant())
      .orElseThrow(ApiException::invalidAccessCode);

  if (code.getFailedAttempts() >= MAX_CODE_ATTEMPTS) throw ApiException.accessCodeLocked();
  if (!passwordEncoder.matches(req.code(), code.getCodeHash())) {
    code.registerFailedAttempt();
    throw code.getFailedAttempts() >= MAX_CODE_ATTEMPTS
        ? ApiException.accessCodeLocked()
        : ApiException.invalidAccessCode();
  }

  code.markUsed(clock.instant());
  var device = deviceService.registerOrReactivate(farmer, req.device());
  return tokenService.startFarmerSession(farmer, device);
}
```

O código é gerado com `SecureRandom`: `String.format("%06d", random.nextInt(1_000_000))`. Só o hash é salvo, e o código em texto volta **uma única vez** na resposta ao coordenador.

#### 1.7.4 Tokens

| | Mobile | Web |
|---|---|---|
| Acesso | JWT de 15 min com `sub`, `role`, `assoc` e `dev` | JWT de 15 min com `sub`, `role` e `assoc` |
| Refresh | 32 bytes aleatórios em base64url, no corpo da resposta | O mesmo, no cookie `af_refresh` |
| Guardado no banco | `SHA-256(token)` em `refresh_token` | Idem |
| Validade | 180 dias sem uso (renovada a cada refresh) | 12 h sem uso, no máximo 7 dias desde o login |

Rotação (RN04), no `TokenService.refresh`:
1. Procure pelo hash.
2. Se não existe, está expirado ou está revogado → `REFRESH_INVALID`.
3. Se já tem `used_at`, é **reutilização**: revogue a família inteira e responda `REFRESH_INVALID`.
4. Se o aparelho está revogado → `DEVICE_REVOKED`.
5. Marque `used_at`, crie um token novo na mesma família e devolva.

> **Tolerância de rede:** se o app enviar o refresh e perder a resposta, a próxima tentativa parece reutilização e derruba a sessão, o que obrigaria o agricultor a pedir outro código. Para evitar isso:
> - aceite a reutilização se ela vier dentro de **30 s** do `used_at`, do **mesmo aparelho**;
> - nesse caso, revogue só o sucessor anterior (que o app nunca recebeu) e emita um novo;
> - depois dos 30 s, vale a regra normal e a família inteira é revogada.

Cookie do web (`ResponseCookie`): `httpOnly(true).secure(isProd).sameSite("Strict").path("/api/v1/auth").maxAge(Duration.ofDays(7))`. Em `/auth/refresh` e `/auth/logout`, quando o token vem por cookie, exija `X-Requested-With: agrofacil-web` (CSRF).

#### 1.7.5 Login do coordenador com 2FA
1. `login`: confira o limite de tentativas, procure o usuário e confira a senha com `passwordEncoder.matches`. Se o usuário não existir, rode um `matches` contra um hash falso para que o tempo de resposta seja igual. Depois crie o `login_challenge` com o hash do código e envie o e-mail **depois do commit** ([§1.12.3](#1123-e-mail)).
2. `verify`: confira a expiração, as tentativas (5) e o código. Marque o desafio como usado, grave `last_login_at` e crie a sessão web.
3. **Primeiro coordenador:** um `ApplicationRunner` verifica se não há coordenadores e se `app.bootstrap.coordinator-email` está definido. Se sim, cria o usuário e um token de **convite**, e envia o e-mail. Em `dev`, o link também vai para o log (o Mailpit mostra o e-mail em `http://localhost:8025`).

#### 1.7.6 Limite de tentativas

```java
// Limita tentativas por chave (CPF, e-mail, IP) em memória; suficiente para uma única instância da API.
@Component
public class RateLimiter {
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public void check(String key, int capacity, Duration period) {
    var bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
        .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
        .build());
    if (!bucket.tryConsume(1)) throw ApiException.rateLimited(period);
  }
}
```

Para obter o IP do cliente atrás do nginx ou do túnel, use `request.getRemoteAddr()` com `server.forward-headers-strategy: framework`. Só confie no `X-Forwarded-For` vindo do proxy.

#### 1.7.7 Versão mínima do app
O `AppVersionFilter` lê `X-App-Version`. Se a versão for menor que `app.min-mobile-version`, ele responde 426 `APP_UPDATE_REQUIRED`. Requisições sem o cabeçalho (o painel web) passam.

### 1.8 Validação e erros

**Duas camadas:**
1. **Bean Validation** nos DTOs de entrada: formato e tamanho (`@NotNull`, `@Size`, `@Positive`, `@Digits(integer = 9, fraction = 3)`, `@PastOrPresent`…).
2. **Regras de negócio** no serviço: datas relativas ao plantio, escopo, transições de estado. Elas lançam `ValidationException(List<FieldError>)` ou `ApiException(ErrorCode)`.

As mensagens em português ficam em `src/main/resources/ValidationMessages.properties`:

```properties
# Mensagens de validação exibidas ao usuário final; linguagem simples, sem termos técnicos.
agrofacil.required=Este campo precisa ser preenchido.
agrofacil.positive=O valor precisa ser maior que zero.
agrofacil.date.future=A data não pode ser depois de hoje.
agrofacil.date.beforePlanting=A data não pode ser antes do plantio.
agrofacil.cpf.invalid=CPF inválido. Confira os números.
```

```java
// Converte qualquer exceção em Problem Details (RFC 9457) no formato do contrato (doc 1 §3.2.5).
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(ApiException.class)
  ProblemDetail handleApi(ApiException ex, HttpServletRequest req) {
    var pd = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
    pd.setTitle(ex.code().title());
    pd.setType(URI.create("https://agrofacil.app/errors/" + ex.code().slug()));
    pd.setProperty("code", ex.code().name());
    pd.setProperty("errors", ex.fieldErrors());
    pd.setProperty("traceId", MDC.get("traceId"));
    return pd;
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    // Converte cada FieldError do Spring em { field, code, message } e responde 422 VALIDATION_ERROR.
    …
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ProblemDetail handleVersion() { … 409 VERSION_CONFLICT … }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception ex) {
    log.error("Erro inesperado", ex);   // o stack trace vai só para o log
    … 500 INTERNAL_ERROR, sem detalhes …
  }
}
```

O `TraceIdFilter` gera um id curto por requisição, coloca no `MDC` (para aparecer em todos os logs) e no cabeçalho `X-Trace-Id`.

### 1.9 Padrão de um recurso REST (exemplo: agricultores)

```java
// Endpoints do painel para cadastro e gestão de agricultores (doc 1 §3.7.4).
@RestController
@RequestMapping("/farmers")
@RequiredArgsConstructor
public class FarmerController {
  private final FarmerService farmerService;
  private final CurrentUserProvider currentUser;

  @GetMapping
  public PageResponse<FarmerListItem> list(FarmerFilter filter, Pageable pageable) {
    return farmerService.list(currentUser.get(), filter, pageable);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public FarmerView create(@Valid @RequestBody FarmerWrite body) {
    return farmerService.create(currentUser.get(), body);
  }

  @PostMapping("/{id}/access-codes")
  @ResponseStatus(HttpStatus.CREATED)
  public AccessCodeResponse newAccessCode(@PathVariable UUID id) {
    return accessCodeService.generate(currentUser.get(), id);
  }
}
```

- **Listas paginadas:** use `PageResponse<T>(List<T> content, PageMeta page)`, criado a partir do `Page` do Spring. Fica idêntico ao contrato e não depende do formato interno do Spring.
- **Filtros:** um record (`FarmerFilter`) ligado aos query params e transformado em `Specification` do JPA.
- **Busca sem acento:** crie a extensão `unaccent` na migração e uma função imutável `f_unaccent(text)`. Filtre com `f_unaccent(lower(name)) like f_unaccent(lower(:q))`.
- **CPF:** a classe `Cpf` (em `common/domain`) normaliza (só dígitos), valida os dígitos verificadores e mascara (`***.456.789-**`). Ela tem testes unitários.

### 1.10 Registros de manejo

O `RecordService` concentra tudo e é usado tanto pelo `RecordController` quanto pelo `SyncPushService`.

```java
// Cria e atualiza registros de manejo aplicando as regras RN09–RN17; usado pelo painel e pela sincronização.
@Service
@RequiredArgsConstructor
public class RecordService {

  /**
   * Cria ou atualiza o registro com o id informado (upsert idempotente).
   *
   * @param origin MOBILE (push) ou WEB (painel)
   * @return o registro salvo, já com a completude recalculada
   */
  @Transactional
  public ManagementRecord upsert(CurrentUser user, RecordWrite write, RecordSource origin) {
    var existing = recordRepository.findById(write.id());
    if (existing.isPresent() && existing.get().isDeleted()) throw ApiException.notFound();
    existing.ifPresent(r -> ensureCanEdit(user, r));         // RN16
    existing.ifPresent(r -> ensureSameType(r, write));       // TYPE_CHANGE_NOT_ALLOWED

    var record = existing.orElseGet(() -> recordFactory.newFor(write.type()));
    var context = contextResolver.resolve(user, write);      // propriedade, área e plantio, todos no escopo (RN11)
    recordAssembler.apply(write, record, context);           // copia os campos, converte as áreas (RN14)
    validator.validate(record, context);                     // RN10, RN11, RN28 → ValidationException
    record.refreshCompleteness();                            // RN09
    applyPlantingCycle(record);                              // RN12
    var saved = recordRepository.save(record);
    sideEffects.afterUpsert(user, saved, existing.isPresent()); // pedidos de correção, notificações (RN16, RN17)
    return saved;
  }
}
```

Pontos de atenção:
- **Conversão de áreas** (`UnitConversion.toSquareMeters(value, unit, association.tarefaM2)`) é uma função pura com testes. Ela rejeita unidades que não são de área.
- **Colheita final (RN12):** depois de salvar uma colheita, recalcule o plantio: se existir alguma colheita final não excluída, o plantio fica `FINISHED`, com `finished_on` igual à data da colheita final mais recente; senão, fica `ACTIVE`. Faça o mesmo ao excluir uma colheita.
- **JSON polimórfico:** os DTOs são `sealed interface RecordView permits PlantingView, …` com `@JsonTypeInfo(use = NAME, include = EXISTING_PROPERTY, property = "type", visible = true)` e `@JsonSubTypes`. No springdoc, anote com `@Schema(oneOf = {...}, discriminatorProperty = "type", discriminatorMapping = {...})` para o OpenAPI gerar uma união discriminada que o `openapi-typescript` entende. Faça o mesmo para `RecordWrite`.
- **Lista do painel:** `RecordListItem` vem de uma consulta de projeção (JPQL com `new` ou interface de projeção) com `left join` nos catálogos, para resolver o `subject` sem N+1.
- **Itens sem catálogo (RN22):** é uma consulta agrupando `crop_other_name` e `input_other_name` (normalizados com `lower(trim())`). O `resolve` faz um `update` em massa e chama `refreshCompleteness` se precisar.

### 1.11 Sincronização

#### 1.11.1 Push

```java
// Processa o lote de mutações do app; cada mutação roda numa transação própria (doc 1 §4.7).
@Service
@RequiredArgsConstructor
public class SyncPushService {
  private final TransactionTemplate tx;   // propagação REQUIRES_NEW
  private final SyncMutationRepository mutations;
  private final Map<String, MutationHandler> handlers; // "record" → RecordMutationHandler, …

  public PushResponse push(CurrentUser user, PushRequest req) {
    deviceService.touch(user, req.deviceId());
    var results = req.mutations().stream().map(m -> processOne(user, m)).toList();
    return new PushResponse(results, clock.instant());
  }

  private MutationResult processOne(CurrentUser user, Mutation m) {
    var previous = mutations.findById(m.mutationId());
    if (previous.isPresent()) return previous.get().response();     // idempotência
    try {
      return tx.execute(status -> {
        var result = handlerFor(m).apply(user, m);                   // usa RecordService etc.
        mutations.save(SyncMutation.of(user, m, result));            // mesma transação do dado
        return result;
      });
    } catch (ApiException e) {                                       // regra de negócio → REJECTED
      var rejected = MutationResult.rejected(m, e);
      tx.executeWithoutResult(s -> mutations.save(SyncMutation.of(user, m, rejected)));
      return rejected;
    }
    // Outras exceções sobem e viram 500: o app repete o lote inteiro, o que é seguro.
  }
}
```

- O controller **não** é `@Transactional`.
- Os `MutationHandler`s convertem o `data` (JSON) no DTO certo com o `ObjectMapper` e validam com o `Validator` do Bean Validation, manualmente, porque o `@Valid` não alcança o `data` genérico.
- Notificações e push gerados durante o push só saem **depois do commit** de cada mutação ([§1.12.3](#1123-e-mail)).

#### 1.11.2 Pull
- Um método por tabela no `SyncPullService`, todos com a mesma forma: `findChanges(scope, cursor, limit)` ordenado por `sync_seq`. Cuidado: consultas por `sync_seq` **não** filtram `deleted_at`, porque as excluídas viram lápides.
- Converta tudo para os **mesmos DTOs** usados no painel (`PropertyView`, `RecordView`…). Assim o mobile e o web enxergam o mesmo formato.
- O cursor é o `sync_seq` como texto. Trate o cursor recebido como opaco: se não for um número válido, responda `reset: true`.
- `@Transactional(readOnly = true)` no método inteiro, para a leitura ser consistente.

### 1.12 Rotinas, notificações, push e e-mail

#### 1.12.1 Alertas de certificação (RN20)

```java
// Rotina diária que avisa agricultor e coordenadores sobre certificações perto de vencer ou vencidas.
@Component
@RequiredArgsConstructor
public class CertificationAlertJob {

  @Scheduled(cron = "0 0 7 * * *", zone = "America/Bahia")
  public void run() {
    var today = LocalDate.now(clock);
    for (var cert : certificationRepository.findCurrentActive()) {
      var thresholds = cert.association().alertDays();       // ex.: [60, 30, 15]
      AlertPlanner.nextAlert(cert.expiresOn(), today, thresholds, cert.sentAlertDays())
          .ifPresent(days -> alertService.send(cert, days)); // grava certification_alert + notifica
    }
  }
}
```

O `AlertPlanner.nextAlert` é uma **função pura**, testada com vários cenários:
- `daysToExpire = expiresOn - today`.
- Se `daysToExpire < 0` e o alerta `0` não foi enviado → envia o alerta "venceu" (`days = 0`).
- Senão, pegue o **menor** limite `N >= daysToExpire` ainda não enviado. Envie só ele, e marque os limites maiores como enviados, para não mandar três alertas de uma vez depois de alguns dias sem a rotina rodar.

Habilite `@EnableScheduling` numa classe de configuração. Como há uma só instância da API, não é preciso lock distribuído. Os avisos das visitas do SPG ficam numa rotina própria, às 07:05 ([§1.15.5](#1155-rotina-de-avisos-rn30)).

#### 1.12.2 Notificações e push
- `NotificationService.notify(userIds, type, title, body, data)` grava as linhas de `notification` e publica um evento `NotificationsCreated`.
- O `ExpoPushSender` escuta com `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`. Ele busca os `push_token` dos aparelhos ativos e envia em lotes de até 100 para `POST https://exp.host/--/api/v2/push/send`:

```json
[{ "to": "ExponentPushToken[xxxx]", "title": "Sua certificação vence em 30 dias",
   "body": "Sítio Boa Vista: vence em 23/11. Fale com a associação para renovar.",
   "data": { "type": "CERTIFICATION_EXPIRING", "certificationId": "..." }, "sound": "default" }]
```

- Se a resposta trouxer `DeviceNotRegistered`, limpe o `push_token` desse aparelho.
- Falha no push **não** é erro de negócio: registre no log e siga. A notificação continua no app (pull).

#### 1.12.3 E-mail
- O `EmailSender` usa o `JavaMailSender`, com templates Thymeleaf de texto simples em `templates/mail/`.
- Use os mesmos `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, para nunca enviar e-mail de algo que deu rollback.
- E-mails da API: código 2FA, convite, redefinição de senha e alertas de certificação para os coordenadores. Também para o agricultor, se ele tiver e-mail.
- Em desenvolvimento, todos caem no **Mailpit** ([Parte 3](#parte-3-ambiente-e-implantação)).

### 1.13 Arquivos

```java
// Guarda os binários dos anexos no disco local, fora de qualquer pasta pública.
public interface FileStorage {
  void save(String key, InputStream content);
  InputStream open(String key);
  void delete(String key);
}
```

- **Chave do arquivo:** `yyyy/MM/<attachmentId>`, nunca o nome enviado pelo usuário.
- **No `PUT /files/{id}/content`:**
  1. confira o escopo;
  2. leia o corpo com limite de tamanho;
  3. detecte o tipo real com `new Tika().detect(bytes)` e compare com a lista do contrato;
  4. calcule o SHA-256 e compare com o informado;
  5. grave;
  6. marque `uploaded_at`.

  Se já existe um arquivo com o mesmo hash, responda `204` sem regravar (idempotência).
- **Download:** `ResponseEntity<StreamingResponseBody>` com `Content-Type` do metadado, `Cache-Control: private` e `Content-Disposition: inline`.
- **Futuro:** um `S3FileStorage` (MinIO ou provedor de nuvem) troca a implementação sem mexer no resto.

### 1.14 Relatórios
- **PDF:**
  1. monte um modelo de dados (record Java) com tudo o que o relatório mostra;
  2. renderize um template Thymeleaf (`templates/report/field-notebook.html`) com CSS simples para impressão (A4, fonte de 11 pt, tabelas com borda);
  3. converta com `PdfRendererBuilder` (openhtmltopdf);
  4. embuta uma fonte com acentos, como Noto Sans, com `useFont`.
- **CSV:** escreva à mão com `;`, BOM UTF-8 (`﻿`) e números formatados com `Locale.of("pt", "BR")`. Use aspas em campos com `;`, aspas ou quebras de linha.
- **Conteúdo do caderno de campo:** cabeçalho com associação, OPAC, agricultor, propriedade, áreas com status e certificação atual. Depois, os plantios do período, cada um com suas aplicações e colheitas. Por fim, as compras de insumo, as saídas/vendas e o rodapé com "Gerado pelo Agrofácil em …" e o número da página.

### 1.15 Visitas e documentos do SPG

Leia antes a [doc 1, Parte 6](1-arquitetura-e-contrato.md#parte-6-certificação-participativa-spg-visitas-e-documentos) (processo) e a [doc 1 §3.15](1-arquitetura-e-contrato.md#315-visitas-do-spg) (contrato). As regras são as RN29–RN40.

#### 1.15.1 Situação das visitas (RN29)

A regra é uma **função pura**, testada sem Spring e sem banco:

```java
// Calcula o ciclo, o período de visitas e a situação de uma propriedade (RN29).
public final class VisitCycleCalculator {

  private static final int URGENT_DAYS = 30;

  /**
   * @param certification certificação atual, ou null se a propriedade não tiver
   * @param doneVisits    visitas DONE da propriedade (tipo e data)
   * @param settings      visit_window_days e peer_visits_per_year da associação
   */
  public static VisitCycleStatus calculate(
      CertificationDates certification, List<VisitDone> doneVisits, VisitSettings settings, LocalDate today) {
    var from = certification != null ? certification.issuedOn() : today.minusMonths(12);
    var to = certification != null ? certification.expiresOn() : today;
    var windowOpensOn = certification != null ? to.minusDays(settings.visitWindowDays()) : null;
    var windowOpen = windowOpensOn != null && !today.isBefore(windowOpensOn);

    long peerDone = countInCycle(doneVisits, VisitType.PEER, from, to);
    long verificationDone = countInCycle(doneVisits, VisitType.VERIFICATION, from, to);
    boolean complete = peerDone >= settings.peerVisitsPerYear() && verificationDone >= 1;

    var daysToExpire = certification != null ? ChronoUnit.DAYS.between(today, to) : null;
    boolean urgent = windowOpen && verificationDone == 0 && daysToExpire <= URGENT_DAYS;

    var state = complete ? State.UP_TO_DATE : urgent ? State.URGENT : State.PENDING;
    return new VisitCycleStatus(from, to, windowOpensOn, windowOpen, daysToExpire, peerDone, verificationDone, state);
  }
}
```

O serviço junta as contagens de visitas `PLANNED` e os `warnings` (sem localização, grupo com menos de 3) e devolve o `PropertyVisitStatus`. A lista de planejamento (`GET /visits/status`) usa **uma** consulta agregada por propriedade (`count(*) filter (where ...)`), não uma consulta por linha.

#### 1.15.2 Sugestão de visitadores (RN32, RN33)

Separe em três partes. Assim a regra fica testável e o motivo de cada sugestão sai pronto em português:
1. **`VisitorEligibility`** (função pura): aplica as regras fixas da RN32 e devolve `eligible` ou `excluded(motivo)`. A mesma classe valida o `POST /visits` e o `PATCH`. A sugestão e a gravação nunca divergem.
2. **`VisitorScoring`** (função pura): calcula a pontuação e os motivos.
3. **`VisitorSuggestionService`**: busca os candidatos, as propriedades deles, o histórico de visitas de 12 meses e a agenda de 30 dias **em lote**, e chama as duas funções.

```java
// Ordena os visitadores elegíveis por distância, rodízio e equilíbrio (doc 1 §6.4).
public final class VisitorScoring {

  static final double SAME_MUNICIPALITY_KM = 15;
  static final double OTHER_MUNICIPALITY_KM = 50;
  static final double UNKNOWN_KM = 60;
  static final double VISITED_RECENTLY_PENALTY = 30;   // rodízio
  static final double RECIPROCAL_PENALTY = 30;         // evita a troca "eu visito você, você me visita"
  static final double PER_PLANNED_VISIT_PENALTY = 10;  // equilíbrio

  public static ScoredCandidate score(Candidate c, VisitedProperty target) {
    var reasons = new ArrayList<String>();
    var distance = nearestDistanceKm(c.properties(), target);   // Haversine; vazio se faltar GPS
    double km;
    if (distance.isPresent()) {
      km = distance.getAsDouble();
      reasons.add("a %s km".formatted(formatKm(km)));
    } else {
      km = estimateByMunicipality(c, target);
      reasons.add("a ~%s km (sem localização)".formatted(formatKm(km)));
    }
    double score = km;
    if (c.visitedTargetInLast12Months()) {
      score += VISITED_RECENTLY_PENALTY;
      reasons.add("já visitou esta roça no último ano");
    } else {
      reasons.add("não visitou esta roça no último ano");
    }
    if (c.wasVisitedByTargetFarmerInLast12Months()) {
      score += RECIPROCAL_PENALTY;
      reasons.add("foi visitado por este agricultor no último ano");
    }
    score += PER_PLANNED_VISIT_PENALTY * c.plannedVisitsNext30Days();
    if (c.plannedVisitsNext30Days() > 0) {
      reasons.add("%d visita(s) marcada(s)".formatted(c.plannedVisitsNext30Days()));
    }
    return new ScoredCandidate(c, distance.isEmpty(), km, score, reasons);
  }
}
```

- **Distância:** a fórmula de Haversine, com raio da Terra de 6371 km, em `GeoMath.haversineKm`. Para o volume do projeto (dezenas de candidatos), calcular em Java é suficiente. Não precisa de PostGIS.
- **Empate:** desempate pelo nome, para a sugestão ser estável (a mesma entrada gera sempre a mesma lista).
- **Amostragem:** `samplingMinimum = (int) Math.ceil(Math.sqrt(agricultoresAtivosDoGrupo))`.
- **Impedimento:** o motivo devolvido é sempre genérico ("impedimento cadastrado com José"), nunca o tipo (RN40).

#### 1.15.3 Resumo da visita (`brief`, RN36)

- **`VisitBriefBuilder.build(propertyId, today)`:** monta o `VisitBrief` com consultas agregadas sobre `management_record` dos últimos 12 meses (contagem e última data por insumo; soma por cultura e unidade nas colheitas e saídas). Os nomes já vêm resolvidos, incluindo o "Outro".
- **Quando grava:**
  - ao criar ou alterar a visita;
  - pela rotina diária, para as visitas `PLANNED` dos próximos 14 dias.

  Só grave se o JSON mudou: uma escrita desnecessária muda o `sync_seq` e força o pull nos celulares.
- **Quem recebe:** o `brief` vai no DTO da visita **só** para os participantes e para o coordenador. Para o agricultor visitado, o mesmo DTO vai com `brief = null`.

#### 1.15.4 Resultado pelo app (mutação `visit`)

O `VisitMutationHandler` segue o mesmo padrão dos outros handlers ([§1.11.1](#1111-push)):
1. Carrega a visita e confere se o usuário é o `recorder` e se ela não está `CANCELLED`. Senão → `REJECTED NOT_FOUND`.
2. Confere o prazo de 7 dias depois de `done_on` (`VISIT_NOT_EDITABLE`).
3. Chama `VisitService.recordResult(visit, write, Source.MOBILE, user)`, **o mesmo método** do `PUT /visits/{id}/result`. Ele valida o roteiro (`CHECKLIST_INCOMPLETE`), grava e muda para `DONE`.
4. Notifica os coordenadores com `VISIT_RESULT_RECORDED` depois do commit.

Os anexos da visita usam o fluxo normal de anexos (`ownerType = VISIT`). O escopo do upload aceita o responsável pelo registro.

#### 1.15.5 Rotina de avisos (RN30)

O `VisitAlertJob` roda às 07:00, logo depois do `CertificationAlertJob`, e usa a tabela `alert_log` para não repetir avisos:

```java
// Avisos diários do SPG: período de visitas aberto, véspera da visita e resultado atrasado.
@Scheduled(cron = "0 5 7 * * *", zone = "America/Bahia")
public void run() {
  var today = LocalDate.now(clock);
  windowOpenings(today).forEach(this::notifyWindowOpen);       // chave VISIT_WINDOW_OPEN:<certificationId>
  visitsOn(today.plusDays(reminderDays)).forEach(this::remind); // chave VISIT_REMINDER:<visitId>:<plannedOn>
  overdueWithoutResult(today.minusDays(3)).forEach(this::notifyResultMissing); // VISIT_RESULT_MISSING:<visitId>
  briefBuilder.refreshUpcoming(today, 14);                       // RN36
}
```

- **Visita sem aviso:** o visitado nunca entra na lista de destinatários enquanto a visita estiver `PLANNED`. Faça isso **num único lugar**, com `VisitRecipients.of(visit)`, usado pela rotina e pelo `VisitService`.
- **Data no `alert_key` do lembrete:** se a data mudar, o lembrete da nova data também sai.
- **Alerta de vencimento (RN20):** o texto ganha o que falta ("Falta a visita da comissão"), a partir do `VisitCycleCalculator`.

#### 1.15.6 Documentos gerados (RN37, RN38)

```text
document/
├── DocumentController.java
├── DocumentService.java          # gera, guarda, substitui, cancela, recebe a cópia assinada
├── DocumentDataAssembler.java    # monta o modelo de dados de cada tipo (um método por kind)
├── DocumentRenderer.java         # Thymeleaf → HTML → PDF (openhtmltopdf), igual ao caderno de campo
├── VerificationCodes.java        # gera "K7QM-4TZP"
└── resources/templates/document/IN19-v1/
    ├── _layout.html              # cabeçalho, rodapé com código e paginação, caixa de avisos
    ├── _signatures.html          # bloco "nome · assinatura ou digital · data"
    ├── membership-request.html
    ├── evaluation-request.html
    ├── visit-plan.html
    ├── visit-form.html
    ├── peer-visit-record.html
    ├── verification-report.html
    ├── conformity-decision.html
    └── certificate.html
```

1. **Monte os dados:** o `DocumentDataAssembler` monta um record Java por tipo (ex.: `ConformityDecisionData`) e a lista de `warnings`. As pré-condições que bloqueiam lançam `DOCUMENT_PRECONDITION_FAILED`.
2. **Gere o código e o PDF:**
   - o código tem 8 caracteres de `23456789ABCDEFGHJKMNPQRSTUVWXYZ` (sem 0/O, 1/I/L), sorteados com `SecureRandom`;
   - o renderer usa o template da **versão atual** (`app.documents.template-version = IN19-v1`), com o código, a data e os avisos.
3. **Guarde:** grave o PDF no `FileStorage` (chave `documents/yyyy/MM/<id>.pdf`), calcule o `sha256` e salve o `data_snapshot` (o record serializado em JSON).
4. **Substitua o anterior:** na mesma transação, o documento anterior do mesmo `kind` e assunto vira `SUPERSEDED`, com `superseded_by_id`.

Regras dos templates:
- **Rodapé em todas as páginas:** use `@page { @bottom-center { content: ... counter(page) " de " counter(pages); } }`. O openhtmltopdf suporta essas margens de página.
- **Escape:** só texto com `th:text`, nunca `th:utext`. Os campos vêm de agricultores e coordenadores.
- **Decisão (D7):** a seção da decisão é **estática** no template, só com caixas vazias. O modelo de dados nem tem esse campo (ADR-15).
- **Assinaturas do D7:** todos os agricultores **ativos** do grupo na data da geração (art. 84, III), mais o fornecedor e linhas em branco para a Comissão.
- **Roteiro (D4):** imprime o `brief` e as perguntas do `visit_checklist_item` ativo, com quatro caixas por pergunta. Ele é a versão em papel do "modo visita".
- **Versão nova dos modelos:** os modelos oficiais do OPAC entram numa pasta nova (por exemplo, `OPAC-v1/`) e mudam a configuração. Os documentos antigos continuam válidos com o `template_version` deles.

**Cópia assinada:** o `POST /documents/{id}/signed-copy` reaproveita a validação de upload da [§1.13](#113-arquivos) (PDF ou foto), cria um `attachment` com `owner_type = DOCUMENT` e muda o status para `SIGNED`.

#### 1.15.7 Testes do SPG

| Teste | Tipo |
|---|---|
| `VisitCycleCalculator`: sem certificação, período fechado/aberto, URGENT com 30 e 31 dias, contagem fora do ciclo ignorada | Unitário |
| `VisitorEligibility`: cada exclusão da RN32, verificação com e sem `verification_same_group_allowed` | Unitário |
| `VisitorScoring`: com e sem GPS, rodízio, troca, equilíbrio, desempate estável | Unitário |
| `VerificationCodes`: alfabeto e tamanho; colisão tratada com nova tentativa | Unitário |
| Visita sem aviso não gera notificação nem entra no pull do visitado (RN31) | Integração |
| `POST /visits` com visitador impedido → `422 VISITOR_NOT_ELIGIBLE` | Integração |
| Resultado pelo app de quem não é o responsável → `REJECTED` | Integração |
| Gerar D7 duas vezes → o primeiro fica `SUPERSEDED`; o PDF não contém nenhuma decisão marcada | Integração |
| Gerar D5 de visita `PLANNED` → `422 DOCUMENT_PRECONDITION_FAILED` | Integração |
| O agricultor não acessa nenhuma rota de `/visits`, `/documents`, `/collaborators`, `/committee-members`, `/member-conflicts` (403) | Segurança |

### 1.16 OpenAPI e geração de tipos
- Em `OpenApiConfig`, defina o título, a versão e o esquema de segurança `bearerAuth`.
- Anote os controllers com `@Tag`, e os enums e uniões com `@Schema`.
- O front roda `pnpm gen:api` ([doc 3 §B.3](3-guia-front-end.md#b3-tipos-gerados-da-api-agrofacilapi-types)), que lê `http://localhost:8080/api/v1/openapi.json`. **Depois de mudar um DTO, rode o script e versione o `openapi.json` atualizado no mesmo PR.**
- Um teste de integração pode baixar o `openapi.json` e compará-lo com o versionado. Ele falha se alguém esquecer de atualizar.

### 1.17 Testes

| Tipo | Ferramenta | O que cobre |
|---|---|---|
| Unitário | JUnit 5 + AssertJ | Funções puras: `Cpf`, `UnitConversion`, `missingRecommendedFields`, `CertificationStatusCalculator`, `AlertPlanner`, validação de datas e as do SPG ([§1.15.7](#1157-testes-do-spg)) |
| Integração | `@SpringBootTest` + `@AutoConfigureMockMvc` + **Testcontainers** (Postgres real com `@ServiceConnection`) | Endpoints de ponta a ponta, migrações, triggers, segurança e escopo |
| Sincronização | Integração | Os 13 casos do [doc 1 §4.9](1-arquitetura-e-contrato.md#49-casos-de-teste-obrigatórios) |

```java
// Base dos testes de integração: Postgres real em container, compartilhado entre as classes de teste.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class IntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
  // helpers: tokenFor(farmer), tokenFor(coordinator), fixtures…
}
```

Testes obrigatórios de segurança:
- o agricultor A não lê, edita nem exclui um registro do agricultor B (espera **404**);
- o agricultor não acessa nenhuma rota de coordenador (espera 403);
- o 6º código errado bloqueia;
- um refresh reutilizado revoga a família;
- um aparelho revogado não renova a sessão.

Nomeie os testes pelo comportamento, citando a RN: `harvestBeforePlanting_isRejected_RN10()`.

### 1.18 Ordem de implementação sugerida

Cada marco termina com testes passando e o contrato atualizado. O front pode começar em paralelo com dados falsos (MSW), porque o contrato já existe.

| Marco | Entrega |
|---|---|
| M0 | Projeto criado, Spotless, Docker Compose (Postgres + Mailpit), `/actuator/health`, CI rodando `./mvnw verify` |
| M1 | Migrações V1–V5, entidades, Envers, `SyncableEntity`, testes de mapeamento (`ddl-auto: validate` sobe sem erro) |
| M2 | Erros (Problem Details), `TraceIdFilter`, segurança JWT, bootstrap do coordenador, convite, login com 2FA, refresh/logout no web, redefinição de senha |
| M3 | Associação, coordenadores, grupos, agricultores, códigos de acesso, ativação do aparelho, refresh rotativo, aparelhos, `/me` |
| M4 | Propriedades (com a área principal), áreas, trabalhadores, municípios, catálogos (leitura) |
| M5 | Registros no painel: CRUD, completude, conversões, plantio e colheita final, histórico |
| M6 | Sincronização: push, pull, lápides, arquivos. Os 10 testes do [doc 1, Parte 4](1-arquitetura-e-contrato.md#parte-4-sincronização-offline) |
| M7 | Certificações, status, renovação, rotina de alertas, notificações, push (Expo) e e-mail |
| M8 | Comunicados, pedidos de correção, gestão de catálogos, itens sem catálogo |
| M9 | Painel inicial (dashboard), relatórios PDF e CSV |
| M10 | SPG: colaboradores, Comissão e impedimentos; roteiro (catálogo); situação das visitas; sugestão de visitadores; visitas (painel e mutação do app); rotina de avisos; documentos D1–D8 ([§1.15](#115-visitas-e-documentos-do-spg)) |
| M11 | Endurecimento: limites de tentativa revisados, cabeçalhos, revisão de logs (sem dados pessoais), rotina de retenção/anonimização ([doc 1, Parte 5](1-arquitetura-e-contrato.md#parte-5-segurança-e-lgpd)), backup testado ([Parte 3](#parte-3-ambiente-e-implantação)) |

### 1.19 Checklist antes de cada PR
- [ ] `./mvnw verify` passa (Spotless + testes).
- [ ] Todo arquivo novo começa com o comentário de uma frase. Métodos públicos de serviço têm Javadoc.
- [ ] Nenhum valor de configuração ou segredo no código (tudo em `AppProperties` ou variáveis de ambiente).
- [ ] Toda busca de recurso por id passa pelo escopo do usuário.
- [ ] Regra de negócio nova tem número RN no [doc 1, Parte 2](1-arquitetura-e-contrato.md#parte-2-modelo-de-domínio-e-regras-de-negócio) e um teste que cita esse número.
- [ ] Mudou um DTO? `openapi.json` regenerado e o [doc 1, Parte 3](1-arquitetura-e-contrato.md#parte-3-contrato-api-front) atualizado.
- [ ] Nada de dado pessoal completo (CPF, telefone, token, código) em log.
- [ ] Migração nova não edita migração antiga.

## Parte 2. DDL de referência (migração V1)

> Use isto como ponto de partida das migrações Flyway (ver [§1.5](#15-banco-e-migrações-flyway)). As tabelas de auditoria do Envers (`*_aud`) e os dados iniciais vão em migrações separadas.

```sql
-- V1__schema.sql: esquema inicial do Agrofácil.

-- Contador global de mudanças usado pelo pull da sincronização (doc 1, Parte 4).
create sequence sync_seq;
create sequence record_number_seq;

-- Atribui sync_seq a cada escrita. O lock consultivo serializa as transações que
-- escrevem dados sincronizáveis, para que a ordem de sync_seq seja igual à ordem de commit (ADR-09).
create function set_sync_seq() returns trigger language plpgsql as $$
begin
  perform pg_advisory_xact_lock(724001);
  new.sync_seq := nextval('sync_seq');
  return new;
end $$;

create table municipality (
  id   integer primary key,          -- código IBGE (7 dígitos)
  name text not null,
  uf   char(2) not null
);

create table association (
  id                     uuid primary key,
  name                   text not null,
  cnpj                   char(14),
  email                  text,
  phone                  text,
  whatsapp               text,
  opac_name              text,
  tarefa_m2              numeric(10,2) not null default 4356,
  alert_days             integer[] not null default '{60,30,15}',
  access_code_ttl_hours  integer not null default 72,
  retention_years        integer not null default 5,
  visit_window_days      integer not null default 90,
  peer_visits_per_year   integer not null default 2,
  visitors_per_visit     integer not null default 2,
  visit_reminder_days    integer not null default 1,
  verification_same_group_allowed boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table spg_group (
  id              uuid primary key,
  association_id  uuid not null references association(id),
  name            text not null,
  municipality_id integer references municipality(id),
  notes           text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table app_user (
  id                uuid primary key,
  association_id    uuid not null references association(id),
  role              text not null check (role in ('FARMER','COORDINATOR')),
  name              text not null,
  cpf               char(11),
  email             text,
  phone             text,
  password_hash     text,
  status            text not null default 'ACTIVE' check (status in ('ACTIVE','INACTIVE')),
  terms_accepted_at timestamptz,
  terms_version     text,
  last_login_at     timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0,
  check (role <> 'FARMER' or cpf is not null),
  check (role <> 'COORDINATOR' or email is not null)
);
create unique index ux_app_user_cpf   on app_user (cpf)          where deleted_at is null and cpf is not null;
create unique index ux_app_user_email on app_user (lower(email)) where deleted_at is null and email is not null;
create index ix_app_user_assoc_role on app_user (association_id, role);

create table farmer (
  user_id           uuid primary key references app_user(id),
  group_id          uuid references spg_group(id),
  caf_number        text,
  group_joined_year integer,
  postal_code       char(8),
  uf                char(2),
  municipality_id   integer references municipality(id),
  locality          text,
  street            text,
  number            text,
  complement        text,
  notes             text
);

create table property (
  id                     uuid primary key,
  association_id         uuid not null references association(id),
  farmer_id              uuid not null references app_user(id),
  name                   text not null,
  total_area             numeric(12,3),
  total_area_unit        text,
  total_area_m2          numeric(14,2),
  production_description text,
  latitude               numeric(9,6),
  longitude              numeric(9,6),
  location_accuracy_m    numeric(8,1),
  postal_code            char(8),
  uf                     char(2),
  municipality_id        integer references municipality(id),
  locality               text,
  street                 text,
  number                 text,
  complement             text,
  access_directions      text,
  created_by             uuid not null references app_user(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);
create index ix_property_farmer on property (farmer_id) where deleted_at is null;

create table plot (
  id                 uuid primary key,
  property_id        uuid not null references property(id),
  name               text not null,
  area               numeric(12,3),
  area_unit          text,
  area_m2            numeric(14,2),
  status             text not null default 'NOT_DEFINED'
                     check (status in ('ORGANIC','IN_CONVERSION','NON_ORGANIC','NOT_DEFINED')),
  conversion_ends_on date,
  notes              text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table worker (
  id           uuid primary key,
  property_id  uuid not null references property(id),
  name         text not null,
  relationship text not null check (relationship in ('FAMILY','EMPLOYEE','PARTNER','OTHER')),
  active       boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table certification (
  id            uuid primary key,
  property_id   uuid not null references property(id),
  number        text,
  issued_on     date not null,
  expires_on    date not null,
  manual_status text not null default 'ACTIVE' check (manual_status in ('ACTIVE','SUSPENDED','CANCELLED')),
  status_reason text,
  scope         text,
  previous_id   uuid references certification(id),
  notes         text,
  created_by    uuid not null references app_user(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0,
  check (expires_on > issued_on)
);
create index ix_certification_property on certification (property_id, issued_on desc);

create table crop_category (
  id   uuid primary key,
  name text not null,
  icon text
);

create table crop (
  id             uuid primary key,
  association_id uuid references association(id),
  category_id    uuid references crop_category(id),
  name           text not null,
  icon           text,
  active         boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table certification_product (
  certification_id uuid not null references certification(id),
  crop_id          uuid references crop(id),
  product_name     text,
  check (crop_id is not null or product_name is not null)
);

create table certification_worker (
  certification_id uuid not null references certification(id),
  worker_id        uuid not null references worker(id),
  primary key (certification_id, worker_id)
);

create table certification_alert (
  certification_id uuid not null references certification(id),
  days_before      integer not null,
  sent_at          timestamptz not null default now(),
  primary key (certification_id, days_before)
);

create table farm_input (
  id                   uuid primary key,
  association_id       uuid references association(id),
  name                 text not null,
  input_type           text not null check (input_type in
                        ('FERTILIZER','SOIL_CORRECTIVE','BIOFERTILIZER','PEST_DISEASE_CONTROL','INOCULANT','OTHER')),
  regulatory_reference text,
  needs_authorization  boolean not null default false,
  home_made            boolean not null default false,
  active               boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table measurement_unit (
  code           text primary key,
  name_singular  text not null,
  name_plural    text not null,
  kind           text not null check (kind in ('AREA','MASS','VOLUME','COUNT','PACKAGE')),
  to_base_factor numeric(14,6),
  sort_order     integer not null default 0,
  active         boolean not null default true,
  updated_at timestamptz not null default now(),
  sync_seq   bigint not null default 0
);

create table device (
  id           uuid primary key,          -- gerado pelo app na instalação
  user_id      uuid not null references app_user(id),
  name         text,
  platform     text,
  app_version  text,
  push_token   text,
  created_at   timestamptz not null default now(),
  last_seen_at timestamptz,
  revoked_at   timestamptz,
  revoked_by   uuid references app_user(id)
);

create table management_record (
  id                   uuid primary key,
  record_type          text not null check (record_type in
                        ('PLANTING','INPUT_APPLICATION','HARVEST','INPUT_PURCHASE','OUTFLOW')),
  record_number        bigint unique default nextval('record_number_seq'),
  association_id       uuid not null references association(id),
  farmer_id            uuid not null references app_user(id),
  property_id          uuid not null references property(id),
  plot_id              uuid references plot(id),
  performed_on         date not null,
  notes                text,
  latitude             numeric(9,6),
  longitude            numeric(9,6),
  location_accuracy_m  numeric(8,1),
  location_captured_at timestamptz,
  device_id            uuid references device(id),
  source               text not null check (source in ('MOBILE','WEB')),
  created_by           uuid not null references app_user(id),
  updated_by           uuid not null references app_user(id),
  client_created_at    timestamptz,
  completeness         text not null check (completeness in ('COMPLETE','INCOMPLETE')),
  missing_fields       text[] not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);
create index ix_record_farmer_date on management_record (farmer_id, performed_on desc) where deleted_at is null;
create index ix_record_property    on management_record (property_id, record_type, performed_on);
create index ix_record_assoc_compl on management_record (association_id, completeness) where deleted_at is null;

create table planting (
  record_id            uuid primary key references management_record(id),
  crop_id              uuid references crop(id),
  crop_other_name      text,
  variety              text,
  planted_area         numeric(12,3),
  planted_area_unit    text references measurement_unit(code),
  planted_area_m2      numeric(14,2),
  propagation_type     text check (propagation_type in ('SEED','SEEDLING','CUTTING','OTHER')),
  propagation_quantity numeric(12,3),
  propagation_unit     text references measurement_unit(code),
  seed_origin          text check (seed_origin in ('OWN','ORGANIC_PURCHASED','CONVENTIONAL_PURCHASED','SEED_BANK')),
  seed_supplier        text,
  expected_harvest_on  date,
  cycle_status         text not null default 'ACTIVE' check (cycle_status in ('ACTIVE','FINISHED')),
  finished_on          date,
  check (crop_id is not null or crop_other_name is not null)
);

create table input_application (
  record_id         uuid primary key references management_record(id),
  planting_id       uuid references planting(record_id),
  input_id          uuid references farm_input(id),
  input_other_name  text,
  purpose           text check (purpose in ('FERTILIZATION','PEST_DISEASE_CONTROL','SOIL_CORRECTION','OTHER')),
  quantity          numeric(12,3),
  quantity_unit     text references measurement_unit(code),
  applied_area      numeric(12,3),
  applied_area_unit text references measurement_unit(code),
  applied_area_m2   numeric(14,2),
  check (input_id is not null or input_other_name is not null)
);

create table harvest (
  record_id           uuid primary key references management_record(id),
  planting_id         uuid not null references planting(record_id),
  quantity            numeric(12,3),
  quantity_unit       text references measurement_unit(code),
  harvested_area      numeric(12,3),
  harvested_area_unit text references measurement_unit(code),
  harvested_area_m2   numeric(14,2),
  is_final            boolean not null default false
);

create table input_purchase (
  record_id        uuid primary key references management_record(id),
  input_id         uuid references farm_input(id),
  input_other_name text,
  quantity         numeric(12,3),
  quantity_unit    text references measurement_unit(code),
  supplier         text,
  check (input_id is not null or input_other_name is not null)
);

create table outflow (
  record_id          uuid primary key references management_record(id),
  crop_id            uuid references crop(id),
  product_other_name text,
  planting_id        uuid references planting(record_id),
  quantity           numeric(12,3),
  quantity_unit      text references measurement_unit(code),
  destination        text check (destination in
                      ('FAIR','COOPERATIVE','PNAE','PAA','MIDDLEMAN','DIRECT_SALE','OWN_CONSUMPTION','DONATION','OTHER')),
  buyer_name         text,
  check (crop_id is not null or product_other_name is not null)
);

create table attachment (
  id               uuid primary key,
  association_id   uuid not null references association(id),
  owner_type       text not null check (owner_type in ('RECORD','PROPERTY','CERTIFICATION','ANNOUNCEMENT','VISIT','DOCUMENT')),
  owner_id         uuid not null,
  kind             text not null check (kind in ('PHOTO','AUDIO','DOCUMENT')),
  content_type     text not null,
  size_bytes       bigint,
  sha256           char(64),
  storage_key      text,
  duration_seconds integer,
  uploaded_at      timestamptz,
  created_by       uuid not null references app_user(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);
create index ix_attachment_owner on attachment (owner_type, owner_id);

create table correction_request (
  id          uuid primary key,
  record_id   uuid not null references management_record(id),
  message     text not null,
  status      text not null default 'OPEN' check (status in ('OPEN','RESOLVED')),
  created_by  uuid not null references app_user(id),
  resolved_by uuid references app_user(id),
  resolved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table announcement (
  id             uuid primary key,
  association_id uuid not null references association(id),
  type           text not null check (type in ('NOTICE','TIP')),
  title          text not null,
  body           text not null,
  group_id       uuid references spg_group(id),
  published_at   timestamptz not null default now(),
  created_by     uuid not null references app_user(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table notification (
  id         uuid primary key,
  user_id    uuid not null references app_user(id),
  type       text not null,
  title      text not null,
  body       text not null,
  data       jsonb not null default '{}',
  read_at    timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);
create index ix_notification_user on notification (user_id, created_at desc);

create table refresh_token (
  id         uuid primary key,
  user_id    uuid not null references app_user(id),
  device_id  uuid references device(id),
  family_id  uuid not null,
  token_hash char(64) not null unique,     -- SHA-256 do token (o token tem 256 bits aleatórios)
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  used_at    timestamptz,
  revoked_at timestamptz
);

create table access_code (
  id              uuid primary key,
  farmer_id       uuid not null references app_user(id),
  code_hash       text not null,          -- Argon2id
  expires_at      timestamptz not null,
  failed_attempts integer not null default 0,
  used_at         timestamptz,
  revoked_at      timestamptz,
  created_by      uuid not null references app_user(id),
  created_at      timestamptz not null default now()
);

create table login_challenge (
  id              uuid primary key,
  user_id         uuid not null references app_user(id),
  code_hash       text not null,
  expires_at      timestamptz not null,
  failed_attempts integer not null default 0,
  used_at         timestamptz,
  created_at      timestamptz not null default now()
);

create table user_token (
  id         uuid primary key,
  user_id    uuid not null references app_user(id),
  purpose    text not null check (purpose in ('PASSWORD_RESET','INVITE')),
  token_hash char(64) not null unique,
  expires_at timestamptz not null,
  used_at    timestamptz,
  created_at timestamptz not null default now()
);

create table sync_mutation (
  id           uuid primary key,          -- gerado pelo app para cada mutação
  user_id      uuid not null references app_user(id),
  device_id    uuid references device(id),
  entity       text not null,
  entity_id    uuid not null,
  status       text not null,
  response     jsonb not null,
  processed_at timestamptz not null default now()
);

-- Avisos já enviados pelas rotinas do SPG (RN30).
create table alert_log (
  subject_id uuid not null,
  alert_key  text not null,
  sent_at    timestamptz not null default now(),
  primary key (subject_id, alert_key)
);

-- Certificação participativa (doc 1, Parte 6).
create table collaborator (
  id             uuid primary key,
  association_id uuid not null references association(id),
  group_id       uuid references spg_group(id),
  name           text not null,
  kind           text not null check (kind in ('TECHNICIAN','CONSUMER','ORGANIZATION','OTHER')),
  organization   text,
  phone          text,
  notes          text,
  active         boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0
);

create table committee_member (
  id                   uuid primary key,
  association_id       uuid not null references association(id),
  farmer_id            uuid references app_user(id),
  collaborator_id      uuid references collaborator(id),
  represented_group_id uuid not null references spg_group(id),
  role                 text not null check (role in ('HOLDER','ALTERNATE')),
  started_on           date not null,
  ended_on             date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0,
  check ((farmer_id is null) <> (collaborator_id is null))
);

create table member_conflict (
  id             uuid primary key,
  association_id uuid not null references association(id),
  farmer_a_id    uuid not null references app_user(id),
  farmer_b_id    uuid not null references app_user(id),
  reason         text not null check (reason in ('FAMILY','SAME_HOUSEHOLD','BUSINESS','OTHER')),
  notes          text,
  created_at     timestamptz not null default now(),
  check (farmer_a_id < farmer_b_id),
  unique (farmer_a_id, farmer_b_id)
);

create table visit (
  id                      uuid primary key,
  association_id          uuid not null references association(id),
  property_id             uuid not null references property(id),
  group_id                uuid references spg_group(id),
  certification_id        uuid references certification(id),
  type                    text not null check (type in ('PEER','VERIFICATION')),
  unannounced             boolean not null default false,
  status                  text not null default 'PLANNED' check (status in ('PLANNED','DONE','CANCELLED')),
  planned_on              date not null,
  cancel_reason           text,
  recorder_participant_id uuid,                 -- referência adicionada depois de visit_participant
  done_on                 date,
  answers                 jsonb not null default '[]',
  issues_found            boolean,
  improvement_points      text,
  recommendations         text,
  notes                   text,
  latitude                numeric(9,6),
  longitude               numeric(9,6),
  location_accuracy_m     numeric(8,1),
  result_source           text check (result_source in ('MOBILE','WEB')),
  result_recorded_by      uuid references app_user(id),
  result_recorded_at      timestamptz,
  brief                   jsonb,
  brief_updated_at        timestamptz,
  created_by              uuid not null references app_user(id),
  updated_by              uuid not null references app_user(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0,
  check (type = 'VERIFICATION' or not unannounced),
  check (status <> 'DONE' or done_on is not null)
);
create index ix_visit_property on visit (property_id, type, done_on) where deleted_at is null;
create index ix_visit_planning on visit (association_id, status, planned_on) where deleted_at is null;

create table visit_participant (
  id                 uuid primary key,
  visit_id           uuid not null references visit(id),
  farmer_id          uuid references app_user(id),
  collaborator_id    uuid references collaborator(id),
  role               text not null check (role in ('VISITOR','OBSERVER')),
  suggested          boolean not null default false,
  suggestion_reasons text[] not null default '{}',
  attended           boolean,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  check ((farmer_id is null) <> (collaborator_id is null))
);
create index ix_visit_participant_farmer on visit_participant (farmer_id);
alter table visit add constraint fk_visit_recorder
  foreign key (recorder_participant_id) references visit_participant(id);

create table visit_checklist_item (
  id               uuid primary key,
  code             text not null,
  association_id   uuid references association(id),   -- nulo = modelo padrão IN19-v1
  applies_to       text not null check (applies_to in ('PEER','VERIFICATION','BOTH')),
  question         text not null,
  formal_text      text not null,
  help             text,
  legal_reference  text,
  sort_order       integer not null default 0,
  template_version text not null,
  active           boolean not null default true,
  updated_at timestamptz not null default now(),
  sync_seq   bigint not null default 0
);
-- Um código por associação (o modelo padrão tem association_id nulo).
create unique index ux_visit_checklist_item on visit_checklist_item
  (coalesce(association_id, '00000000-0000-0000-0000-000000000000'::uuid), code);

create table generated_document (
  id                   uuid primary key,
  association_id       uuid not null references association(id),
  kind                 text not null check (kind in ('MEMBERSHIP_REQUEST','EVALUATION_REQUEST','VISIT_PLAN','VISIT_FORM',
                        'PEER_VISIT_RECORD','VERIFICATION_REPORT','CONFORMITY_DECISION','CERTIFICATE')),
  template_version     text not null,
  title                text not null,
  farmer_id            uuid references app_user(id),
  property_id          uuid references property(id),
  group_id             uuid references spg_group(id),
  visit_id             uuid references visit(id),
  certification_id     uuid references certification(id),
  period_from          date,
  period_to            date,
  verification_code    char(8) not null unique,
  sha256               char(64) not null,
  storage_key          text not null,
  size_bytes           bigint not null,
  data_snapshot        jsonb not null,
  warnings             text[] not null default '{}',
  status               text not null default 'GENERATED'
                       check (status in ('GENERATED','SIGNED','SUPERSEDED','CANCELLED')),
  signed_attachment_id uuid references attachment(id),
  superseded_by_id     uuid references generated_document(id),
  cancel_reason        text,
  generated_by         uuid not null references app_user(id),
  generated_at         timestamptz not null default now()
);
create index ix_generated_document_subject on generated_document (kind, property_id);

-- Trigger de sync_seq em todas as tabelas sincronizáveis.
do $$
declare t text;
begin
  foreach t in array array['association','spg_group','app_user','property','plot','worker','certification',
                           'crop','farm_input','measurement_unit','management_record','attachment',
                           'correction_request','announcement','notification','visit','visit_checklist_item']
  loop
    execute format('create trigger trg_%1$s_sync_seq before insert or update on %1$s
                    for each row execute function set_sync_seq()', t);
    execute format('create index ix_%1$s_sync_seq on %1$s (sync_seq)', t);
  end loop;
end $$;
```

> **Visitas:** `visit_participant` não tem `sync_seq`, porque os participantes vão dentro do JSON da visita. Por isso, toda mudança de participante **também** atualiza a linha de `visit` (`updated_at`, `version`), o que dispara o trigger. O mesmo vale para a lápide de quem saiu da visita ([doc 1 §4.8](1-arquitetura-e-contrato.md#48-servidor-como-o-pull-é-calculado)).

> **Atenção:** os subtipos (`planting`, `harvest`…) não têm `sync_seq` próprio. Ao alterar um subtipo, o serviço **sempre** atualiza também a linha de `management_record` (ao menos `updated_at` e `version`), e é isso que dispara o trigger. O JPA já faz isso por causa do `@Version` na superclasse.

## Parte 3. Ambiente e implantação

Por enquanto o sistema roda **num computador da equipe**, para testes e apresentações. Isso funciona bem com o desenho abaixo. Para uso real com dados dos agricultores, veja a [§3.8](#38-caminho-para-produção-real): um computador pessoal não garante disponibilidade (RNF07) nem a segurança física dos dados.

### 3.1 Visão dos ambientes

| Ambiente | Onde | API | Painel | App |
|---|---|---|---|---|
| **Desenvolvimento** | Máquina de cada membro | `./mvnw spring-boot:run` (perfil `dev`) | `pnpm --filter @agrofacil/web dev` (Vite, porta 5173, com proxy `/api`) | Development build apontando para o **IP da máquina na rede local** (`http://192.168.x.x:8080`) |
| **Demonstração / testes de campo** | O computador "servidor" da equipe, via Docker Compose | Container `api` | Container `web` (nginx) | APK `preview` apontando para a **URL HTTPS do túnel** |

### 3.2 Desenvolvimento local

`infra/docker-compose.dev.yml` traz só o que a API precisa para rodar:

```yaml
# Serviços de apoio para desenvolvimento: banco e caixa de e-mail de teste.
services:
  postgres:
    image: postgres:17.11-alpine
    environment:
      POSTGRES_DB: agrofacil
      POSTGRES_USER: agrofacil
      POSTGRES_PASSWORD: agrofacil   # só desenvolvimento; a porta fica presa ao localhost
    ports: ["127.0.0.1:5432:5432"]
    volumes: [pgdata-dev:/var/lib/postgresql/data]
  mailpit:
    image: axllent/mailpit:v1.33.0
    ports: ["127.0.0.1:1025:1025", "127.0.0.1:8025:8025"] # SMTP e interface web (http://localhost:8025)
volumes:
  pgdata-dev:
```

Primeiro uso:
1. `cp api/.env.example api/.env` e preencha o `JWT_SECRET` (`openssl rand -base64 48`) e o `BOOTSTRAP_COORDINATOR_EMAIL`.
2. `cd api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. O suporte a Docker Compose do Spring Boot sobe o Postgres e o Mailpit sozinho (o `docker compose -f infra/docker-compose.dev.yml up -d` continua valendo para subir à mão). O Flyway cria o banco, e o convite do primeiro coordenador aparece no Mailpit.
3. `corepack pnpm install` na raiz (ou `corepack enable` uma vez e depois só `pnpm`), depois `pnpm gen:api` e `pnpm --filter @agrofacil/web dev`.
4. Mobile:
   1. copie `apps/mobile/.env.example` para `apps/mobile/.env` e defina `EXPO_PUBLIC_API_URL=http://<seu IP>:8080`;
   2. rode `npx expo run:android` com o celular em modo desenvolvedor ou num emulador.

> **Android e HTTP:** o Android bloqueia HTTP sem TLS por padrão. No perfil de desenvolvimento, libere o tráfego em texto claro **só no build de desenvolvimento**, com o plugin `expo-build-properties` (`android.usesCleartextTraffic: true`). Os builds `preview` usam sempre HTTPS.

### 3.3 Demonstração: Docker Compose completo

```text
infra/
├── docker-compose.yml
├── .env.example
├── nginx/default.conf
└── backup/backup.sh
api/Dockerfile
apps/web/Dockerfile
```

```yaml
# Pilha completa para demonstração: banco, API e painel (nginx), mais o túnel HTTPS.
name: agrofacil                  # fixa o prefixo dos volumes (agrofacil_files, usado no backup)
services:
  postgres:
    image: postgres:17-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: agrofacil
      POSTGRES_USER: agrofacil
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U agrofacil"]
      interval: 10s

  api:
    build: ../api
    restart: unless-stopped
    depends_on:
      postgres: { condition: service_healthy }
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: jdbc:postgresql://postgres:5432/agrofacil
      DB_USER: agrofacil
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      SMTP_HOST: ${SMTP_HOST}
      SMTP_PORT: ${SMTP_PORT}
      SMTP_USER: ${SMTP_USER}
      SMTP_PASSWORD: ${SMTP_PASSWORD}
      MAIL_FROM: ${MAIL_FROM}
      APP_PUBLIC_URL: ${PUBLIC_URL}
      EXPO_ACCESS_TOKEN: ${EXPO_ACCESS_TOKEN}
      BOOTSTRAP_COORDINATOR_EMAIL: ${BOOTSTRAP_COORDINATOR_EMAIL}
      STORAGE_DIR: /data/files
      TZ: America/Bahia
    volumes: [files:/data/files]
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/api/v1/actuator/health/readiness || exit 1"]
      interval: 15s

  web:
    build:
      context: ..
      dockerfile: apps/web/Dockerfile
    restart: unless-stopped
    depends_on: [api]
    ports: ["8080:80"]                 # só a porta do nginx fica exposta

  tunnel:
    image: cloudflare/cloudflared:latest   # fixe uma versão
    restart: unless-stopped
    command: tunnel --no-autoupdate run
    environment:
      TUNNEL_TOKEN: ${CLOUDFLARE_TUNNEL_TOKEN}
    depends_on: [web]

volumes:
  pgdata:
  files:
```

`api/Dockerfile` (multi-stage):

```dockerfile
# Imagem da API: compila com Maven e roda só o JRE, com usuário sem privilégios.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && mkdir -p /data/files && chown app:app /data/files
USER app
COPY --from=build /src/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
```

`apps/web/Dockerfile`:

```dockerfile
# Imagem do painel: compila o React no monorepo e serve os arquivos estáticos com nginx.
FROM node:22-alpine AS build
WORKDIR /repo
RUN corepack enable
COPY . .
RUN pnpm install --frozen-lockfile && pnpm --filter @agrofacil/web build

FROM nginx:1.27-alpine
COPY infra/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/apps/web/dist /usr/share/nginx/html
```

### 3.4 nginx: painel + API na mesma origem

```nginx
# Serve o painel (SPA) e repassa /api para a API; aplica cabeçalhos de segurança.
server {
  listen 80;
  root /usr/share/nginx/html;
  client_max_body_size 11m;

  add_header X-Content-Type-Options "nosniff" always;
  add_header Referrer-Policy "no-referrer" always;
  add_header X-Frame-Options "DENY" always;
  add_header Strict-Transport-Security "max-age=31536000" always;
  add_header Content-Security-Policy "default-src 'self'; img-src 'self' data: blob: https://*.tile.openstreetmap.org; media-src 'self' blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'" always;

  location /api/ {
    proxy_pass http://api:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $http_x_forwarded_proto;
    proxy_read_timeout 60s;
  }

  location /assets/ {
    expires 1y;
    add_header Cache-Control "public, immutable";
  }

  location / {
    try_files $uri /index.html;   # rotas do React Router
    add_header Cache-Control "no-cache";
  }
}
```

> `'unsafe-inline'` em `style-src` é necessário porque o Mantine usa atributos `style`. Scripts continuam bloqueados para qualquer origem que não seja a própria.

### 3.5 HTTPS para os celulares no campo: túnel

O app no celular precisa alcançar a API pela internet e **com HTTPS**. Sem abrir portas no roteador, há duas opções:

| Opção | Endereço | Prós | Contras |
|---|---|---|---|
| **Cloudflare Tunnel com nome** (recomendado) | `https://agrofacil.<seu-dominio>` | Endereço fixo, HTTPS automático, gratuito | Precisa de um domínio na Cloudflare (alguns reais por ano) |
| **ngrok com domínio estático gratuito** | `https://<nome>.ngrok-free.app` | Sem domínio próprio, endereço fixo | Limites do plano gratuito; o painel do ngrok mostra um aviso no navegador na primeira visita |

**O endereço precisa ser fixo:** ele vai "embutido" no APK (`EXPO_PUBLIC_API_URL`). Um túnel com endereço aleatório (como o "quick tunnel") obriga a gerar um APK novo a cada reinício. Evite.

Para configurar a Cloudflare:
1. crie o túnel no painel Zero Trust, que gera o `TUNNEL_TOKEN`;
2. aponte o hostname público para `http://web:80`;
3. coloque o token no `infra/.env`.

### 3.6 Backup e restauração

`infra/backup/backup.sh`, agendado no cron do computador servidor, todo dia às 02:00:

```bash
#!/usr/bin/env bash
# Faz backup diário do banco e dos anexos, mantém 30 diários e 12 mensais, e copia para fora da máquina.
set -euo pipefail
STAMP=$(date +%F)
DEST=/var/backups/agrofacil
mkdir -p "$DEST/daily" "$DEST/monthly"

docker compose -f /opt/agrofacil/infra/docker-compose.yml exec -T postgres \
  pg_dump -U agrofacil -Fc agrofacil > "$DEST/daily/db-$STAMP.dump"
docker run --rm -v agrofacil_files:/files -v "$DEST/daily":/out alpine \
  tar czf "/out/files-$STAMP.tar.gz" -C /files .

if [ "$(date +%d)" = "01" ]; then cp "$DEST/daily/"*"$STAMP"* "$DEST/monthly/"; fi
find "$DEST/daily" -type f -mtime +30 -delete
ls -1t "$DEST/monthly"/db-*.dump | tail -n +13 | xargs -r rm --

# Cópia para fora do computador (HD externo, outro computador ou nuvem com rclone).
rclone copy "$DEST" remoto:agrofacil-backups --max-age 48h
```

**Teste de restauração (obrigatório, uma vez por mês):**
1. `docker run -d --name pg-restore -e POSTGRES_PASSWORD=x postgres:17-alpine`
2. `docker exec -i pg-restore createdb -U postgres agrofacil`
3. `docker exec -i pg-restore pg_restore -U postgres -d agrofacil < db-AAAA-MM-DD.dump`
4. Confira a contagem de registros e abra um caderno de campo.
5. Anote a data do teste no README de `infra/`.

> Os backups contêm dados pessoais. Proteja-os: disco criptografado ou `rclone crypt` ([doc 1 §5.2](1-arquitetura-e-contrato.md#52-ameaças-e-controles)).

### 3.7 Distribuição do app (APK)

1. `eas build --profile preview --platform android`, com `EXPO_PUBLIC_API_URL=https://agrofacil.<dominio>`.
2. O EAS devolve um link de download do APK. Gere um QR Code para o coordenador instalar nos celulares durante a ativação.
3. No celular, permita "Instalar apps desconhecidos" para o navegador e instale.
4. **Versão nova:** gere outro APK e instale por cima. Os dados locais são mantidos. Se a mudança quebrar o contrato, suba `APP_MIN_MOBILE_VERSION` na API **depois** que todos tiverem a versão nova, ou deixe a tela "Atualize o app" orientar.

### 3.8 Caminho para produção real

O computador próprio serve para testes e apresentações. Antes de a associação depender do sistema no dia a dia:

| Item | Por quê |
|---|---|
| Servidor sempre ligado: VPS pequena (2 vCPU, 2–4 GB de RAM) ou um servidor do IFBA | RNF07: disponibilidade. O mesmo `docker-compose.yml` funciona lá |
| Domínio próprio + HTTPS (Let's Encrypt via Caddy ou o próprio túnel) | URL fixa para o APK |
| Backup fora do servidor, com restauração testada | Os registros são prova da certificação |
| Monitoramento simples: UptimeRobot no `/actuator/health` | Saber da queda antes dos agricultores |
| E-mail transacional com domínio próprio (SPF/DKIM) | Os e-mails de 2FA e de alertas não podem cair no spam |
| Publicação na Play Store (opcional) | Atualização automática e instalação mais fácil |
| Revisão jurídica e termo controladora/operadora ([doc 1, Parte 5](1-arquitetura-e-contrato.md#parte-5-segurança-e-lgpd)) | LGPD |

### 3.9 Variáveis de ambiente (`infra/.env.example`)

```dotenv
# Copie para .env e preencha. Nunca versione o .env.
DB_PASSWORD=
JWT_SECRET=                      # openssl rand -base64 48
PUBLIC_URL=https://agrofacil.exemplo.org
SMTP_HOST=
SMTP_PORT=587
SMTP_USER=
SMTP_PASSWORD=
MAIL_FROM="Agrofácil <nao-responda@exemplo.org>"
EXPO_ACCESS_TOKEN=               # opcional; reforça a segurança do envio de push
BOOTSTRAP_COORDINATOR_EMAIL=     # usado só na primeira subida
CLOUDFLARE_TUNNEL_TOKEN=
APP_MIN_MOBILE_VERSION=1.0.0
```
