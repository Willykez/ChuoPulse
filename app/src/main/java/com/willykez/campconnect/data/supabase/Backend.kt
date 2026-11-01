package com.willykez.campconnect.data.supabase

import android.content.Context

/** The one shared connection to Supabase, and the one answer to "is this build configured?". */
object Backend {
  val client: SupabaseClient by lazy { SupabaseClient() }
  val live: Boolean by lazy { client.isConfigured }

  /** Application context, set once at start-up. Needed to read picked files and keep downloaded copies. */
  @Volatile var appContext: Context? = null
    private set

  /** The signed-in person's token, for features that live outside the repository (the AI analyser). */
  @Volatile var tokenSource: (() -> String?)? = null
  @Volatile var tokenRefresh: (suspend () -> String?)? = null

  fun init(context: Context) {
    appContext = context.applicationContext
  }
}
