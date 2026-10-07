import { Type } from 'typebox'
import { DateTime, errorResponses } from './common.schema.ts'

export const UserSchema = Type.Object({
  id: Type.String(),
  displayName: Type.String(),
  createdAt: DateTime,
})

export const CreateUserBody = Type.Object(
  { displayName: Type.String({ minLength: 1, maxLength: 100 }) },
  { additionalProperties: false },
)

export const createUserSchema = {
  tags: ['users'],
  summary: '建立使用者（dev auth 用；正式登入流程尚未實作）',
  body: CreateUserBody,
  response: { 201: UserSchema, ...errorResponses },
}

export const getMeSchema = {
  tags: ['users'],
  summary: '目前使用者',
  response: { 200: UserSchema, ...errorResponses },
}
