package xyz.five82.takeup.api

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * The one Gson for Loom's API: snake_case JSON onto camelCase DTOs. Shared
 * with tests so a drift between policy and DTOs cannot hide.
 */
val loomGson: Gson = GsonBuilder()
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .create()
