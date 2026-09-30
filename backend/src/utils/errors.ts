/** 應用層錯誤；由 error handler 轉成統一 error envelope。 */
export class AppError extends Error {
  constructor(
    readonly statusCode: number,
    readonly code: string,
    message: string,
    readonly details?: unknown,
  ) {
    super(message)
    this.name = 'AppError'
  }
}

export const badRequest = (code: string, message: string, details?: unknown) => new AppError(400, code, message, details)
export const unauthorized = (message = 'Authentication required') => new AppError(401, 'unauthorized', message)
export const forbidden = (message = 'Permission denied') => new AppError(403, 'forbidden', message)
export const notFound = (resource: string) => new AppError(404, 'not_found', `${resource} not found`)
export const conflict = (code: string, message: string, details?: unknown) => new AppError(409, code, message, details)
export const unprocessable = (code: string, message: string, details?: unknown) =>
  new AppError(422, code, message, details)
