// Test double for @supabase/supabase-js: every call is routed to globalThis.__mock and recorded.
type Who = 'user' | 'admin' | 'probe'
export const calls: Array<{ who: Who; op: string; args: unknown }> = []
// deno-lint-ignore no-explicit-any
const M = () => (globalThis as any).__mock
export function createClient(_url: string, key: string, opts?: { global?: { headers?: Record<string, string> }; auth?: Record<string, unknown> }) {
  const who: Who = opts?.global?.headers?.Authorization ? 'user' : key === 'service-key' ? 'admin' : 'probe'
  const rec = (op: string, args: unknown) => { calls.push({ who, op, args }) }
  return {
    auth: {
      getUser: (t: string) => { rec('auth.getUser', t); return Promise.resolve(M().getUser(t)) },
      signInWithPassword: (a: unknown) => { rec('auth.signInWithPassword', a); return Promise.resolve(M().signIn(a)) },
      admin: {
        updateUserById: (id: string, a: unknown) => { rec('admin.updateUserById', { id, ...(a as object) }); return Promise.resolve(M().updateUser?.(id, a) ?? { error: null }) },
        createUser: (a: unknown) => { rec('admin.createUser', a); return Promise.resolve({ data: { user: { id: 'new' } }, error: null as { message: string; code?: string } | null }) },
        deleteUser: (id: string) => { rec('admin.deleteUser', id); return Promise.resolve(M().deleteUser?.(id) ?? { error: null }) },
      },
    },
    rpc: (name: string, args?: unknown) => { rec('rpc:' + name, args); return Promise.resolve(M().rpc(who, name, args)) },
    from: (table: string) => {
      const q = { table, filters: [] as unknown[] }
      const chain = {
        select: (_c: string) => chain, eq: (c: string, v: unknown) => { q.filters.push([c, v]); return chain },
        maybeSingle: () => { rec('from.select', q); return Promise.resolve(M().table(table, q)) },
        update: (v: unknown) => { rec('from.update', { table, v }); return { eq: (_c: string, _v: unknown) => Promise.resolve({ error: null as { message: string; code?: string } | null }) } },
      }
      return chain
    },
    storage: { from: (bucket: string) => ({ remove: (paths: string[]) => { rec('storage.remove', { bucket, paths }); return Promise.resolve(M().remove(bucket, paths)) } }) },
  }
}
