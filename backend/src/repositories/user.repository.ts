import type { Db } from '../db/client.ts'
import type { User } from '../models/user.ts'

export class UserRepository {
  constructor(private readonly db: Db) {}

  async create(displayName: string): Promise<User> {
    const [row] = await this.db<User[]>`
      INSERT INTO users (display_name) VALUES (${displayName}) RETURNING *
    `
    return row!
  }

  async findActiveById(id: string): Promise<User | null> {
    const [row] = await this.db<User[]>`SELECT * FROM users WHERE id = ${id} AND disabled_at IS NULL`
    return row ?? null
  }
}
