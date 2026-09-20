package vn.lobie.mytube

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.lobie.mytube.data.repository.FakeYouTubeRepository
import vn.lobie.mytube.ui.home.HomeScreen
import vn.lobie.mytube.ui.home.HomeViewModel
import vn.lobie.mytube.ui.theme.MyTubeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyTubeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val repository = remember { FakeYouTubeRepository() }
                    val viewModel: HomeViewModel = viewModel {
                        HomeViewModel(repository)
                    }

                    HomeScreen(
                        viewModel = viewModel,
                        onVideoClick = { video ->
                            Toast.makeText(
                                this,
                                "Selected: ${video.title}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }
}
