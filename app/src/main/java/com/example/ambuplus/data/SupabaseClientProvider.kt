package com.example.ambuplus.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.gotrue.Auth

object SupabaseClientProvider {

    private const val SUPABASE_URL = "https://xvxtlvmuluwxxsydafnm.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2eHRsdm11bHV3eHhzeWRhZm5tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjE0MzU3NDksImV4cCI6MjA3NzAxMTc0OX0.UWEH2igedTL-Kndg1V_jdAYkOfWUm64Tg1HJ98U-ryc"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Auth) // ✅ Correct for your SDK version
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Functions)
        }
    }
}



// https://xvxtlvmuluwxxsydafnm.supabase.co
// eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2eHRsdm11bHV3eHhzeWRhZm5tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjE0MzU3NDksImV4cCI6MjA3NzAxMTc0OX0.UWEH2igedTL-Kndg1V_jdAYkOfWUm64Tg1HJ98U-ryc
