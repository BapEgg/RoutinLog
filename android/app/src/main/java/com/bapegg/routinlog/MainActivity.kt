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
import com.bapegg.routinlog.ui.MealViewModel
import com.bapegg.routinlog.ui.WorkoutReviewViewModel
import com.bapegg.routinlog.ui.MealReviewViewModel
import com.bapegg.routinlog.ui.ReportViewModel
import com.bapegg.routinlog.ui.StepsViewModel
import com.bapegg.routinlog.steps.RoutineLogServices
import com.bapegg.routinlog.ui.ConditionViewModel
import com.bapegg.routinlog.ui.WorkoutViewModel
import com.bapegg.routinlog.ui.AccountViewModel
import com.bapegg.routinlog.data.AccountRepository
import com.bapegg.routinlog.ui.theme.RoutineLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val services = RoutineLogServices.get(applicationContext)
        val repository = services.accounts
        val factory = viewModelFactory {
            initializer {
                RoutineLogViewModel(SystemStatusRepository.create(BuildConfig.API_BASE_URL, BuildConfig.DEBUG))
            }
            initializer {
                AccountViewModel(repository)
            }
        }
        val reviewFactory = viewModelFactory { initializer { WorkoutReviewViewModel(repository) } }
        val mealReviewFactory = viewModelFactory { initializer { MealReviewViewModel(repository) } }
        val reportFactory = viewModelFactory { initializer { ReportViewModel(repository) } }
        val stepsFactory = viewModelFactory { initializer { StepsViewModel(repository,services.steps) } }
        val mealFactory = viewModelFactory { initializer { MealViewModel(repository) } }
        val conditionFactory = viewModelFactory { initializer { ConditionViewModel(repository) } }
        val workoutFactory = viewModelFactory { initializer { WorkoutViewModel(repository) } }
        setContent {
            RoutineLogTheme {
                val model: RoutineLogViewModel = viewModel(factory = factory)
                val account: AccountViewModel = viewModel(factory = factory)
                val meals: MealViewModel = viewModel(factory = mealFactory)
                val workouts: WorkoutViewModel = viewModel(factory = workoutFactory)
                val conditions: ConditionViewModel = viewModel(factory = conditionFactory)
                val reviews: WorkoutReviewViewModel = viewModel(factory = reviewFactory)
                val mealReviews: MealReviewViewModel = viewModel(factory = mealReviewFactory)
                val reports: ReportViewModel = viewModel(factory = reportFactory)
                val steps: StepsViewModel = viewModel(factory = stepsFactory)
                RoutineLogApp(model, if (BuildConfig.DEBUG) intent.getStringExtra("preview_route") else null, account, meals, workouts, conditions, steps, reports, reviews,mealReviews)
            }
        }
    }
}
