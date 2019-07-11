package dev.gregbahr.ledcontroller

import androidx.lifecycle.MutableLiveData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedControllerRepository @Inject constructor() {
    val brightness: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }
    val animationType: MutableLiveData<AnimationType> by lazy {
        MutableLiveData<AnimationType>()
    }
    val delayTime: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }
    val color: MutableLiveData<ByteArray> by lazy {
        MutableLiveData<ByteArray>()
    }
}