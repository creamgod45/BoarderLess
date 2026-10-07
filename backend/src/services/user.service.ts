import type { User } from '../models/user.ts'
import type { Database } from '../repositories/index.ts'
import { notFound } from '../utils/errors.ts'

export class UserService {
  constructor(private readonly db: Database) {}

  create(displayName: string): Promise<User> {
    return this.db.repos.users.create(displayName.trim())
  }

  async get(id: string): Promise<User> {
    const user = await this.db.repos.users.findActiveById(id)
    if (!user) throw notFound('User')
    return user
  }

  findActive(id: string): Promise<User | null> {
    return this.db.repos.users.findActiveById(id)
  }
}
