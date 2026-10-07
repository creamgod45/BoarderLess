import { describe, expect, test } from 'bun:test'
import { touchedObjectIds, validateOperationPayload } from '../../src/domain/operations.ts'
import { can } from '../../src/domain/permissions.ts'

const id = () => crypto.randomUUID()

describe('validateOperationPayload', () => {
  test('accepts a valid create_object', () => {
    const result = validateOperationPayload('create_object', { objectId: id(), objectType: 'text' })
    expect(result.ok).toBe(true)
  })

  test('rejects unknown kinds', () => {
    const result = validateOperationPayload('explode', {})
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.errors[0]!.path).toBe('/kind')
  })

  test('rejects extra properties and bad ids', () => {
    const result = validateOperationPayload('delete_objects', { objectIds: ['nope'], extra: 1 })
    expect(result.ok).toBe(false)
    if (!result.ok) {
      const paths = result.errors.map((e) => e.path)
      expect(paths).toContain('/payload/objectIds/0')
    }
  })

  test('rejects relation with unknown direction', () => {
    const result = validateOperationPayload('create_relation', {
      relationId: id(),
      sourceObjectId: id(),
      targetObjectId: id(),
      direction: 'sideways',
    })
    expect(result.ok).toBe(false)
  })
})

describe('touchedObjectIds', () => {
  test('lists ids affected by move_objects', () => {
    const [a, b] = [id(), id()]
    const result = validateOperationPayload('move_objects', {
      moves: [
        { objectId: a, transform: { x: 1 } },
        { objectId: b, transform: { x: 2 } },
      ],
    })
    if (!result.ok) throw new Error('expected valid')
    expect(touchedObjectIds(result.operation)).toEqual([a, b])
  })
})

describe('permissions', () => {
  test('viewer cannot submit operations; editor can', () => {
    expect(can('viewer', 'operations.submit')).toBe(false)
    expect(can('commenter', 'operations.submit')).toBe(false)
    expect(can('editor', 'operations.submit')).toBe(true)
  })

  test('only owner manages members and deletes workspace', () => {
    expect(can('editor', 'members.manage')).toBe(false)
    expect(can('owner', 'members.manage')).toBe(true)
    expect(can('editor', 'workspace.delete')).toBe(false)
  })
})
