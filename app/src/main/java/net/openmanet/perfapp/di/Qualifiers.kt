package net.openmanet.perfapp.di

import javax.inject.Qualifier

/** The general-internet-reachable OkHttpClient used for CSV upload, never bound to the mesh network. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CloudHttpClient
