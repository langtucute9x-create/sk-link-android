package vn.sklight.auto

import android.content.Intent
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Diem vao cua Android Auto. App khai bao loai "dan duong" de duoc cap mot be mat tu ve
 * (giong ban do), roi ve trinh duyet / video len be mat do.
 */
class AutoService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(sessionInfo: SessionInfo): Session = AutoSession()
}

class AutoSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val r = Renderer(carContext)
        Hub.renderer = r
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (Hub.renderer === r) Hub.renderer = null
                r.release()
            }
        })
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(r)
        return MainScreen(carContext, r)
    }
}

/** Man hinh chinh: toan bo la be mat tu ve, kem hai dai nut. */
class MainScreen(carContext: CarContext, private val r: Renderer) : Screen(carContext) {

    private fun act(icon: Int, f: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, icon)).build())
            .setOnClickListener { f() }
            .build()

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
            .addAction(act(R.drawable.ic_home) { r.goHome() })
            .addAction(act(R.drawable.ic_search) { screenManager.push(SearchScreen(carContext, r)) })
            .addAction(act(R.drawable.ic_list) { screenManager.push(SitesScreen(carContext, r)) })
            .addAction(act(R.drawable.ic_playpause) { r.togglePlay() })
            .build()
        // Phai co Action.PAN thi Android Auto moi gui cham / vuot / thu phong ve cho app.
        val map = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(act(R.drawable.ic_back) { r.goBack() })
            .addAction(act(R.drawable.ic_up) { r.step(-1) })
            .addAction(act(R.drawable.ic_down) { r.step(1) })
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(strip)
            .setMapActionStrip(map)
            .build()
    }
}

/** Tim tren YouTube bang ban phim / giong noi cua Android Auto. */
class SearchScreen(carContext: CarContext, private val r: Renderer) : Screen(carContext) {
    override fun onGetTemplate(): Template =
        SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchSubmitted(searchText: String) {
                if (searchText.isNotBlank()) r.load(Hub.toUrl(searchText))
                screenManager.pop()
            }
        })
            .setHeaderAction(Action.BACK)
            .setShowKeyboardByDefault(true)
            .setSearchHint("Tìm trên YouTube hoặc nhập địa chỉ web")
            .build()
}

/** Danh sach trang da luu (du phong khi dau xe khong gui duoc cham len be mat). */
class SitesScreen(carContext: CarContext, private val r: Renderer) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        val sites = Store.sites(carContext).filter { it.name.isNotBlank() }.take(5)
        for (s in sites) {
            list.addItem(
                Row.Builder()
                    .setTitle(s.name)
                    .setOnClickListener {
                        r.load(s.url)
                        screenManager.pop()
                    }
                    .build()
            )
        }
        if (Store.videos(carContext).isNotEmpty()) {
            list.addItem(
                Row.Builder()
                    .setTitle("Video trong máy")
                    .setOnClickListener {
                        r.openVideo(0)
                        screenManager.pop()
                    }
                    .build()
            )
        }
        list.setNoItemsMessage("Chưa có trang nào")
        return ListTemplate.Builder()
            .setTitle("SK Auto")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}
