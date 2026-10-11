import type { Static } from 'typebox'
import { ASSET_MEDIA_TYPES, MAX_ASSET_BYTES } from '../models/asset.ts'
import { PREPARATION_RESPONSE_SCHEMA, PREPARE_REQUEST_SCHEMA } from '../models/asset-preparation.ts'
import { REALTIME_PROTOCOL_VERSION, REALTIME_SUBPROTOCOL } from '../realtime/messages.ts'
import type { CapabilitiesSchema } from '../schemas/capabilities.schema.ts'
import { SELECTION_SCHEMA_VERSIONS } from '../domain/selection.ts'
import { CANVAS_STYLE_SCHEMA_VERSIONS, DEFAULT_CANVAS_STYLE } from '../domain/canvas-style.ts'
import { SNAPSHOT_SCHEMA_VERSION } from '../services/snapshot.service.ts'
import { QUICK_SCHEME_RESOURCE_VERSION } from '../models/quick-scheme.ts'
import { OPERATION_SCHEMA_VERSION, PROTOCOL_VERSION } from '../services/operation.service.ts'

export interface CapabilityFlags {
  realtime: boolean
}

/**
 * 全域能力宣告（不需身分）。APP 只啟用此處宣告為 true / 有版本的能力；
 * 尚未交付的能力明確為 false，舊版 APP 不可寫入不認得的內容。
 */
export class CapabilitiesController {
  constructor(private readonly flags: CapabilityFlags) {}

  get = async (): Promise<Static<typeof CapabilitiesSchema>> => ({
    canvas: {
      protocolVersion: PROTOCOL_VERSION,
      operationSchemaVersion: OPERATION_SCHEMA_VERSION,
      /** 可寫入正式畫布的最小 protocol 版本；低於此值的 APP 只能唯讀 */
      minWritableProtocolVersion: PROTOCOL_VERSION,
    },
    assetPreparation: {
      requestSchema: PREPARE_REQUEST_SCHEMA,
      responseSchema: PREPARATION_RESPONSE_SCHEMA,
      maxByteSize: MAX_ASSET_BYTES,
      mediaTypes: Object.keys(ASSET_MEDIA_TYPES),
    },
    realtime: this.flags.realtime
      ? { supported: true as const, protocolVersions: [REALTIME_PROTOCOL_VERSION], subprotocol: REALTIME_SUBPROTOCOL }
      : { supported: false as const },
    quickSchemes: {
      supported: true as const,
      resourceVersions: [QUICK_SCHEME_RESOURCE_VERSION],
      selectionSchemaVersions: [...SELECTION_SCHEMA_VERSIONS],
      materializationEnabled: false,
    },
    canvasStyle: {
      supported: true as const,
      operationKinds: ['update_canvas_style'],
      styleSchemaVersions: [...CANVAS_STYLE_SCHEMA_VERSIONS],
      snapshotSchemaVersion: SNAPSHOT_SCHEMA_VERSION,
      defaultStyle: DEFAULT_CANVAS_STYLE,
    },
    relationGeometry: { supported: false },
    vectorPath: { supported: false },
    sequenceDiagrams: { supported: false },
    mediaReferenceRestore: { supported: false },
  })
}
