package dev.gregbahr.ledcontroller.di

import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import dev.gregbahr.ledcontroller.LedApplication
import javax.inject.Singleton

@Singleton
@Component(modules = [AndroidInjectionModule::class, LedApplicationModule::class])
interface LedApplicationComponent : AndroidInjector<LedApplication>