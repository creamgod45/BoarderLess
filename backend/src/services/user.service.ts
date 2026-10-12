import type { User } from '../models/user.ts'
import type { Database } from '../repositories/index.ts'

export class UserService {
  constructor(private readonly db: Database) {}

  create(displayName: string): Promise<User> {
    return this.db.repos.users.create(displayName.trim())
  }

  findActive(id: string): Promise<User | null> {
    return this.db.repos.users.findActiveById(id)
  }
}
