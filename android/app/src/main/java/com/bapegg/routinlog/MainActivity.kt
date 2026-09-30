package com.bapegg.routinlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bapegg.routinlog.data.SystemStatusRepository
import com.bapegg.routinlog.ui.RoutineLogApp
import com.bapegg.routinlog.ui.RoutineLogViewModel
import com.bapegg.routinlog.ui.theme.RoutineLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = viewModelFactory {
            initializer {
                RoutineLogViewModel(SystemStatusRepository.create(BuildConfig.API_BASE_URL, BuildConfig.DEBUG))
            }
        }
        setContent {
            RoutineLogTheme {
                val model: RoutineLogViewModel = viewModel(factory = factory)
                RoutineLogApp(model)
            }
        }
    }
}
