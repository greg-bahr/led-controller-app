package dev.gregbahr.ledcontroller.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import dev.gregbahr.ledcontroller.BluetoothService
import dev.gregbahr.ledcontroller.LedControlActivity
import dev.gregbahr.ledcontroller.MainActivity

@Module
abstract class LedApplicationModule {
    @ContributesAndroidInjector
    abstract fun contributeActivityInjector(): LedControlActivity

    @ContributesAndroidInjector
    abstract fun contributeServiceInjector(): BluetoothService
}