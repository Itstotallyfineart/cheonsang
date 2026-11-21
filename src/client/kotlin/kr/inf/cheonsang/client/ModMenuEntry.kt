package kr.inf.cheonsang.client

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

/** 1.2.1: ModMenu 가 깔려 있으면 모드 목록에서 천상 설정 화면 열기 (없으면 이 클래스는 안 불림) */
class ModMenuEntry : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> = ConfigScreenFactory { parent -> CheonsangMenu.create(parent) }
}
