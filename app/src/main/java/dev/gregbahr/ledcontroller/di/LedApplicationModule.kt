package dev.gregbahr.ledcontroller.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import dev.gregbahr.ledcontroller.BluetoothService
import dev.gregbahr.ledcontroller.MainActivity

@Module
abstract class LedApplicationModule {
    @ContributesAndroidInjector
    abstract fun contributeActivityInjector(): MainActivity

    @ContributesAndroidInjector
    abstract fun contributeServiceInjector(): BluetoothService
}