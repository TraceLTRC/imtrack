package xyz.tracel.imtrack.placeholder

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class PlaceholderViewModel @Inject constructor(clock: Clock) : ViewModel() {
    val text: StateFlow<String> =
        MutableStateFlow("imtrack is wired up. Today is ${LocalDate.now(clock)}.")
}
