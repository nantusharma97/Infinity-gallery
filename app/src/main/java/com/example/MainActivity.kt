package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.GalleryApp
import com.example.ui.GalleryViewModel
import com.example.ui.GalleryViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize the ViewModel using the secure Factory
    val viewModelFactory = GalleryViewModelFactory(applicationContext)
    val viewModel = ViewModelProvider(this, viewModelFactory)[GalleryViewModel::class.java]

    setContent {
      MyApplicationTheme {
        GalleryApp(viewModel = viewModel)
      }
    }
  }
}
