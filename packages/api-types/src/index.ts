// Reexporta os tipos gerados do OpenAPI com nomes curtos para uso nos apps (doc 3 §B.3).
import type { components, paths } from "./schema";

export type { components, paths };
export type Schemas = components["schemas"];

// Autenticação e perfil
export type LoginRequest = Schemas["LoginRequest"];
export type LoginChallengeResponse = Schemas["LoginChallengeResponse"];
export type VerifyRequest = Schemas["VerifyRequest"];
export type ResendRequest = Schemas["ResendRequest"];
export type ForgotPasswordRequest = Schemas["ForgotPasswordRequest"];
export type ResetPasswordRequest = Schemas["ResetPasswordRequest"];
export type AcceptInviteRequest = Schemas["AcceptInviteRequest"];
export type ActivateRequest = Schemas["ActivateRequest"];
export type SessionResponse = Schemas["SessionResponse"];
export type Me = Schemas["Me"];
export type MeWrite = Schemas["MeWrite"];
export type PasswordChange = Schemas["PasswordChange"];

// Associação, coordenadores, grupos e agricultores
export type AssociationView = Schemas["AssociationView"];
export type AssociationWrite = Schemas["AssociationWrite"];
export type CoordinatorView = Schemas["CoordinatorView"];
export type InvitationRequest = Schemas["InvitationRequest"];
export type GroupView = Schemas["GroupView"];
export type GroupDetail = Schemas["GroupDetail"];
export type GroupWrite = Schemas["GroupWrite"];
export type FarmerListItem = Schemas["FarmerListItem"];
export type FarmerView = Schemas["FarmerView"];
export type FarmerWrite = Schemas["FarmerWrite"];
export type FarmerFilter = Schemas["FarmerFilter"];
export type AccessCodeResponse = Schemas["AccessCodeResponse"];
export type DeviceView = Schemas["DeviceView"];

// Propriedades, áreas e trabalhadores
export type Address = Schemas["Address"];
export type Area = Schemas["Area"];
export type Quantity = Schemas["Quantity"];
export type GeoPoint = Schemas["GeoPoint"];
export type PropertyView = Schemas["PropertyView"];
export type PropertyDetail = Schemas["PropertyDetail"];
export type PropertyWrite = Schemas["PropertyWrite"];
export type PropertyFilter = Schemas["PropertyFilter"];
export type PropertyMapItem = Schemas["PropertyMapItem"];
export type PlotView = Schemas["PlotView"];
export type PlotWrite = Schemas["PlotWrite"];
export type WorkerView = Schemas["WorkerView"];
export type WorkerWrite = Schemas["WorkerWrite"];

// Registros de manejo e pedidos de correção
export type RecordView = Schemas["RecordView"];
export type RecordDetail = RecordView & Schemas["RecordDetail"];
export type RecordListItem = Schemas["RecordListItem"];
export type RecordWrite = Schemas["RecordWrite"];
export type RecordFilter = Schemas["RecordFilter"];
export type RecordHistoryEntry = Schemas["RecordHistoryEntry"];
export type PlantingRecord = Schemas["PlantingRecord"];
export type InputApplicationRecord = Schemas["InputApplicationRecord"];
export type HarvestRecord = Schemas["HarvestRecord"];
export type InputPurchaseRecord = Schemas["InputPurchaseRecord"];
export type OutflowRecord = Schemas["OutflowRecord"];
export type PlantingWrite = Schemas["PlantingWrite"];
export type InputApplicationWrite = Schemas["InputApplicationWrite"];
export type HarvestWrite = Schemas["HarvestWrite"];
export type InputPurchaseWrite = Schemas["InputPurchaseWrite"];
export type OutflowWrite = Schemas["OutflowWrite"];
export type CorrectionRequestView = Schemas["CorrectionRequestView"];

// Certificações
export type CertificationView = Schemas["CertificationView"];
export type CertificationSummary = Schemas["CertificationSummary"];
export type CertificationWrite = Schemas["CertificationWrite"];
export type CertificationFilter = Schemas["CertificationFilter"];

// Catálogos, comunicados, notificações e arquivos
export type CropView = Schemas["CropView"];
export type CropWrite = Schemas["CropWrite"];
export type CropCategoryView = Schemas["CropCategoryView"];
export type FarmInputView = Schemas["FarmInputView"];
export type FarmInputWrite = Schemas["FarmInputWrite"];
export type UnitView = Schemas["UnitView"];
export type UnitWrite = Schemas["UnitWrite"];
export type MunicipalityView = Schemas["MunicipalityView"];
export type UnmappedItem = Schemas["UnmappedItem"];
export type ResolveUnmapped = Schemas["ResolveUnmapped"];
export type AnnouncementView = Schemas["AnnouncementView"];
export type AnnouncementWrite = Schemas["AnnouncementWrite"];
export type NotificationView = Schemas["NotificationView"];
export type AttachmentView = Schemas["AttachmentView"];

// Dashboard, SPG (visitas, pessoas e documentos) e sincronização
export type DashboardSummary = Schemas["DashboardSummary"];
export type PropertyVisitStatus = Schemas["PropertyVisitStatus"];
export type VisitView = Schemas["VisitView"];
export type VisitWrite = Schemas["VisitWrite"];
export type VisitFilter = Schemas["VisitFilter"];
export type VisitBrief = Schemas["VisitBrief"];
export type ResultWrite = Schemas["ResultWrite"];
export type VisitorSuggestion = Schemas["VisitorSuggestion"];
export type SuggestRequest = Schemas["SuggestRequest"];
export type CommitteeView = Schemas["CommitteeView"];
export type CommitteeWrite = Schemas["CommitteeWrite"];
export type ConflictView = Schemas["ConflictView"];
export type ConflictWrite = Schemas["ConflictWrite"];
export type DocumentView = Schemas["DocumentView"];
export type DocumentFilter = Schemas["DocumentFilter"];
export type GenerateDocumentRequest = Schemas["GenerateDocumentRequest"];
export type PageMeta = Schemas["PageMeta"];
export type Pageable = Schemas["Pageable"];
export type PushRequest = Schemas["PushRequest"];
export type PushResponse = Schemas["PushResponse"];
export type PullResponse = Schemas["PullResponse"];
export type FieldErrorItem = Schemas["FieldErrorItem"];
export type ParticipantWrite = Schemas["ParticipantWrite"];
