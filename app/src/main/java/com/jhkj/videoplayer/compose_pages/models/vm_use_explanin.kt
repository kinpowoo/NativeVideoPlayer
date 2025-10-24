import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController


//核心概念
//1. ViewModelStoreOwner：每个 Activity、Fragment 或 Navigation 目的地(destination)都有自己的 ViewModelStore（存储 ViewModel 的容器），
//   而 ViewModelStoreOwner就是提供这个容器的接口。
//2. 默认行为：在 Composable 中使用 viewModel()时，它会自动查找最近的 ViewModelStoreOwner（通常是当前的 Activity 或 NavGraph 目的地）。
//3. 共享 ViewModel：如果你希望多个屏幕（Composable）共享同一个 ViewModel，你需要确保它们使用同一个 ViewModelStoreOwner。


// 3. 跨屏幕共享 ViewModel
//如果你想在多个屏幕间共享同一个 ViewModel 实例：
@Composable
fun SharedViewModelSample() {
    val sharedViewModel: ViewModel = viewModel(
        viewModelStoreOwner = LocalViewModelStoreOwner.current!!
    )

    // 或者在导航图中共享
    val navController = rememberNavController()
    val sharedViewModel2: ViewModel = viewModel(
        viewModelStoreOwner = navController.getViewModelStoreOwner(navController.graph.id)
    )
}


