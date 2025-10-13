package com.robining.games.frame.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.robining.games.frame.R

object GameCenter {
    const val URL_PRIVACY_POLICY = "https://miaozhi-ai.github.io/aboutus/privacy.html"
    const val URL_TERMS_SERVICE = "https://miaozhi-ai.github.io/aboutus/terms.html"
    const val URL_CONTACT_US = "https://miaozhi-ai.github.io"
    const val URL_APP_STORE_MIAOZHI = "https://play.google.com/store/apps/dev?id=5159771901379904318"
}

enum class Game(
    @StringRes val nameResId: Int,
    @StringRes val introduceResId: Int,
    @DrawableRes val iconResId: Int,
    val packageName: String
) {
    Klotski(
        R.string.game_klotski,
        R.string.game_klotski_desc,
        R.mipmap.logo_klotski,
        GameContext.PACKAGE_NAME_KLOTSKI
    ),
    YUKON(
        R.string.game_yukon,
        R.string.game_yukon_desc,
        R.mipmap.logo_yukon,
        GameContext.PACKAGE_NAME_YUKON
    ),
    NumberKlotski(
        R.string.game_number_klotski,
        R.string.game_number_klotski_desc,
        R.mipmap.logo_number_klotski,
        GameContext.PACKAGE_NAME_NUMBER_KLOTSKI
    ),
    PuzzleLover(
        R.string.game_puzzle,
        R.string.game_puzzle_desc,
        R.mipmap.logo_puzzle_lover,
        GameContext.PACKAGE_NAME_PUZZLE_LOVER
    ),
    OATB(
        R.string.game_1a2b,
        R.string.game_1a2b_desc,
        R.mipmap.logo_1a2b,
        GameContext.PACKAGE_NAME_1A2B
    ),
    MATH24(
        R.string.game_number24,
        R.string.game_number24_desc,
        R.mipmap.logo_24_math,
        GameContext.PACKAGE_NAME_MATH_24
    ),
    MINESWEEPER(
        R.string.game_mine_sweeper,
        R.string.game_mine_sweeper_desc,
        R.mipmap.logo_minesweeper,
        GameContext.PACKAGE_NAME_MINESWEEPER
    ),
    MONTANA(
        R.string.game_montana,
        R.string.game_montana_desc,
        R.mipmap.logo_montana,
        GameContext.PACKAGE_NAME_MONTANA
    );

    companion object {
        fun findByPackageName(packageName: String): Game? {
            values().forEach {
                if (it.packageName == packageName) {
                    return it
                }
            }
            return null
        }
    }
}