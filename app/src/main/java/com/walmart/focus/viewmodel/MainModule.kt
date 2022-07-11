package com.walmart.focus.viewmodel

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class MainModule {
    @Provides
    fun providesContext(@ApplicationContext appContext: Context) : Context{
        return appContext
    }
}
