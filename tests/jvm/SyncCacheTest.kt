import android.content.Context
import com.willykez.campconnect.data.repository.SyncCache
import org.json.JSONArray
import java.io.File

fun main() {
  val root = File.createTempFile("synccache", "").apply { delete(); mkdirs() }
  val ctx = Context().apply { dir = root }
  var pass = 0; var fail = 0
  fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL $name") } }

  val a = SyncCache(ctx, "user-1")
  check("missing key is null", a.get("sessions?x") == null)
  a.put("sessions?x", JSONArray("""[{"id":"1","venue":"LH1"},{"id":"2","venue":"LH2"}]"""))
  check("round trip", a.get("sessions?x")?.getJSONObject(1)?.getString("venue") == "LH2")
  a.put("sessions?x", JSONArray("""[{"id":"9"}]"""))
  check("overwrite", a.get("sessions?x")?.length() == 1 && a.get("sessions?x")?.getJSONObject(0)?.getString("id") == "9")
  check("keys with odd chars", run { a.put("rpc:x:{\"p\":\"a/b?c=d&e\"}", JSONArray("[1]")); a.get("rpc:x:{\"p\":\"a/b?c=d&e\"}")?.getInt(0) == 1 })
  check("syncedAt default 0", SyncCache(ctx, "user-2").syncedAt() == 0L)
  a.markSynced(1234567890123L)
  check("syncedAt persists across instances", SyncCache(ctx, "user-1").syncedAt() == 1234567890123L)
  check("users are isolated", SyncCache(ctx, "user-2").get("sessions?x") == null)
  check("corrupt file -> null, no crash", run { File(root, "sync/user-1").listFiles()!!.first { it.name.length == 40 }.writeText("{not json"); a.get("sessions?x") == null })
  val dirc = SyncCache(ctx, SyncCache.DIRECTORY); dirc.put("universities?q", JSONArray("""[{"code":"CUOM"}]"""))
  SyncCache.clearPersonal(ctx)
  check("sign-out clears personal cache", SyncCache(ctx, "user-1").get("rpc:x:{\"p\":\"a/b?c=d&e\"}") == null && SyncCache(ctx, "user-1").syncedAt() == 0L)
  check("sign-out keeps the public directory", SyncCache(ctx, SyncCache.DIRECTORY).get("universities?q")?.getJSONObject(0)?.getString("code") == "CUOM")
  check("clearPersonal with no folder is safe", run { root.deleteRecursively(); SyncCache.clearPersonal(ctx); true })
  println("SyncCache: $pass passed, $fail failed"); System.exit(if (fail == 0) 0 else 1)
}
