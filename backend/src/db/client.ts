import postgres from 'postgres'

/** 一般連線或 transaction 內的連線；repository 只依賴這個型別。 */
export type Db = postgres.Sql | postgres.TransactionSql

export function createSql(url: string, max = 10): postgres.Sql {
  return postgres(url, {
    max,
    // snake_case column <-> camelCase property
    transform: postgres.camel,
    // BIGINT 以 number 回傳（serverSeq / version 在 2^53 內）
    types: {
      bigint: {
        to: 20,
        from: [20],
        serialize: (value: number) => value.toString(),
        parse: (value: string) => Number(value),
      },
    },
    onnotice: () => {},
  })
}

/** 將任意值包成 JSONB 參數。 */
export function json(db: Db, value: unknown) {
  return db.json(value as postgres.JSONValue)
}
