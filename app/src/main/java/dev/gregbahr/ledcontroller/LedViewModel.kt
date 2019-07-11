package dev.gregbahr.ledcontroller

import androidx.lifecycle.ViewModel
import javax.inject.Inject

class LedViewModel @Inject constructor(val ledControllerRepository: LedControllerRepository) : ViewModel()