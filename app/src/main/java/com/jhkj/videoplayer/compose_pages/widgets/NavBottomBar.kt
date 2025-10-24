package com.jhkj.videoplayer.compose_pages.widgets

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jhkj.videoplayer.compose_pages.models.ConnConfig
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.compose_pages.pages.AddConnectionScreen
import com.jhkj.videoplayer.compose_pages.pages.ConnType
import com.jhkj.videoplayer.compose_pages.pages.ConnectionEditScreen
import com.jhkj.videoplayer.compose_pages.pages.HomeScreen
import com.jhkj.videoplayer.compose_pages.router.ParamsConfig
import com.jhkj.videoplayer.compose_pages.router.RouteConfig

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen(RouterName.Home.name, "Home", Icons.Outlined.Home)
    data object Connection : Screen(RouterName.Connection.name, "Connection", Icons.Outlined.SyncAlt)
    data object Profile : Screen(RouterName.Profile.name, "Profile", Icons.Outlined.Person)
}

val items = listOf(
    Screen.Home,
    Screen.Connection,
    Screen.Profile,
)


sealed class RouterName(val name:String){
    data object Home : RouterName("home")
    data object Connection : RouterName("connection")
    data object Profile : RouterName("profile")
    data object AddConnection : RouterName("add_connection")
    data object EditConnection : RouterName("edit_connection")
}

class AppState(navController: NavHostController) {
    private val _showBottomBar = mutableStateOf(true)
    val showBottomBar: State<Boolean> = _showBottomBar

    init {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            _showBottomBar.value = when(destination.route) {
                Screen.Home.route, Screen.Connection.route, Screen.Profile.route -> true
                else -> false
            }
        }
    }
}

@Composable
fun rememberAppState(navController: NavHostController = rememberNavController()) =
    remember(navController) { AppState(navController) }

@Composable
fun NavBottomBar() {
    val navController = rememberNavController()
    val appState = rememberAppState(navController)

    Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            composable(Screen.Home.route) { HomeScreen(navController) }
            composable(Screen.Connection.route) { FavoriteScreen(navController) }
            composable(Screen.Profile.route) { ProfileScreen(navController) }
            composable(RouterName.AddConnection.name){ AddConnectionScreen(navController) }
            //必传参数，使用"/"拼写在路由地址后面添加占位符
            composable("${RouterName.EditConnection.name}/{${ConnConfig.PARAMS_NAME}}/{${ConnConfig.PARAMS_FLAG}}/{connType}",
                arguments = listOf(
                    navArgument(ParamsConfig.PARAMS_NAME) {
                        type = NavType.ParcelableType(ConnInfo::class.java)
                        nullable = true
                    },//参数是String类型可以不用额外指定，这句不写也是可以的
                    navArgument(ConnConfig.PARAMS_FLAG) {
                        type = NavType.BoolType //指定具体类型
                        defaultValue = false //默认值（选配）
                        nullable = false  //可否为null（选配）
                    },
                    navArgument("connType") {
                        type = NavType.IntType //指定具体类型
                        defaultValue = ConnType.WEBDAV.ordinal//默认值（选配）
                    }
                )
            ) { backStackEntry ->
                val user:ConnInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    backStackEntry.arguments?.getParcelable(ConnConfig.PARAMS_NAME,ConnInfo::class.java)
                } else {
                    backStackEntry.arguments?.getParcelable(ConnConfig.PARAMS_NAME)
                }
                val isEdit = backStackEntry.arguments?.getBoolean(ConnConfig.PARAMS_FLAG) ?: false
                //通过composable函数中提供的NavBackStackEntry提取参数
                ConnectionEditScreen(user,isEdit,navController)
            }
        }
        AnimatedVisibility(
            visible = appState.showBottomBar.value,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            BottomBar(navController, items, modifier = Modifier.fillMaxWidth())
        }
//        if (appState.showBottomBar.value) {
//            BottomBar(navController, items, modifier = Modifier.fillMaxWidth())
//        }
    }
}

@Composable
fun BottomBar(
    navController: NavHostController,
    items: List<Screen>,       //导航路线
    modifier: Modifier = Modifier
) {
    //获取当前的 NavBackStackEntry 来访问当前的 NavDestination
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    Row(
        modifier = modifier.background(color = Color.White),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, screen ->
            BottomBarItem(
                item = screen,
                //与层次结构进行比较来确定是否被选中
                isSelected = currentDestination?.hierarchy?.any { it.route == screen.route },
                onItemClicked = {
                    //加这个可解决问题：按back键会返回2次，第一次先返回home, 第二次才会退出
                    navController.popBackStack()
                    //点击item时，清空栈内 popUpTo ID到栈顶之间的所有节点，避免站内节点持续增加
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            //跳转时保存页面状态
                            saveState = true
                        }
                        //栈顶复用，避免重复点击同一个导航按钮，回退栈中多次创建实例
                        launchSingleTop = true
                        //回退时恢复页面状态
                        restoreState = true
                        //通过使用 saveState 和 restoreState 标志，当在底部导航项之间切换时，
                        //系统会正确保存并恢复该项的状态和返回堆栈。
                    }
                }
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    item: Screen,
    isSelected: Boolean?,   //是否选中
    onItemClicked: () -> Unit,  //按钮点击监听
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickableWithoutInteraction { onItemClicked.invoke() }
            .padding(top = 5.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            modifier = Modifier.size(30.dp),
            imageVector = item.icon,
            contentDescription = item.title,
            tint = if (isSelected == true) Color.Blue else Color.Gray,
        )
        Text(
            text = item.title,
            color = if (isSelected == true) Color.Blue else Color.Gray,
            fontSize = 10.sp,
        )
    }
}


@Composable
fun FavoriteScreen(navController: NavController) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "FavoriteScreen"
        )
    }
}

@Composable
fun ProfileScreen(navController: NavController) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "ProfileScreen"
        )
    }
}

/**
 * clickable禁用点击涟漪效应
 */
inline fun Modifier.clickableWithoutInteraction(crossinline onClick: () -> Unit): Modifier =
    this.composed {
        clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            onClick()
        }
    }